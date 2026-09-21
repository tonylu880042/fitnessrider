import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import { comparePassword, signJwt } from '@/lib/auth';
import { AuthResponse } from '@/lib/types';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const { email, password, platform } = body;
    const device_fingerprint = body.device_fingerprint || body.deviceFingerprint;
    const device_model = body.device_model || body.deviceModel;

    if (!email || !password || !device_fingerprint) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '請輸入帳號、密碼與設備碼', error_code: 'INVALID_CREDENTIALS' },
        { status: 400 }
      );
    }

    const user = await db.getUserByEmail(email);
    if (!user) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '帳號或密碼錯誤', error_code: 'INVALID_CREDENTIALS' },
        { status: 401 }
      );
    }

    const isValidPassword = await comparePassword(password, user.password_hash);
    if (!isValidPassword) {
      return NextResponse.json<AuthResponse>(
        { success: false, error: '帳號或密碼錯誤', error_code: 'INVALID_CREDENTIALS' },
        { status: 401 }
      );
    }

    // 檢核單一設備綁定
    const boundDevice = await db.getDeviceByUserId(user.id);
    if (!boundDevice) {
      // 首次從 App 登入，自動綁定
      await db.bindDevice({
        id: crypto.randomUUID(),
        user_id: user.id,
        device_fingerprint,
        platform: platform === 'android' ? 'android' : 'ios',
        device_model: device_model || 'Unknown Device',
      });
    } else if (boundDevice.device_fingerprint !== device_fingerprint) {
      // 設備不相符（防共用作弊）
      return NextResponse.json<AuthResponse>(
        {
          success: false,
          error: `此帳號目前已綁定於其他設備 [${boundDevice.device_model || '已綁定設備'}]。一個帳號限定單一設備使用。`,
          error_code: 'DEVICE_MISMATCH',
          current_bound_device: {
            device_model: boundDevice.device_model,
            bound_at: boundDevice.bound_at,
          },
        },
        { status: 403 }
      );
    } else {
      // 相同設備，更新最後活躍時間
      await db.updateDeviceLastActive(user.id);
    }

    // 檢查授權狀態
    const license = await db.getLicenseByUserId(user.id);
    const now = Date.now();
    const expiresAtMs = license ? new Date(license.expires_at).getTime() : 0;
    const isValidLicense = expiresAtMs > now && license?.status === 'active';
    const daysRemaining = Math.max(0, Math.ceil((expiresAtMs - now) / (1000 * 60 * 60 * 24)));

    // 簽發 JWT Token
    const token = await signJwt({
      userId: user.id,
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
        device_fingerprint,
        device_model: boundDevice?.device_model || device_model,
        bound_at: boundDevice?.bound_at || new Date().toISOString(),
      },
      license: license
        ? {
            plan_type: license.plan_type,
            expires_at: license.expires_at,
            status: isValidLicense ? 'active' : 'expired',
            days_remaining: daysRemaining,
            is_valid: isValidLicense,
          }
        : {
            plan_type: 'trial',
            expires_at: new Date(0).toISOString(),
            status: 'expired',
            days_remaining: 0,
            is_valid: false,
          },
    });
  } catch (error) {
    console.error('Login API Error:', error);
    return NextResponse.json<AuthResponse>(
      { success: false, error: '伺服器內部錯誤，請稍後再試', error_code: 'SERVER_ERROR' },
      { status: 500 }
    );
  }
}
