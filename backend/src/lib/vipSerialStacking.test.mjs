import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { register } from 'node:module';

process.env.AWS_LAMBDA_FUNCTION_NAME = process.env.AWS_LAMBDA_FUNCTION_NAME || 'fitnessrider-test-runner';

const { publicKey, privateKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
process.env.VIP_SERIAL_PUBLIC_KEY_SPKI_B64 = publicKey
  .export({ type: 'spki', format: 'der' })
  .toString('base64');

const DATA_FILE = path.join('/tmp', '.fitnessrider-data', 'db.json');
fs.rmSync(DATA_FILE, { force: true });

register(new URL('./tsExtensionResolveHook.mjs', import.meta.url));

const { db } = await import('./db.ts');
const { stackedVipExpiry } = await import('./licenseConfig.ts');
const { buildVipSerialPayload, encodeVipSerial } = await import('./vipSerial.ts');

test.after(() => {
  fs.rmSync(DATA_FILE, { force: true });
});

function makeSerial(serialIdHex, planDays) {
  const payload = buildVipSerialPayload(serialIdHex, planDays);
  const signature = crypto.sign('sha256', payload, privateKey);
  return encodeVipSerial(payload, signature);
}

function randomSerialId() {
  return crypto.randomBytes(4).toString('hex');
}

test('stackedVipExpiry：無現有到期時間 -> now + planDays', () => {
  const now = Date.parse('2026-09-29T00:00:00Z');
  const result = stackedVipExpiry(now, null, 90);
  assert.equal(result, now + 90 * 24 * 60 * 60 * 1000);
});

test('stackedVipExpiry：現有到期時間在未來 -> 現有到期時間 + planDays', () => {
  const now = Date.parse('2026-09-29T00:00:00Z');
  const currentExpiry = now + 30 * 24 * 60 * 60 * 1000;
  const result = stackedVipExpiry(now, currentExpiry, 90);
  assert.equal(result, currentExpiry + 90 * 24 * 60 * 60 * 1000);
});

test('stackedVipExpiry：現有到期時間已過期 -> now + planDays', () => {
  const now = Date.parse('2026-09-29T00:00:00Z');
  const currentExpiry = now - 10 * 24 * 60 * 60 * 1000;
  const result = stackedVipExpiry(now, currentExpiry, 90);
  assert.equal(result, now + 90 * 24 * 60 * 60 * 1000);
});

test('同一裝置兌換兩組不同序號時到期時間相加（疊加）', async () => {
  const fingerprint = `fp-stack-${crypto.randomUUID()}`;
  const serialA = makeSerial(randomSerialId(), 100);
  const serialB = makeSerial(randomSerialId(), 30);

  const resA = await db.activateLicenseWithCode(fingerprint, serialA);
  assert.equal(resA.success, true);
  const expiresAtA = new Date(resA.license.expires_at).getTime();

  const resB = await db.activateLicenseWithCode(fingerprint, serialB);
  assert.equal(resB.success, true);
  const expiresAtB = new Date(resB.license.expires_at).getTime();

  const diffDays = Math.round((expiresAtB - expiresAtA) / (24 * 60 * 60 * 1000));
  assert.equal(diffDays, 30);
});

test('同一序號在同一裝置重複兌換：不加天數、到期時間不變', async () => {
  const fingerprint = `fp-repeat-${crypto.randomUUID()}`;
  const serial = makeSerial(randomSerialId(), 200);

  const first = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(first.success, true);
  const firstExpiry = first.license.expires_at;

  const second = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(second.success, true);
  assert.equal(second.already_claimed_by_device, true);
  assert.equal(second.trial_days, 0);
  assert.equal(second.license.expires_at, firstExpiry);
});

test('同一序號換另一台裝置仍被拒絕', async () => {
  const fingerprintA = `fp-owner-${crypto.randomUUID()}`;
  const fingerprintB = `fp-other-${crypto.randomUUID()}`;
  const serial = makeSerial(randomSerialId(), 60);

  const first = await db.activateLicenseWithCode(fingerprintA, serial);
  assert.equal(first.success, true);

  const second = await db.activateLicenseWithCode(fingerprintB, serial);
  assert.equal(second.success, false);
  assert.equal(second.error_code, 'VIP_SERIAL_ALREADY_CLAIMED');
});

test('同一序號到期後重新輸入：回傳 VIP_SERIAL_ALREADY_USED，且原授權紀錄不變', async () => {
  const fingerprint = `fp-expired-repeat-${crypto.randomUUID()}`;
  const serial = makeSerial(randomSerialId(), 10);

  const first = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(first.success, true);

  const expiredAt = new Date(Date.now() - 1000).toISOString();
  await db.setLicense({
    id: first.license.id,
    user_id: first.license.user_id,
    plan_type: first.license.plan_type,
    expires_at: expiredAt,
    status: 'active',
    revenuecat_entitlement_id: first.license.revenuecat_entitlement_id,
  });

  const second = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(second.success, false);
  assert.equal(second.error_code, 'VIP_SERIAL_ALREADY_USED');

  const licenseAfter = await db.getLicenseByUserId(first.license.user_id);
  assert.equal(licenseAfter.expires_at, expiredAt);
  assert.equal(licenseAfter.plan_type, first.license.plan_type);
});

test('重新輸入舊序號不會覆蓋目前生效中的推廣代碼授權', async () => {
  const fingerprint = `fp-promo-not-overwritten-${crypto.randomUUID()}`;
  const serial = makeSerial(randomSerialId(), 5);

  const vipRes = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(vipRes.success, true);

  const expiredAt = new Date(Date.now() - 1000).toISOString();
  await db.setLicense({
    id: vipRes.license.id,
    user_id: vipRes.license.user_id,
    plan_type: vipRes.license.plan_type,
    expires_at: expiredAt,
    status: 'active',
    revenuecat_entitlement_id: vipRes.license.revenuecat_entitlement_id,
  });

  const currentYearShort = String(new Date().getFullYear() % 100).padStart(2, '0');
  const promoCode = `${currentYearShort}FR-NR`;
  const promoRes = await db.activateLicenseWithCode(fingerprint, promoCode);
  assert.equal(promoRes.success, true);
  assert.equal(promoRes.license.plan_type, 'promo_trial_30d');
  const promoExpiresAt = promoRes.license.expires_at;

  const reentered = await db.activateLicenseWithCode(fingerprint, serial);
  assert.equal(reentered.success, true);
  assert.equal(reentered.already_claimed_by_device, true);
  assert.equal(reentered.is_promo, true);
  assert.equal(reentered.license.plan_type, 'promo_trial_30d');
  assert.equal(reentered.license.expires_at, promoExpiresAt);

  const licenseAfter = await db.getLicenseByUserId(vipRes.license.user_id);
  assert.equal(licenseAfter.plan_type, 'promo_trial_30d');
  assert.equal(licenseAfter.expires_at, promoExpiresAt);
});
