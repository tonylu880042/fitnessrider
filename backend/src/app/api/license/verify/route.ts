import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { verifyJwt } from '@/lib/auth';
import { minSupportedVersionCodeFor } from '@/lib/licenseConfig';

const rateLimitMap = new Map<string, { count: number; resetAt: number }>();
const MAX_RATE_LIMIT_ENTRIES = 1000;

function isRateLimited(ip: string): boolean {
  const now = Date.now();
  if (rateLimitMap.size > MAX_RATE_LIMIT_ENTRIES) {
    for (const [k, v] of rateLimitMap) {
      if (now > v.resetAt) rateLimitMap.delete(k);
    }
  }
  const entry = rateLimitMap.get(ip);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(ip, { count: 1, resetAt: now + 60_000 });
    return false;
  }
  entry.count++;
  return entry.count > 60;
}

export async function POST(req: NextRequest) {
  try {
    const clientIp = req.headers.get('x-forwarded-for')?.split(',')[0].trim() || '127.0.0.1';
    if (isRateLimited(clientIp)) {
      return NextResponse.json(
        { success: false, error: '請求過於頻繁，請稍候再試', is_valid: false },
        { status: 429 }
      );
    }

    let authorizedUserId: string | null = null;

    const authHeader = req.headers.get('authorization');
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      const decoded = await verifyJwt(token);
      if (decoded) {
        authorizedUserId = decoded.userId;
      }
    }

    let body: Record<string, unknown> = {};
    try {
      body = await req.json();
    } catch {
    }

    const deviceFingerprint = (body.device_fingerprint || body.deviceFingerprint) as string | undefined;
    const platform = body.platform === 'android' ? 'android' : 'ios';
    const minSupportedVersionCode = minSupportedVersionCodeFor(platform);

    if (!deviceFingerprint || typeof deviceFingerprint !== 'string' || deviceFingerprint.length < 4 || deviceFingerprint.length > 128) {
      return NextResponse.json(
        { success: false, error: '缺少或無效設備識別碼', is_valid: false },
        { status: 400 }
      );
    }

    if (!authorizedUserId) {
      const timestamp = Number(body.timestamp);
      const signature = typeof body.signature === 'string' ? body.signature : '';
      if (timestamp && signature) {
        const signatureValid = await db.verifyDeviceSignature(deviceFingerprint, timestamp, signature);
        if (signatureValid) {
          const dev = await db.getDeviceByFingerprint(deviceFingerprint);
          if (dev) authorizedUserId = dev.user_id;
        }
      }
    }

    const anchor = await db.getOrCreateDeviceTrialAnchor(deviceFingerprint);

    const commonFields = {
      trial_started_at: anchor?.first_seen_at || null,
      min_supported_version_code: minSupportedVersionCode,
    };

    if (!authorizedUserId) {
      return NextResponse.json({
        success: true,
        is_valid: false,
        days_remaining: 0,
        plan_type: 'none',
        status: 'unregistered',
        ...commonFields,
      });
    }

    const boundDevice = await db.getDeviceByUserId(authorizedUserId);
    if (!boundDevice || boundDevice.device_fingerprint !== deviceFingerprint) {
      return NextResponse.json(
        {
          success: false,
          error: '設備識別碼不相符，該帳號已綁定於其他設備',
          is_valid: false,
          error_code: 'DEVICE_MISMATCH',
          ...commonFields,
        },
        { status: 403 }
      );
    }

    await db.updateDeviceLastActive(authorizedUserId);

    const license = await db.getLicenseByUserId(authorizedUserId);
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
      ...commonFields,
    });
  } catch (error) {
    console.error('License Verify Error:', error);
    return NextResponse.json(
      { success: false, error: '驗證失敗，請檢查網路連線', is_valid: false },
      { status: 500 }
    );
  }
}
