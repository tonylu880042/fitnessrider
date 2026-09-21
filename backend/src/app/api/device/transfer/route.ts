import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { comparePassword, signJwt } from '@/lib/auth';

const TRANSFER_COOLDOWN_DAYS = 30;

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { email, password, platform } = body;
    const new_device_fingerprint = body.new_device_fingerprint || body.newDeviceFingerprint;
    const device_model = body.device_model || body.deviceModel;

    if (!email || !password || !new_device_fingerprint) {
      return NextResponse.json(
        { success: false, error: '請輸入帳號、密碼與新設備識別碼' },
        { status: 400 }
      );
    }

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

    // 檢查上次換機時間（30 天防作弊限制）
    const lastTransfer = await db.getLastTransfer(user.id);
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

    const currentDevice = await db.getDeviceByUserId(user.id);
    const oldFingerprint = currentDevice ? currentDevice.device_fingerprint : 'none';

    // 1. 記錄換機日誌
    await db.recordDeviceTransfer({
      id: crypto.randomUUID(),
      user_id: user.id,
      old_device_fingerprint: oldFingerprint,
      new_device_fingerprint,
    });

    // 2. 更新設備綁定為新設備
    const newDevice = await db.bindDevice({
      id: crypto.randomUUID(),
      user_id: user.id,
      device_fingerprint: new_device_fingerprint,
      platform: platform === 'android' ? 'android' : 'ios',
      device_model: device_model || 'New Device',
    });

    // 3. 簽發新 Token
    const token = await signJwt({
      userId: user.id,
      email: user.email,
      deviceFingerprint: new_device_fingerprint,
    });

    const license = await db.getLicenseByUserId(user.id);
    const now = Date.now();
    const expiresAtMs = license ? new Date(license.expires_at).getTime() : 0;
    const isValidLicense = expiresAtMs > now && license?.status === 'active';
    const daysRemaining = Math.max(0, Math.ceil((expiresAtMs - now) / (1000 * 60 * 60 * 24)));

    return NextResponse.json({
      success: true,
      message: `設備轉移成功！此帳號已成功綁定至新設備 [${newDevice.device_model}]。`,
      token,
      device: {
        device_fingerprint: newDevice.device_fingerprint,
        device_model: newDevice.device_model,
        bound_at: newDevice.bound_at,
      },
      license: {
        plan_type: license?.plan_type || 'none',
        expires_at: license?.expires_at || null,
        days_remaining: daysRemaining,
        is_valid: isValidLicense,
      },
    });
  } catch (error) {
    console.error('Device Transfer Error:', error);
    return NextResponse.json(
      { success: false, error: '轉移設備失敗，請稍後再試' },
      { status: 500 }
    );
  }
}
