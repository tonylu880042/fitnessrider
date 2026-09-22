import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { BASE_TRIAL_DAYS, PROMO_TOTAL_TRIAL_DAYS } from './licenseConfig.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

test('licenseConfig constants match promo.properties', () => {
  // Locate promo.properties at repository root
  const promoPropsPath = path.resolve(__dirname, '../../../promo.properties');
  assert.ok(fs.existsSync(promoPropsPath), `promo.properties must exist at ${promoPropsPath}`);

  const content = fs.readFileSync(promoPropsPath, 'utf8');
  const lines = content.split('\n');

  let promoPropsBaseTrialDays = null;
  let promoPropsTrialDays = null;

  for (const line of lines) {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || !trimmed.includes('=')) continue;
    const [k, v] = trimmed.split('=').map(s => s.trim());
    if (k === 'BASE_TRIAL_DAYS') promoPropsBaseTrialDays = parseInt(v, 10);
    if (k === 'TRIAL_DAYS') promoPropsTrialDays = parseInt(v, 10);
  }

  assert.equal(typeof promoPropsBaseTrialDays, 'number', 'BASE_TRIAL_DAYS must be defined in promo.properties');
  assert.equal(typeof promoPropsTrialDays, 'number', 'TRIAL_DAYS must be defined in promo.properties');

  assert.equal(
    BASE_TRIAL_DAYS,
    promoPropsBaseTrialDays,
    `licenseConfig.ts BASE_TRIAL_DAYS (${BASE_TRIAL_DAYS}) must match promo.properties (${promoPropsBaseTrialDays})`
  );

  assert.equal(
    PROMO_TOTAL_TRIAL_DAYS,
    promoPropsTrialDays,
    `licenseConfig.ts PROMO_TOTAL_TRIAL_DAYS (${PROMO_TOTAL_TRIAL_DAYS}) must match promo.properties TRIAL_DAYS (${promoPropsTrialDays})`
  );
});
