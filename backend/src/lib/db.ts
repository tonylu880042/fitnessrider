import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { Pool } from 'pg';
import type { User, Device, License, DeviceTransferLog, PromoRedemption, DeviceTrialAnchor, VipSerialRedemption } from './types';
import { PROMO_TOTAL_TRIAL_DAYS } from './licenseConfig';
import { verifyVipSerial } from './vipSerial';

interface InMemoryData {
  users: User[];
  devices: Device[];
  licenses: License[];
  device_transfers: DeviceTransferLog[];
  promo_redemptions: PromoRedemption[];
  device_trials: DeviceTrialAnchor[];
  vip_serial_redemptions: VipSerialRedemption[];
}

let pgPool: Pool | null = null;

if (process.env.DATABASE_URL) {
  pgPool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: process.env.DATABASE_URL.includes('localhost') ? false : { rejectUnauthorized: false },
  });
}

let memoryFallback: InMemoryData = {
  users: [],
  devices: [],
  licenses: [],
  device_transfers: [],
  promo_redemptions: [],
  device_trials: [],
  vip_serial_redemptions: [],
};

const DATA_DIR = process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME
  ? path.join('/tmp', '.fitnessrider-data')
  : path.join(process.cwd(), '.data');
const DATA_FILE = path.join(DATA_DIR, 'db.json');

function ensureLocalDb(): InMemoryData {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    if (!fs.existsSync(DATA_FILE)) {
      fs.writeFileSync(DATA_FILE, JSON.stringify(memoryFallback, null, 2), 'utf-8');
      return memoryFallback;
    }
    const raw = fs.readFileSync(DATA_FILE, 'utf-8');
    const parsed = JSON.parse(raw) as InMemoryData;
    if (!parsed.promo_redemptions) parsed.promo_redemptions = [];
    if (!parsed.device_trials) parsed.device_trials = [];
    if (!parsed.vip_serial_redemptions) parsed.vip_serial_redemptions = [];
    memoryFallback = parsed;
    return parsed;
  } catch (err) {
    console.warn('Local DB disk access warning, using memory fallback:', err);
    return memoryFallback;
  }
}

function saveLocalDb(data: InMemoryData) {
  memoryFallback = data;
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(DATA_FILE, JSON.stringify(data, null, 2), 'utf-8');
  } catch (err) {
    console.warn('Local DB write warning, saved in memory fallback:', err);
  }
}

let pgInitialized = false;
async function initPgTables() {
  if (!pgPool || pgInitialized) return;
  const client = await pgPool.connect();
  try {
    await client.query(`
      CREATE TABLE IF NOT EXISTS users (
        id VARCHAR(64) PRIMARY KEY,
        email VARCHAR(255) UNIQUE NOT NULL,
        password_hash VARCHAR(255) NOT NULL,
        name VARCHAR(100),
        created_at TIMESTAMPTZ DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS devices (
        id VARCHAR(64) PRIMARY KEY,
        user_id VARCHAR(64) UNIQUE REFERENCES users(id) ON DELETE CASCADE,
        device_fingerprint VARCHAR(255) NOT NULL,
        platform VARCHAR(20) NOT NULL,
        device_model VARCHAR(100),
        bound_at TIMESTAMPTZ DEFAULT NOW(),
        last_active_at TIMESTAMPTZ DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS licenses (
        id VARCHAR(64) PRIMARY KEY,
        user_id VARCHAR(64) UNIQUE REFERENCES users(id) ON DELETE CASCADE,
        plan_type VARCHAR(20) NOT NULL,
        expires_at TIMESTAMPTZ NOT NULL,
        status VARCHAR(20) DEFAULT 'active',
        revenuecat_entitlement_id VARCHAR(255),
        updated_at TIMESTAMPTZ DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS device_transfers (
        id VARCHAR(64) PRIMARY KEY,
        user_id VARCHAR(64) REFERENCES users(id) ON DELETE CASCADE,
        old_device_fingerprint VARCHAR(255) NOT NULL,
        new_device_fingerprint VARCHAR(255) NOT NULL,
        transferred_at TIMESTAMPTZ DEFAULT NOW()
      );
      CREATE TABLE IF NOT EXISTS promo_redemptions (
        id VARCHAR(64) PRIMARY KEY,
        device_fingerprint VARCHAR(255) NOT NULL,
        promo_code VARCHAR(50) NOT NULL,
        redeemed_at TIMESTAMPTZ DEFAULT NOW(),
        expires_at TIMESTAMPTZ NOT NULL,
        trial_days INT DEFAULT 30,
        UNIQUE(device_fingerprint, promo_code)
      );
      CREATE TABLE IF NOT EXISTS device_trials (
        device_fingerprint VARCHAR(255) PRIMARY KEY,
        first_seen_at TIMESTAMPTZ DEFAULT NOW(),
        device_secret VARCHAR(64)
      );
      CREATE TABLE IF NOT EXISTS vip_serial_redemptions (
        serial_id VARCHAR(16) PRIMARY KEY,
        device_fingerprint VARCHAR(255) NOT NULL,
        plan_days INT NOT NULL,
        redeemed_at TIMESTAMPTZ DEFAULT NOW()
      );
    `);
    pgInitialized = true;
  } finally {
    client.release();
  }
}

export function checkPromoCodeStatus(rawCode: string): { valid: boolean; expired: boolean; codeYear?: number } {
  const code = rawCode.trim().toUpperCase();
  const currentYearShort = new Date().getFullYear() % 100;

  const match = code.match(/^(\d{2})FR-NR$/);
  if (!match) {
    return { valid: false, expired: false };
  }

  const codeYear = parseInt(match[1], 10);
  if (codeYear === currentYearShort) {
    return { valid: true, expired: false, codeYear };
  }
  return { valid: false, expired: codeYear < currentYearShort, codeYear };
}

export function isPromoCode(rawCode: string): boolean {
  return checkPromoCodeStatus(rawCode).valid;
}

export const db = {
  async getUserByEmail(email: string): Promise<User | null> {
    const normalizedEmail = email.toLowerCase().trim();
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM users WHERE email = $1 LIMIT 1', [normalizedEmail]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.users.find(u => u.email === normalizedEmail) || null;
    }
  },

  async getUserById(id: string): Promise<User | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM users WHERE id = $1 LIMIT 1', [id]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.users.find(u => u.id === id) || null;
    }
  },

  async createUser(user: { id: string; email: string; password_hash: string; name: string }): Promise<User> {
    const newUser: User = {
      ...user,
      email: user.email.toLowerCase().trim(),
      created_at: new Date().toISOString(),
    };
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        'INSERT INTO users (id, email, password_hash, name, created_at) VALUES ($1, $2, $3, $4, $5)',
        [newUser.id, newUser.email, newUser.password_hash, newUser.name, newUser.created_at]
      );
    } else {
      const data = ensureLocalDb();
      data.users.push(newUser);
      saveLocalDb(data);
    }
    return newUser;
  },

  async getDeviceByUserId(userId: string): Promise<Device | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM devices WHERE user_id = $1 LIMIT 1', [userId]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.devices.find(d => d.user_id === userId) || null;
    }
  },

  async getDeviceByFingerprint(fingerprint: string): Promise<Device | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM devices WHERE device_fingerprint = $1 LIMIT 1', [fingerprint]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.devices.find(d => d.device_fingerprint === fingerprint) || null;
    }
  },

  async hasDeviceRedeemedPromo(deviceFingerprint: string, promoCode: string): Promise<boolean> {
    const normalizedCode = promoCode.trim().toUpperCase();
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query(
        'SELECT id FROM promo_redemptions WHERE device_fingerprint = $1 AND promo_code = $2 LIMIT 1',
        [deviceFingerprint, normalizedCode]
      );
      return res.rows.length > 0;
    } else {
      const data = ensureLocalDb();
      return (data.promo_redemptions || []).some(
        r => r.device_fingerprint === deviceFingerprint && r.promo_code === normalizedCode
      );
    }
  },

  async recordPromoRedemption(redemption: PromoRedemption): Promise<void> {
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        `INSERT INTO promo_redemptions (id, device_fingerprint, promo_code, redeemed_at, expires_at, trial_days)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (device_fingerprint, promo_code) DO NOTHING`,
        [
          redemption.id,
          redemption.device_fingerprint,
          redemption.promo_code,
          redemption.redeemed_at,
          redemption.expires_at,
          redemption.trial_days,
        ]
      );
    } else {
      const data = ensureLocalDb();
      if (!data.promo_redemptions) data.promo_redemptions = [];
      data.promo_redemptions.push(redemption);
      saveLocalDb(data);
    }
  },

  async getDeviceTrialAnchor(deviceFingerprint: string): Promise<DeviceTrialAnchor | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query(
        'SELECT device_fingerprint, first_seen_at, device_secret FROM device_trials WHERE device_fingerprint = $1 LIMIT 1',
        [deviceFingerprint]
      );
      if (res.rows.length === 0) return null;
      const row = res.rows[0];
      return {
        device_fingerprint: row.device_fingerprint,
        first_seen_at: new Date(row.first_seen_at).toISOString(),
        device_secret: row.device_secret,
      };
    } else {
      const data = ensureLocalDb();
      const anchor = data.device_trials?.find(t => t.device_fingerprint === deviceFingerprint);
      return anchor || null;
    }
  },

  async getOrCreateDeviceTrialAnchor(deviceFingerprint: string, clientFirstLaunchAt?: string): Promise<DeviceTrialAnchor> {
    const now = Date.now();
    const EARLIEST_POSSIBLE_TIME = new Date('2026-01-01T00:00:00Z').getTime();
    const MAX_BACKDATE_MS = PROMO_TOTAL_TRIAL_DAYS * 24 * 60 * 60 * 1000;
    const minAllowedTime = Math.max(EARLIEST_POSSIBLE_TIME, now - MAX_BACKDATE_MS);

    let candidateFirstSeen = new Date(now).toISOString();
    if (clientFirstLaunchAt) {
      const parsed = new Date(clientFirstLaunchAt).getTime();
      if (Number.isFinite(parsed) && parsed >= minAllowedTime && parsed <= now) {
        candidateFirstSeen = new Date(parsed).toISOString();
      }
    }

    if (pgPool) {
      await initPgTables();
      const existing = await pgPool.query(
        'SELECT device_fingerprint, first_seen_at, device_secret FROM device_trials WHERE device_fingerprint = $1 LIMIT 1',
        [deviceFingerprint]
      );
      if (existing.rows.length > 0) {
        const row = existing.rows[0];
        return {
          device_fingerprint: row.device_fingerprint,
          first_seen_at: new Date(row.first_seen_at).toISOString(),
          device_secret: row.device_secret,
        };
      }
      await pgPool.query(
        `INSERT INTO device_trials (device_fingerprint, first_seen_at, device_secret)
         VALUES ($1, $2, NULL)
         ON CONFLICT (device_fingerprint) DO NOTHING`,
        [deviceFingerprint, candidateFirstSeen]
      );
      const res = await pgPool.query(
        'SELECT device_fingerprint, first_seen_at, device_secret FROM device_trials WHERE device_fingerprint = $1 LIMIT 1',
        [deviceFingerprint]
      );
      const row = res.rows[0];
      return {
        device_fingerprint: row.device_fingerprint,
        first_seen_at: new Date(row.first_seen_at).toISOString(),
        device_secret: row.device_secret,
      };
    } else {
      const data = ensureLocalDb();
      if (!data.device_trials) data.device_trials = [];
      let anchor = data.device_trials.find(t => t.device_fingerprint === deviceFingerprint);
      if (!anchor) {
        anchor = { device_fingerprint: deviceFingerprint, first_seen_at: candidateFirstSeen, device_secret: null };
        data.device_trials.push(anchor);
        saveLocalDb(data);
      }
      return anchor;
    }
  },

  async isSerialClaimedByDevice(serialId: string, deviceFingerprint: string): Promise<boolean> {
    if (pgPool) {
      await initPgTables();
      const existing = await pgPool.query(
        'SELECT device_fingerprint FROM vip_serial_redemptions WHERE serial_id = $1 LIMIT 1',
        [serialId]
      );
      return existing.rows[0]?.device_fingerprint === deviceFingerprint;
    } else {
      const data = ensureLocalDb();
      const existing = data.vip_serial_redemptions?.find(r => r.serial_id === serialId);
      return existing?.device_fingerprint === deviceFingerprint;
    }
  },

  async setDeviceSecretIfAbsent(deviceFingerprint: string, newSecret: string): Promise<string | null> {
    await this.getOrCreateDeviceTrialAnchor(deviceFingerprint);
    if (pgPool) {
      await initPgTables();
      const updateRes = await pgPool.query(
        `UPDATE device_trials SET device_secret = $2 WHERE device_fingerprint = $1 AND device_secret IS NULL`,
        [deviceFingerprint, newSecret]
      );
      if ((updateRes.rowCount ?? 0) > 0) {
        return newSecret;
      }
      return null;
    } else {
      const data = ensureLocalDb();
      const anchor = data.device_trials.find(t => t.device_fingerprint === deviceFingerprint);
      if (anchor && !anchor.device_secret) {
        anchor.device_secret = newSecret;
        saveLocalDb(data);
        return newSecret;
      }
      return null;
    }
  },

  async verifyDeviceSignature(deviceFingerprint: string, timestampMs: number, signatureHex: string): Promise<boolean> {
    if (!Number.isFinite(timestampMs) || Math.abs(Date.now() - timestampMs) > 5 * 60 * 1000) {
      return false;
    }
    const anchor = pgPool
      ? await (async () => {
          await initPgTables();
          const res = await pgPool!.query('SELECT device_secret FROM device_trials WHERE device_fingerprint = $1', [deviceFingerprint]);
          return res.rows[0]?.device_secret as string | undefined;
        })()
      : ensureLocalDb().device_trials.find(t => t.device_fingerprint === deviceFingerprint)?.device_secret || undefined;

    if (!anchor) return false;

    const expected = crypto
      .createHmac('sha256', anchor)
      .update(`${deviceFingerprint}.${timestampMs}`)
      .digest('hex');

    const expectedBuf = Buffer.from(expected, 'hex');
    const givenBuf = Buffer.from((signatureHex || '').trim(), 'hex');
    if (expectedBuf.length !== givenBuf.length) return false;
    return crypto.timingSafeEqual(expectedBuf, givenBuf);
  },

  async claimVipSerial(serialId: string, deviceFingerprint: string, planDays: number): Promise<boolean> {
    if (pgPool) {
      await initPgTables();
      const inserted = await pgPool.query(
        `INSERT INTO vip_serial_redemptions (serial_id, device_fingerprint, plan_days, redeemed_at)
         VALUES ($1, $2, $3, $4)
         ON CONFLICT (serial_id) DO NOTHING`,
        [serialId, deviceFingerprint, planDays, new Date().toISOString()]
      );
      if (inserted.rowCount && inserted.rowCount > 0) return true;

      const existing = await pgPool.query(
        'SELECT device_fingerprint FROM vip_serial_redemptions WHERE serial_id = $1 LIMIT 1',
        [serialId]
      );
      return existing.rows[0]?.device_fingerprint === deviceFingerprint;
    } else {
      const data = ensureLocalDb();
      if (!data.vip_serial_redemptions) data.vip_serial_redemptions = [];
      const existing = data.vip_serial_redemptions.find(r => r.serial_id === serialId);
      if (existing) return existing.device_fingerprint === deviceFingerprint;

      data.vip_serial_redemptions.push({
        serial_id: serialId,
        device_fingerprint: deviceFingerprint,
        plan_days: planDays,
        redeemed_at: new Date().toISOString(),
      });
      saveLocalDb(data);
      return true;
    }
  },

  async activateLicenseWithCode(
    deviceFingerprint: string,
    rawCode: string,
    platform: 'ios' | 'android' = 'ios',
    deviceModel: string = 'Coach Device',
    clientFirstLaunchAt?: string
  ): Promise<{
    success: boolean;
    error?: string;
    error_code?: string;
    license?: License;
    is_promo?: boolean;
    trial_days?: number;
    device_secret?: string;
  }> {
    const code = rawCode.trim().toUpperCase();
    const promoStatus = checkPromoCodeStatus(code);

    if (promoStatus.expired) {
      const currentYearShort = String(new Date().getFullYear() % 100).padStart(2, '0');
      return {
        success: false,
        error: `推廣代碼（${code}）已超過一年有效期限。請向講師或官方索取當前年度（${currentYearShort}FR-NR）最新代碼。`,
        error_code: 'PROMO_YEAR_EXPIRED',
      };
    }

    const isPromo = promoStatus.valid;
    const vipSerialInfo = !isPromo ? verifyVipSerial(code) : null;
    const isValidVIP = !!vipSerialInfo;

    if (!isPromo && !isValidVIP) {
      return { success: false, error: '無效的授權序號或推廣代碼', error_code: 'INVALID_CODE' };
    }

    const dev = await this.getDeviceByFingerprint(deviceFingerprint);
    let userId = dev?.user_id;

    if (isPromo && userId) {
      const existingLicense = await this.getLicenseByUserId(userId);
      if (
        existingLicense &&
        existingLicense.status === 'active' &&
        new Date(existingLicense.expires_at).getTime() > Date.now() &&
        existingLicense.plan_type !== 'promo_trial_30d'
      ) {
        return {
          success: false,
          error: '此設備已有生效中的專業年繳版 VIP 授權，無需使用體驗推廣代碼。',
          error_code: 'VIP_ALREADY_ACTIVE',
        };
      }
    }

    let expiresAt: string;
    let durationDays: number;

    if (isPromo) {
      const alreadyRedeemed = await this.hasDeviceRedeemedPromo(deviceFingerprint, code);
      if (alreadyRedeemed) {
        return {
          success: false,
          error: `本設備已兌換過此年度推廣代碼（${code}），每台設備限領一次。`,
          error_code: 'PROMO_ALREADY_REDEEMED',
        };
      }

      const anchor = await this.getOrCreateDeviceTrialAnchor(deviceFingerprint, clientFirstLaunchAt);
      const anchorMs = new Date(anchor.first_seen_at).getTime();
      const promoExpiresMs = anchorMs + PROMO_TOTAL_TRIAL_DAYS * 24 * 60 * 60 * 1000;

      if (promoExpiresMs <= Date.now()) {
        return {
          success: false,
          error: `此推廣代碼體驗期限為首次啟用起算 ${PROMO_TOTAL_TRIAL_DAYS} 天。本設備首次啟用已超過 ${PROMO_TOTAL_TRIAL_DAYS} 天，無法再使用此代碼，請升級專業年繳版。`,
          error_code: 'PROMO_EXPIRED',
        };
      }

      expiresAt = new Date(promoExpiresMs).toISOString();
      durationDays = PROMO_TOTAL_TRIAL_DAYS;
    } else {
      durationDays = vipSerialInfo!.planDays;
      expiresAt = new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000).toISOString();
    }

    if (isValidVIP && vipSerialInfo) {
      const claimed = await this.claimVipSerial(
        vipSerialInfo.serialId,
        deviceFingerprint,
        vipSerialInfo.planDays
      );
      if (!claimed) {
        return {
          success: false,
          error: '此授權序號已在其他設備開通過。如需更換設備，請使用「轉移既有授權」功能。',
          error_code: 'VIP_SERIAL_ALREADY_CLAIMED',
        };
      }
    }

    if (!userId) {
      const newUser = await this.getOrCreateUserForDevice(deviceFingerprint, platform, deviceModel);
      userId = newUser.id;
    }

    if (isPromo) {
      await this.recordPromoRedemption({
        id: crypto.randomUUID(),
        device_fingerprint: deviceFingerprint,
        promo_code: code,
        redeemed_at: new Date().toISOString(),
        expires_at: expiresAt,
        trial_days: PROMO_TOTAL_TRIAL_DAYS,
      });
    }

    const license = await this.setLicense({
      id: crypto.randomUUID(),
      user_id: userId,
      plan_type: isPromo ? 'promo_trial_30d' : 'yearly',
      expires_at: expiresAt,
      status: 'active',
      revenuecat_entitlement_id: code,
    });

    let deviceSecret: string | null = null;
    const existingAnchor = await this.getDeviceTrialAnchor(deviceFingerprint);
    if (existingAnchor?.device_secret) {
      if (!isPromo) {
        deviceSecret = existingAnchor.device_secret;
      }
    } else {
      deviceSecret = await this.setDeviceSecretIfAbsent(deviceFingerprint, crypto.randomBytes(32).toString('hex'));
    }

    return {
      success: true,
      license,
      is_promo: isPromo,
      trial_days: durationDays,
      ...(deviceSecret ? { device_secret: deviceSecret } : {}),
    };
  },

  async getOrCreateUserForDevice(
    deviceFingerprint: string,
    platform: 'ios' | 'android' = 'ios',
    deviceModel: string = 'Coach Device'
  ): Promise<User> {
    const existingDevice = await this.getDeviceByFingerprint(deviceFingerprint);
    if (existingDevice) {
      const existingUser = await this.getUserById(existingDevice.user_id);
      if (existingUser) {
        return existingUser;
      }
    }

    const userId = crypto.randomUUID();
    const newUser = await this.createUser({
      id: userId,
      email: `coach_${deviceFingerprint.slice(0, 8)}@fitnessrider.local`,
      password_hash: 'local_license_auth',
      name: '飛輪教練',
    });
    await this.bindDevice({
      id: crypto.randomUUID(),
      user_id: userId,
      device_fingerprint: deviceFingerprint,
      platform,
      device_model: deviceModel,
    });
    return newUser;
  },

  async bindDevice(device: {
    id: string;
    user_id: string;
    device_fingerprint: string;
    platform: 'ios' | 'android';
    device_model: string;
  }): Promise<Device> {
    const newDevice: Device = {
      ...device,
      bound_at: new Date().toISOString(),
      last_active_at: new Date().toISOString(),
    };
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        `INSERT INTO devices (id, user_id, device_fingerprint, platform, device_model, bound_at, last_active_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT (user_id) DO UPDATE SET
           device_fingerprint = EXCLUDED.device_fingerprint,
           platform = EXCLUDED.platform,
           device_model = EXCLUDED.device_model,
           last_active_at = EXCLUDED.last_active_at`,
        [
          newDevice.id,
          newDevice.user_id,
          newDevice.device_fingerprint,
          newDevice.platform,
          newDevice.device_model,
          newDevice.bound_at,
          newDevice.last_active_at,
        ]
      );
    } else {
      const data = ensureLocalDb();
      const index = data.devices.findIndex(d => d.user_id === device.user_id);
      if (index >= 0) {
        data.devices[index] = newDevice;
      } else {
        data.devices.push(newDevice);
      }
      saveLocalDb(data);
    }
    return newDevice;
  },

  async updateDeviceLastActive(userId: string): Promise<void> {
    const now = new Date().toISOString();
    if (pgPool) {
      await initPgTables();
      await pgPool.query('UPDATE devices SET last_active_at = $1 WHERE user_id = $2', [now, userId]);
    } else {
      const data = ensureLocalDb();
      const dev = data.devices.find(d => d.user_id === userId);
      if (dev) {
        dev.last_active_at = now;
        saveLocalDb(data);
      }
    }
  },

  async getLicenseByUserId(userId: string): Promise<License | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM licenses WHERE user_id = $1 LIMIT 1', [userId]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.licenses.find(l => l.user_id === userId) || null;
    }
  },

  async getLicenseByCode(code: string): Promise<License | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query('SELECT * FROM licenses WHERE revenuecat_entitlement_id = $1 LIMIT 1', [code]);
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      return data.licenses.find(l => l.revenuecat_entitlement_id === code) || null;
    }
  },

  async setLicense(license: {
    id: string;
    user_id: string;
    plan_type: License['plan_type'];
    expires_at: string;
    status: 'active' | 'expired' | 'canceled';
    revenuecat_entitlement_id?: string;
  }): Promise<License> {
    const now = new Date().toISOString();
    const newLicense: License = {
      ...license,
      updated_at: now,
    };
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        `INSERT INTO licenses (id, user_id, plan_type, expires_at, status, revenuecat_entitlement_id, updated_at)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         ON CONFLICT (user_id) DO UPDATE SET
           plan_type = EXCLUDED.plan_type,
           expires_at = EXCLUDED.expires_at,
           status = EXCLUDED.status,
           revenuecat_entitlement_id = EXCLUDED.revenuecat_entitlement_id,
           updated_at = EXCLUDED.updated_at`,
        [
          newLicense.id,
          newLicense.user_id,
          newLicense.plan_type,
          newLicense.expires_at,
          newLicense.status,
          newLicense.revenuecat_entitlement_id || null,
          newLicense.updated_at,
        ]
      );
    } else {
      const data = ensureLocalDb();
      const index = data.licenses.findIndex(l => l.user_id === license.user_id);
      if (index >= 0) {
        data.licenses[index] = newLicense;
      } else {
        data.licenses.push(newLicense);
      }
      saveLocalDb(data);
    }
    return newLicense;
  },

  async getLastTransfer(userId: string): Promise<DeviceTransferLog | null> {
    if (pgPool) {
      await initPgTables();
      const res = await pgPool.query(
        'SELECT * FROM device_transfers WHERE user_id = $1 ORDER BY transferred_at DESC LIMIT 1',
        [userId]
      );
      return res.rows[0] || null;
    } else {
      const data = ensureLocalDb();
      const logs = data.device_transfers
        .filter(l => l.user_id === userId)
        .sort((a, b) => new Date(b.transferred_at).getTime() - new Date(a.transferred_at).getTime());
      return logs[0] || null;
    }
  },

  async recordDeviceTransfer(log: {
    id: string;
    user_id: string;
    old_device_fingerprint: string;
    new_device_fingerprint: string;
  }): Promise<DeviceTransferLog> {
    const newLog: DeviceTransferLog = {
      ...log,
      transferred_at: new Date().toISOString(),
    };
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        'INSERT INTO device_transfers (id, user_id, old_device_fingerprint, new_device_fingerprint, transferred_at) VALUES ($1, $2, $3, $4, $5)',
        [newLog.id, newLog.user_id, newLog.old_device_fingerprint, newLog.new_device_fingerprint, newLog.transferred_at]
      );
    } else {
      const data = ensureLocalDb();
      data.device_transfers.push(newLog);
      saveLocalDb(data);
    }
    return newLog;
  },
};
