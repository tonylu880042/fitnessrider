import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { comparePassword, signJwt } from '@/lib/auth';
import { verifyVipSerial } from '@/lib/vipSerial';

const TRANSFER_COOLDOWN_DAYS = 30;

export async function GET(req: NextRequest) {
  try {
    const { searchParams } = new URL(req.url);
    const email = searchParams.get('email');
    const license_code = searchParams.get('license_code') || searchParams.get('code');

    let userId: string | null = null;
    if (email) {
      const user = await db.getUserByEmail(email);
      if (user) userId = user.id;
    } else if (license_code) {
      const license = await db.getLicenseByCode(license_code);
      if (license) userId = license.user_id;
    }

    if (!userId) {
      return NextResponse.json({
        success: true,
        can_transfer: true,
        remaining_cooldown_days: 0,
        message: '無轉移記錄，可直接進行首次綁定或轉移。',
      });
    }

    const lastTransfer = await db.getLastTransfer(userId);
    if (lastTransfer) {
      const lastTransferTime = new Date(lastTransfer.transferred_at).getTime();
      const elapsedDays = (Date.now() - lastTransferTime) / (1000 * 60 * 60 * 24);
      if (elapsedDays < TRANSFER_COOLDOWN_DAYS) {
        const remainingDays = Math.ceil(TRANSFER_COOLDOWN_DAYS - elapsedDays);
        return NextResponse.json({
          success: true,
          can_transfer: false,
          remaining_cooldown_days: remainingDays,
          last_transferred_at: lastTransfer.transferred_at,
          message: `距離下次可更換設備尚有 ${remainingDays} 天。`,
        });
      }
    }

    return NextResponse.json({
      success: true,
      can_transfer: true,
      remaining_cooldown_days: 0,
      message: '冷卻期已過，可正常轉移設備。',
    });
  } catch (error) {
    console.error('Device Transfer GET Error:', error);
    return NextResponse.json({ success: false, error: '查詢換機狀態失敗' }, { status: 500 });
  }
}

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { email, password, platform } = body;
    const license_code = (body.license_code || body.licenseCode || '').trim();
    const new_device_fingerprint = body.new_device_fingerprint || body.newDeviceFingerprint;
    const device_model = body.device_model || body.deviceModel;

    if (!new_device_fingerprint) {
      return NextResponse.json(
        { success: false, error: '缺少新設備識別碼' },
        { status: 400 }
      );
    }

    // 目標設備若已經開通過（資料庫已有 device_secret），呼叫端必須用「該設備的密鑰」簽章，
    // 證明自己真的坐在那台設備前。否則任何人都能拿受害者的 ANDROID_ID 當「新設備」：
    //   a. 用自己的帳號把綁定搬過去 —— devices 只在 user_id 上有唯一性，bindDevice 會直接改寫，
    //      之後 /api/license/activate 的「JWT 帳號綁定設備 === 目標設備」檢查就會通過，
    //      可竄改受害者的授權與試用錨點（與 /api/auth/register 的 DEVICE_ALREADY_BOUND 同一條規則）；
    //   b. 用一組全新序號走下面的「模式 2」分支，activateLicenseWithCode 對非推廣碼會回傳
    //      該設備既有的 device_secret，等於把受害者的密鑰原封不動送給攻擊者。
    // 全新、從未開通過的設備沒有密鑰可簽，也沒有東西可被竊，照常放行。
    const targetAnchor = await db.getDeviceTrialAnchor(new_device_fingerprint);
    if (targetAnchor?.device_secret) {
      const timestamp = Number(body.timestamp);
      const signature = typeof body.signature === 'string' ? body.signature : '';
      const ownsTargetDevice =
        timestamp && signature
          ? await db.verifyDeviceSignature(new_device_fingerprint, timestamp, signature)
          : false;
      if (!ownsTargetDevice) {
        return NextResponse.json(
          {
            success: false,
            error: '此設備已完成開通綁定，請在該設備上操作轉移（需原設備密鑰簽章驗證）',
            error_code: 'DEVICE_SECRET_REQUIRED',
          },
          { status: 403 }
        );
      }
    }

    let targetUserId: string | null = null;
    let targetEmail: string = '';

    // 模式 1：以會員帳號密碼轉移
    if (email && password) {
      const user = await db.getUserByEmail(email);
      if (!user) {
        return NextResponse.json(
          { success: false, error: '帳號或密碼錯誤' },
          { status: 401 }
        );
      }

      const isValidPassword = await comparePassword(password, user.password_hash);
      if (!isValidPassword) {
        return NextResponse.json(
          { success: false, error: '帳號或密碼錯誤' },
          { status: 401 }
        );
      }

      targetUserId = user.id;
      targetEmail = user.email;
    }
    // 模式 2：以 VIP 授權序號轉移
    else if (license_code) {
      const vipSerialInfo = verifyVipSerial(license_code);
      if (!vipSerialInfo) {
        return NextResponse.json(
          { success: false, error: '無效的 VIP 授權序號' },
          { status: 400 }
        );
      }

      const existingLicense = await db.getLicenseByCode(license_code);
      if (existingLicense) {
        targetUserId = existingLicense.user_id;
        const user = await db.getUserById(targetUserId);
        targetEmail = user?.email || `vip_${license_code.slice(-6)}@fitnessrider.local`;
      } else {
        // 全新合法序號轉移（首次直接在該機開通並綁定）
        const actRes = await db.activateLicenseWithCode(new_device_fingerprint, license_code, platform === 'android' ? 'android' : 'ios', device_model || 'New Device');
        if (actRes.success && actRes.license) {
          return NextResponse.json({
            success: true,
            message: `VIP 授權序號已成功開通並綁定至新設備 [${device_model || 'New Device'}]！`,
            license: {
              plan_type: actRes.license.plan_type,
              expires_at: actRes.license.expires_at,
              days_remaining: actRes.trial_days || 365,
              is_valid: true,
            },
            device: {
              device_fingerprint: new_device_fingerprint,
              device_model: device_model || 'New Device',
            },
            device_secret: actRes.device_secret,
          });
        } else {
          return NextResponse.json(
            { success: false, error: actRes.error || '開通授權失敗' },
            { status: 400 }
          );
        }
      }
    } else {
      return NextResponse.json(
        { success: false, error: '請提供會員帳密或 VIP 授權序號' },
        { status: 400 }
      );
    }

    // 檢查上次換機時間（30 天防共用限制）
    const lastTransfer = await db.getLastTransfer(targetUserId);
    if (lastTransfer) {
      const lastTransferTime = new Date(lastTransfer.transferred_at).getTime();
      const elapsedDays = (Date.now() - lastTransferTime) / (1000 * 60 * 60 * 24);
      if (elapsedDays < TRANSFER_COOLDOWN_DAYS) {
        const remainingDays = Math.ceil(TRANSFER_COOLDOWN_DAYS - elapsedDays);
        return NextResponse.json(
          {
            success: false,
            error: `換機次數受限。為保障單機授權政策，每 ${TRANSFER_COOLDOWN_DAYS} 天僅允許轉移一次設備。距離下次可更換設備尚有 ${remainingDays} 天。`,
            error_code: 'TRANSFER_COOLDOWN',
            remaining_cooldown_days: remainingDays,
          },
          { status: 403 }
        );
      }
    }

    const currentDevice = await db.getDeviceByUserId(targetUserId);
    const oldFingerprint = currentDevice ? currentDevice.device_fingerprint : 'none';

    // 1. 記錄換機日誌
    await db.recordDeviceTransfer({
      id: crypto.randomUUID(),
      user_id: targetUserId,
      old_device_fingerprint: oldFingerprint,
      new_device_fingerprint,
    });

    // 2. 更新設備綁定為新設備
    const newDevice = await db.bindDevice({
      id: crypto.randomUUID(),
      user_id: targetUserId,
      device_fingerprint: new_device_fingerprint,
      platform: platform === 'android' ? 'android' : 'ios',
      device_model: device_model || 'New Device',
    });

    // 3. 簽發新 Token
    const token = await signJwt({
      userId: targetUserId,
      email: targetEmail,
      deviceFingerprint: new_device_fingerprint,
    });

    const license = await db.getLicenseByUserId(targetUserId);
    const now = Date.now();
    const expiresAtMs = license ? new Date(license.expires_at).getTime() : 0;
    const isValidLicense = expiresAtMs > now && license?.status === 'active';
    const daysRemaining = Math.max(0, Math.ceil((expiresAtMs - now) / (1000 * 60 * 60 * 24)));

    // 新設備也要有自己的裝置密鑰，之後才能用簽章呼叫 /api/license/verify（spec 項目 E）。
    // 若該新設備為初次加入，setDeviceSecretIfAbsent 會產生並回傳新密鑰。
    // 若該設備先前早已存在密鑰，setDeviceSecretIfAbsent 會回傳 null；此處絕不可對外 echo 既有密鑰，
    // 避免攻擊者持有一組付費序號 + 受害者指紋即藉由轉移端點窺探他人密鑰（spec 項目 1）。
    const deviceSecret = await db.setDeviceSecretIfAbsent(new_device_fingerprint, crypto.randomBytes(32).toString('hex'));

    return NextResponse.json({
      success: true,
      message: `設備轉移成功！授權已成功遷入新設備 [${newDevice.device_model}]。`,
      token,
      device: {
        device_fingerprint: newDevice.device_fingerprint,
        device_model: newDevice.device_model,
        bound_at: newDevice.bound_at,
      },
      license: {
        plan_type: license?.plan_type || 'yearly',
        expires_at: license?.expires_at || null,
        days_remaining: daysRemaining,
        is_valid: isValidLicense,
      },
      ...(deviceSecret ? { device_secret: deviceSecret } : {}),
    });
  } catch (error) {
    console.error('Device Transfer Error:', error);
    return NextResponse.json(
      { success: false, error: '轉移設備失敗，請稍後再試' },
      { status: 500 }
    );
  }
}
