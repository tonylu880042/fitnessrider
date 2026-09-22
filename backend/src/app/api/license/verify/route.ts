import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { verifyJwt } from '@/lib/auth';
import { minSupportedVersionCodeFor } from '@/lib/licenseConfig';

/**
 * 授權狀態查詢端點。
 *
 * 修正前：任何人只要送一個 device_fingerprint（Android 用的 ANDROID_ID 並不是秘密）
 * 就能查到「那台裝置」綁定的帳號、方案與到期日 —— 是一個未經授權就能查詢的 oracle
 * （spec 項目 E）。修正後，要嘛帶有效的 JWT（Bearer），要嘛帶「裝置簽章」
 * （timestamp + HMAC-SHA256(device_secret, "<fingerprint>.<timestamp>")，
 * device_secret 是裝置第一次成功開通授權/推廣代碼時，/api/license/activate 回傳並
 * 由 App 端保存的密鑰）。兩者都沒有時，一律只回傳「未註冊/一般試用中」的最小資訊，
 * 不查詢、也不揭露任何裝置的真實付費狀態。
 *
 * 不論走哪個分支，都會回傳 `trial_started_at`（伺服器端試用起算錨點，供 App 端與本機
 * 快取取較早者，讓 Android 重灌後試用不會被重置，spec 項目 D）與
 * `min_supported_version_code`（App 啟動時一併帶回的強制更新門檻，spec 項目 F），
 * 這兩個欄位不敏感，可以放心對任何呼叫者回傳。
 */
// In-memory rate limiter per IP (max 60 requests per minute)
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();

function isRateLimited(ip: string): boolean {
  const now = Date.now();
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
      // body 是選填的（例如只帶 Bearer JWT 的情況）
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

    // 若沒有 JWT，嘗試用裝置簽章驗證身分
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

    // 試用錨點：未通過身分驗證時，僅查詢既有紀錄，查無紀錄回傳 null，絕不自動新增紀錄（防止未認證濫建錨點，spec 項目 10）。
    // 若已通過身分驗證，則確保該已驗證裝置有錨點紀錄。
    let anchor = await db.getDeviceTrialAnchor(deviceFingerprint);
    if (!anchor && authorizedUserId) {
      anchor = await db.getOrCreateDeviceTrialAnchor(deviceFingerprint);
    }

    const commonFields = {
      trial_started_at: anchor?.first_seen_at || null,
      min_supported_version_code: minSupportedVersionCode,
    };

    if (!authorizedUserId) {
      // 未通過身分驗證：一律只回傳最小、非敏感的資訊，不查詢真實授權狀態。
      return NextResponse.json({
        success: true,
        is_valid: false,
        days_remaining: 0,
        plan_type: 'none',
        status: 'unregistered',
        ...commonFields,
      });
    }

    // 1. 檢驗設備綁定
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

    // 2. 更新最後連線時間
    await db.updateDeviceLastActive(authorizedUserId);

    // 3. 檢驗授權到期日
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
