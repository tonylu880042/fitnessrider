import { NextRequest, NextResponse } from 'next/server';
import { db, isPromoCode } from '@/lib/db';
import { verifyJwt } from '@/lib/auth';
import { verifyVipSerial } from '@/lib/vipSerial';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const deviceFingerprint = body.device_fingerprint || body.deviceFingerprint;
    const licenseCode = (body.license_code || body.licenseCode || '').trim();

    if (!deviceFingerprint || !licenseCode) {
      return NextResponse.json(
        { success: false, error: '缺少必要參數 (device_fingerprint, license_code)', error_code: 'MISSING_PARAMS' },
        { status: 400 }
      );
    }

    // 先判定呼叫端身分：JWT 帳號目前綁定的設備要與目標設備相符，或帶有原設備密鑰簽章。
    let authorized = false;
    const authHeader = req.headers.get('authorization');
    if (authHeader && authHeader.startsWith('Bearer ')) {
      const token = authHeader.substring(7);
      const decoded = await verifyJwt(token);
      if (decoded) {
        const boundDevice = await db.getDeviceByUserId(decoded.userId);
        if (boundDevice && boundDevice.device_fingerprint === deviceFingerprint) {
          authorized = true;
        }
      }
    }
    if (!authorized) {
      const timestamp = Number(body.timestamp);
      const signature = typeof body.signature === 'string' ? body.signature : '';
      if (timestamp && signature) {
        authorized = await db.verifyDeviceSignature(deviceFingerprint, timestamp, signature);
      }
    }

    // 驗證檢查：若該裝置先前已開通過且已有 device_secret，未經授權的請求必須受檢
    const existingAnchor = await db.getDeviceTrialAnchor(deviceFingerprint);
    if (existingAnchor?.device_secret) {
      if (!authorized) {
        if (isPromoCode(licenseCode)) {
          // 公開推廣碼（如 26FR-NR）任何人皆可輸入，未認證請求一律以 403 阻擋，避免他人篡改受害者授權（spec 項目 1）
          return NextResponse.json(
            { success: false, error: '此設備已完成開通綁定，需透過原設備簽章或帳號登入驗證', error_code: 'DEVICE_SECRET_REQUIRED' },
            { status: 403 }
          );
        } else {
          // 付費 VIP 序號：
          const vipInfo = verifyVipSerial(licenseCode);
          if (!vipInfo) {
            return NextResponse.json(
              { success: false, error: '無效的授權序號或推廣代碼', error_code: 'INVALID_CODE' },
              { status: 400 }
            );
          }

          // 檢查此序號是否「原先就已綁定於本設備」（合法用戶重裝 Android 復原情境）
          const isOwnedByThisDevice = await db.isSerialClaimedByDevice(vipInfo.serialId, deviceFingerprint);
          if (!isOwnedByThisDevice) {
            // 此序號若為「全新未認領序號」或「屬於其他設備之序號」，在已有密鑰的設備上未經原設備簽章認證一律拒絕！
            // 防止持有未認領序號的攻擊者惡意綁定受害者的 fingerprint 並覆寫其密鑰（spec 項目 2）。
            return NextResponse.json(
              { success: false, error: '此設備已完成開通綁定，需透過原設備簽章驗證', error_code: 'DEVICE_SECRET_REQUIRED' },
              { status: 403 }
            );
          }
          // 若是原先就已綁定於本設備的序號，證明呼叫端為合法持有該序號的原機重灌用戶，放行重新開通！
        }
      }
    }

    const platform = body.platform === 'android' ? 'android' : 'ios';
    const deviceModel = body.device_model || body.deviceModel || (platform === 'android' ? 'Android Device' : 'iPad / iPhone');
    // 只有通過身分驗證的請求才可以把試用起算錨點往回推。未認證請求一律用伺服器當下時間建立錨點 ——
    // 否則任何人只要拿受害者的 ANDROID_ID 送一次 client_first_launch_at=<30 天前>，
    // 錨點就會被種成過期值（錨點一旦建立就不再移動），受害者首次啟動當下試用即已結束。
    const clientFirstLaunchAt = authorized
      ? ((body.client_first_launch_at || body.clientFirstLaunchAt) as string | undefined)
      : undefined;

    const result = await db.activateLicenseWithCode(deviceFingerprint, licenseCode, platform, deviceModel, clientFirstLaunchAt);
    if (!result.success) {
      return NextResponse.json(
        { success: false, error: result.error || '開通失敗', error_code: result.error_code },
        { status: 400 }
      );
    }

    const isPromo = result.is_promo;
    return NextResponse.json({
      success: true,
      message: isPromo
        ? '推廣課程專屬代碼兌換成功！已為此設備啟用 30 天全功能免費 VIP 體驗。'
        : '授權開通成功！已升級為專業年繳版。',
      plan_type: result.license?.plan_type || (isPromo ? 'promo_trial_30d' : 'yearly'),
      expires_at: result.license?.expires_at,
      days_remaining: result.trial_days || (isPromo ? 30 : 365),
      is_promo: isPromo,
      // 裝置密鑰：僅在首次為新設備建立時回傳供 App 端保存，已有密鑰者絕不回傳既有值。
      ...(result.device_secret ? { device_secret: result.device_secret } : {}),
    });
  } catch (error: unknown) {
    console.error('License Activate Error:', error);
    const msg = error instanceof Error ? error.message : '伺服器錯誤，請稍後再試';
    return NextResponse.json(
      { success: false, error: msg },
      { status: 500 }
    );
  }
}
