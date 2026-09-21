import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';

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

    const platform = body.platform === 'android' ? 'android' : 'ios';
    const deviceModel = body.device_model || body.deviceModel || (platform === 'android' ? 'Android Device' : 'iPad / iPhone');

    const result = await db.activateLicenseWithCode(deviceFingerprint, licenseCode, platform, deviceModel);
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
