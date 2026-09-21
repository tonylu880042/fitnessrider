import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { verifyJwt } from '@/lib/auth';

export async function POST(req: NextRequest) {
  try {
    let userId: string | null = null;
    let deviceFingerprint: string | null = null;

    // 支援 Authorization Header (Bearer JWT)
    const authHeader = req.headers.get('authorization');
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      const decoded = await verifyJwt(token);
      if (decoded) {
        userId = decoded.userId;
        deviceFingerprint = decoded.deviceFingerprint;
      }
    }

    // 嘗試解析 Request Body (支援 snake_case & camelCase)
    try {
      const body = await req.json();
      const bodyFingerprint = body.device_fingerprint || body.deviceFingerprint;
      if (bodyFingerprint) {
        deviceFingerprint = bodyFingerprint;
      }
      if (!userId && body.email) {
        const user = await db.getUserByEmail(body.email);
        if (user) {
          userId = user.id;
        }
      }
    } catch {
      // body parsing optional
    }

    if (!userId || !deviceFingerprint) {
      return NextResponse.json(
        { success: false, error: '未授權或缺少必要驗證參數', is_valid: false },
        { status: 401 }
      );
    }

    // 1. 檢驗設備綁定
    const boundDevice = await db.getDeviceByUserId(userId);
    if (!boundDevice || boundDevice.device_fingerprint !== deviceFingerprint) {
      return NextResponse.json(
        {
          success: false,
          error: '設備識別碼不相符，該帳號已綁定於其他設備',
          is_valid: false,
          error_code: 'DEVICE_MISMATCH',
        },
        { status: 403 }
      );
    }

    // 2. 更新最後連線時間
    await db.updateDeviceLastActive(userId);

    // 3. 檢驗授權到期日
    const license = await db.getLicenseByUserId(userId);
    const now = Date.now();
    const expiresAtMs = license ? new Date(license.expires_at).getTime() : 0;
    const isValid = expiresAtMs > now && license?.status === 'active';
    const daysRemaining = Math.max(0, Math.ceil((expiresAtMs - now) / (1000 * 60 * 60 * 24)));

    return NextResponse.json({
      success: true,
      is_valid: isValid,
      days_remaining: daysRemaining,
      plan_type: license?.plan_type || 'none',
      expires_at: license?.expires_at || null,
      status: isValid ? 'active' : 'expired',
      device_model: boundDevice.device_model,
    });
  } catch (error) {
    console.error('License Verify Error:', error);
    return NextResponse.json(
      { success: false, error: '驗證失敗，請檢查網路連線', is_valid: false },
      { status: 500 }
    );
  }
}
