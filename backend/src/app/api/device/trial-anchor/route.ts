import { NextRequest, NextResponse } from 'next/server';
import { db } from '@/lib/db';
import { minSupportedVersionCodeFor } from '@/lib/licenseConfig';

/**
 * 讓 App 用裝置指紋（Android 為 ANDROID_ID）取得（或建立）伺服器端的試用起算時間。
 *
 * 這是刻意設計成「不需要簽章驗證」的窄範圍端點：只回傳「這台裝置何時第一次被看到」，
 * 不會回傳授權方案、到期日等任何敏感的付費狀態（那些一律要透過 /api/license/verify 並經過
 * JWT 或裝置簽章驗證才會回傳，見 spec 項目 E）。目的是讓 Android 重灌後仍能用同一個
 * ANDROID_ID 對回同一筆試用錨點時間，不會因為 SharedPreferences 被清空而重置試用
 * （spec 項目 D）。App 端仍應以「本機快取的錨點」與「伺服器回傳的錨點」取較早者為準，
 * 離線時就先用本機值。
 */
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
