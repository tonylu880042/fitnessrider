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

    const existingAnchor = await db.getDeviceTrialAnchor(deviceFingerprint);
    if (existingAnchor?.device_secret) {
      if (!authorized) {
        if (isPromoCode(licenseCode)) {
          return NextResponse.json(
            { success: false, error: '此設備已完成開通綁定，需透過原設備簽章或帳號登入驗證', error_code: 'DEVICE_SECRET_REQUIRED' },
            { status: 403 }
          );
        } else {
          const vipInfo = verifyVipSerial(licenseCode);
          if (!vipInfo) {
            return NextResponse.json(
              { success: false, error: '無效的授權序號或推廣代碼', error_code: 'INVALID_CODE' },
              { status: 400 }
            );
          }

          const isOwnedByThisDevice = await db.isSerialClaimedByDevice(vipInfo.serialId, deviceFingerprint);
          if (!isOwnedByThisDevice) {
            return NextResponse.json(
              { success: false, error: '此設備已完成開通綁定，需透過原設備簽章驗證', error_code: 'DEVICE_SECRET_REQUIRED' },
              { status: 403 }
            );
          }
        }
      }
    }

    const platform = body.platform === 'android' ? 'android' : 'ios';
    const deviceModel = body.device_model || body.deviceModel || (platform === 'android' ? 'Android Device' : 'iPad / iPhone');
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
