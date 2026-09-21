import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { hashPassword, signJwt } from '@/lib/auth';
import { AuthResponse } from '@/lib/types';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { email, password, name, platform } = body;
    const device_fingerprint = body.device_fingerprint || body.deviceFingerprint;
    const device_model = body.device_model || body.deviceModel;

    if (!email || !password || !device_fingerprint) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '請填寫完整的註冊資訊與設備碼', error_code: 'INVALID_CREDENTIALS' },
        { status: 400 }
      );
    }

    if (password.length < 6) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '密碼長度至少需 6 個字元', error_code: 'INVALID_CREDENTIALS' },
        { status: 400 }
      );
    }

    const existingUser = await db.getUserByEmail(email);
    if (existingUser) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '此電子郵件已被註冊', error_code: 'EMAIL_EXISTS' },
        { status: 409 }
      );
    }

    // 1. 建立使用者
    const userId = crypto.randomUUID();
    const password_hash = await hashPassword(password);
    const user = await db.createUser({
      id: userId,
      email,
      password_hash,
      name: name || '飛輪教練',
    });

    // 2. 綁定首次註冊的設備
    const device = await db.bindDevice({
      id: crypto.randomUUID(),
      user_id: userId,
      device_fingerprint,
      platform: platform === 'android' ? 'android' : 'ios',
      device_model: device_model || 'Unknown Device',
    });

    // 3. 發放 7 天免費 VIP 試用授權
    const trialDays = 7;
    const expiresAt = new Date(Date.now() + trialDays * 24 * 60 * 60 * 1000).toISOString();
    const license = await db.setLicense({
      id: crypto.randomUUID(),
      user_id: userId,
      plan_type: 'trial',
      expires_at: expiresAt,
      status: 'active',
    });

    // 4. 簽發 JWT Token
    const token = await signJwt({
      userId,
      email: user.email,
      deviceFingerprint: device_fingerprint,
    });

    return NextResponse.json<AuthResponse>({
      success: true,
      token,
      user: {
        id: user.id,
        email: user.email,
        name: user.name,
      },
      device: {
        device_fingerprint: device.device_fingerprint,
        device_model: device.device_model,
        bound_at: device.bound_at,
      },
      license: {
        plan_type: license.plan_type,
        expires_at: license.expires_at,
        status: license.status,
        days_remaining: trialDays,
        is_valid: true,
      },
    });
  } catch (error) {
    console.error('Register API Error:', error);
    return NextResponse.json<AuthResponse>(
      { success: false, error: '伺服器內部錯誤，請稍後再試', error_code: 'SERVER_ERROR' },
      { status: 500 }
    );
  }
}
