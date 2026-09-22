import { NextRequest, NextResponse } from 'next/server';
import { db, isPromoCode } from '@/lib/db';
import { verifyJwt } from '@/lib/auth';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const deviceFingerprint = body.device_fingerprint || body.deviceFingerprint;
    const licenseCode = (body.license_code || body.licenseCode || '').trim();

    if (!deviceFingerprint || !licenseCode) {
      return NextResponse.json(
        { success: false, error: '缺少必要參數 (device_fingerprint, license_code)' },
        { status: 400 }
      );
    }

    // 若該裝置先前已開通過且已有 device_secret，且使用者輸入的是「公開推廣代碼」，未經授權（無 JWT / 無原裝置簽章）的請求一律阻擋，
    // 防止任何人僅靠 ANDROID_ID + 公開推廣碼（如 26FR-NR）篡改受害者授權或探測密鑰（spec 項目 1）。
    // 若使用者輸入的是付費 VIP 序號（非公開推廣碼），ECDSA 簽章序號本身即為私密持有人憑證，
    // 在 db.activateLicenseWithCode 內由 claimVipSerial 檢驗該序號是否已綁定此設備或未被認領；
    // 讓 Android 重裝（SharedPreferences 清空、密鑰遺失）的合法付費 VIP 擁有者能夠憑其合法序號成功重新開通並復原密鑰（spec 項目 2）。
    if (isPromoCode(licenseCode)) {
      const existingAnchor = await db.getDeviceTrialAnchor(deviceFingerprint);
      if (existingAnchor?.device_secret) {
        let authorized = false;
        const authHeader = req.headers.get('authorization');
        if (authHeader && authHeader.startsWith('Bearer ')) {
          const token = authHeader.substring(7);
          const decoded = await verifyJwt(token);
          if (decoded) {
            authorized = true;
          }
        }
        if (!authorized) {
          const timestamp = Number(body.timestamp);
          const signature = typeof body.signature === 'string' ? body.signature : '';
          if (timestamp && signature) {
            authorized = await db.verifyDeviceSignature(deviceFingerprint, timestamp, signature);
          }
        }
        if (!authorized) {
          return NextResponse.json(
            { success: false, error: '此設備已完成開通綁定，需透過原設備簽章或帳號登入驗證' },
            { status: 403 }
          );
        }
      }
    }

    const platform = body.platform === 'android' ? 'android' : 'ios';
    const deviceModel = body.device_model || body.deviceModel || (platform === 'android' ? 'Android Device' : 'iPad / iPhone');
    const clientFirstLaunchAt = (body.client_first_launch_at || body.clientFirstLaunchAt) as string | undefined;

    const result = await db.activateLicenseWithCode(deviceFingerprint, licenseCode, platform, deviceModel, clientFirstLaunchAt);
    if (!result.success) {
      return NextResponse.json(
        { success: false, error: result.error || '開通失敗' },
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
