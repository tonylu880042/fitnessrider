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

    await db.recordDeviceTransfer({
      id: crypto.randomUUID(),
      user_id: targetUserId,
      old_device_fingerprint: oldFingerprint,
      new_device_fingerprint,
    });

    const newDevice = await db.bindDevice({
      id: crypto.randomUUID(),
      user_id: targetUserId,
      device_fingerprint: new_device_fingerprint,
      platform: platform === 'android' ? 'android' : 'ios',
      device_model: device_model || 'New Device',
    });

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
