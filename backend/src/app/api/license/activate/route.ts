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

    const result = await db.activateLicenseWithCode(deviceFingerprint, licenseCode);
    if (!result.success) {
      return NextResponse.json(
        { success: false, error: result.error || '開通失敗' },
        { status: 400 }
      );
    }

    return NextResponse.json({
      success: true,
      message: '授權開通成功！已升級為專業版。',
      plan_type: result.license?.plan_type || 'yearly',
      expires_at: result.license?.expires_at,
      days_remaining: 365,
    });
  } catch (error) {
    console.error('License Activate Error:', error);
    return NextResponse.json(
      { success: false, error: '伺服器錯誤，請稍後再試' },
      { status: 500 }
    );
  }
}
