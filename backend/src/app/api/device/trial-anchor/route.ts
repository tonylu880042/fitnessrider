import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { minSupportedVersionCodeFor } from '@/lib/licenseConfig';

export async function POST(req: NextRequest) {
  try {
    const body = await req.json();
    const deviceFingerprint = body.device_fingerprint || body.deviceFingerprint;
    const platform = body.platform === 'android' ? 'android' : 'ios';

    if (!deviceFingerprint || typeof deviceFingerprint !== 'string') {
      return NextResponse.json({ success: false, error: '缺少必要設備識別碼' }, { status: 400 });
    }

    const anchor = await db.getDeviceTrialAnchor(deviceFingerprint);

    return NextResponse.json({
      success: true,
      trial_started_at: anchor?.first_seen_at || null,
      min_supported_version_code: minSupportedVersionCodeFor(platform),
    });
  } catch (error) {
    console.error('Device Trial Anchor Error:', error);
    return NextResponse.json({ success: false, error: '查詢失敗，請檢查網路連線' }, { status: 500 });
  }
}
