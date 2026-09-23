import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { register } from 'node:module';

process.env.AWS_LAMBDA_FUNCTION_NAME = process.env.AWS_LAMBDA_FUNCTION_NAME || 'fitnessrider-test-runner';

const DATA_FILE = path.join('/tmp', '.fitnessrider-data', 'db.json');
fs.rmSync(DATA_FILE, { force: true });

register(new URL('./tsExtensionResolveHook.mjs', import.meta.url));

const { db } = await import('./db.ts');

test.after(() => {
  fs.rmSync(DATA_FILE, { force: true });
});

test('指紋查得到既有裝置時，解析到該裝置綁定的 user_id', async () => {
  const fingerprint = `fp-existing-${crypto.randomUUID()}`;
  const existingUser = await db.createUser({
    id: crypto.randomUUID(),
    email: `existing_${fingerprint}@fitnessrider.local`,
    password_hash: 'local_license_auth',
    name: '既有教練',
  });
  await db.bindDevice({
    id: crypto.randomUUID(),
    user_id: existingUser.id,
    device_fingerprint: fingerprint,
    platform: 'ios',
    device_model: 'iPhone 15',
  });

  const resolved = await db.getOrCreateUserForDevice(fingerprint);
  assert.equal(resolved.id, existingUser.id);

  const device = await db.getDeviceByFingerprint(fingerprint);
  assert.equal(device?.user_id, existingUser.id);
});

test('指紋查不到時建立合成 user 並綁定該指紋，之後同一指紋解析到同一個 user', async () => {
  const fingerprint = `fp-new-${crypto.randomUUID()}`;

  const beforeDevice = await db.getDeviceByFingerprint(fingerprint);
  assert.equal(beforeDevice, null);

  const created = await db.getOrCreateUserForDevice(fingerprint);
  assert.ok(created.id);
  assert.equal(created.email, `coach_${fingerprint.slice(0, 8)}@fitnessrider.local`);

  const device = await db.getDeviceByFingerprint(fingerprint);
  assert.ok(device);
  assert.equal(device.user_id, created.id);

  const resolvedAgain = await db.getOrCreateUserForDevice(fingerprint);
  assert.equal(resolvedAgain.id, created.id);

  const deviceAfterSecondCall = await db.getDeviceByFingerprint(fingerprint);
  assert.equal(deviceAfterSecondCall?.id, device.id);
});
