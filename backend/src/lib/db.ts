import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { Pool } from 'pg';
import { User, Device, License, DeviceTransferLog, PromoRedemption, DeviceTrialAnchor, VipSerialRedemption } from './types';
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

// Local file storage fallback
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

// Initialize tables if PostgreSQL is connected
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

/**
 * Check if a code matches the yearly promotion format (e.g. 26FR-NR for 2026).
 *
 * Only the code for the CURRENT year is valid. A past year's code is reported as
 * `expired` (clear "please ask for this year's code" messaging). A future year's
 * code (e.g. entering `99FR-NR` today) is format-valid but not yet active, and is
 * treated as invalid rather than "not yet expired" — otherwise it would never expire
 * and would grant an unlimited-lifetime promo trial (see spec item G / fixed bug).
 */
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
  // Past year -> expired (surfaced with a dedicated message). Future year -> just invalid.
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

  /**
   * 純查詢該裝置的試用起算錨點，查無紀錄時回傳 null，絕不自動新增紀錄（防止未經授權請求隨意建錨點，spec 項目 D / 項目 10）。
   */
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

  /**
   * 取得（或在合法開通/認證時建立）該裝置的試用起算錨點。
   * clientFirstLaunchAt 有合法時間區間限制（不得早於 2026 年且不得早於現在往前推 35 天，防止篡改回 1970 年，spec 項目 5）。
   * 若伺服器已有既有紀錄，未經認證的請求絕不允許將既有錨點往前推移。
   */
  async getOrCreateDeviceTrialAnchor(deviceFingerprint: string, clientFirstLaunchAt?: string): Promise<DeviceTrialAnchor> {
    const now = Date.now();
    const EARLIEST_POSSIBLE_TIME = new Date('2026-01-01T00:00:00Z').getTime();
    // 試用與推廣代碼至多允許往前推算 35 天（30 天試用上限 + 5 天緩衝），絕不允許無限回溯至 1970 年
    const MAX_BACKDATE_MS = 35 * 24 * 60 * 60 * 1000;
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
        // 伺服器已有錨點紀錄時，未認證請求不可任意推移既有時間
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
      // Re-read in case of a concurrent insert race.
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

  /**
   * 強制設定或刷新該裝置的密鑰（僅在以合法 VIP 序號成功完成身分認證/重灌開通時呼叫）。
   */
  async setOrRotateDeviceSecret(deviceFingerprint: string, newSecret: string): Promise<string> {
    await this.getOrCreateDeviceTrialAnchor(deviceFingerprint);
    if (pgPool) {
      await initPgTables();
      await pgPool.query(
        `UPDATE device_trials SET device_secret = $2 WHERE device_fingerprint = $1`,
        [deviceFingerprint, newSecret]
      );
    } else {
      const data = ensureLocalDb();
      const anchor = data.device_trials?.find(t => t.device_fingerprint === deviceFingerprint);
      if (anchor) {
        anchor.device_secret = newSecret;
        saveLocalDb(data);
      }
    }
    return newSecret;
  },

  /**
   * 只在該裝置尚未有密鑰時才設定（第一次成功開通授權/推廣代碼時呼叫）。
   * 成功設定新密鑰時回傳該密鑰；若該裝置已存在密鑰，回傳 null（絕不回傳既有密鑰，避免成為查詢 oracle，spec 項目 1）。
   */
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

  /**
   * 驗證 /api/license/verify 的裝置簽章（HMAC-SHA256(device_secret, "<fingerprint>.<timestamp>")）。
   * 只有先前透過 /api/license/activate 成功開通過授權（含推廣代碼）的裝置才會有 device_secret，
   * 純試用、尚未開通任何東西的裝置沒有密鑰、也沒有付費授權狀態可以被查詢（見 spec 項目 E）。
   */
  async verifyDeviceSignature(deviceFingerprint: string, timestampMs: number, signatureHex: string): Promise<boolean> {
    if (!Number.isFinite(timestampMs) || Math.abs(Date.now() - timestampMs) > 5 * 60 * 1000) {
      return false; // 逾時 5 分鐘的請求一律拒絕，避免重放
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

  /**
   * 認領一組付費 VIP 序號：同一組序號只能綁在一台裝置上。
   *
   * 刻意讓「INSERT 本身」當閘門，而不是先查再寫 —— 先查再寫有 TOCTOU：同一組外流的序號
   * 同時在兩台裝置上開通時，兩邊都會讀到「還沒人用過」而各自拿到授權，後寫的那筆
   * `ON CONFLICT DO NOTHING` 只會靜靜變成 no-op，錯誤完全不會被發現。改由資料庫的
   * PRIMARY KEY 仲裁之後，同時只有一方插得進去。
   *
   * 插不進去時回讀既有紀錄，只有原本就是同一台裝置才放行 —— 讓同一台裝置重裝後重新輸入
   * 同一組序號仍然可以成功。回傳 true 代表這台裝置可以用這組序號開通。
   */
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
      // 記憶體／檔案後備：Node 是單執行緒，find 到 push 之間沒有任何 await，
      // 這一段本身就是不可分割的，不需要額外的鎖。
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
      };
    }

    const isPromo = promoStatus.valid;
    // 付費 VIP 序號一律用 P-256 簽章驗證，不再有任何寫死序號或前綴長度規則（spec 項目 B）。
    const vipSerialInfo = !isPromo ? verifyVipSerial(code) : null;
    const isValidVIP = !!vipSerialInfo;

    if (!isPromo && !isValidVIP) {
      return { success: false, error: '無效的授權序號或推廣代碼' };
    }

    let dev = await this.getDeviceByFingerprint(deviceFingerprint);
    let userId = dev?.user_id;

    // 若設備已有生效中的付費 VIP，不可被推廣代碼覆蓋
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
        };
      }
    }

    // Single-device anti-abuse for promotional codes: each device can only redeem once per year
    let expiresAt: string;
    let durationDays: number;

    // Single-device anti-abuse for promotional codes: each device can only redeem once per year
    if (isPromo) {
      const alreadyRedeemed = await this.hasDeviceRedeemedPromo(deviceFingerprint, code);
      if (alreadyRedeemed) {
        return {
          success: false,
          error: `本設備已兌換過此年度推廣代碼（${code}），每台設備限領一次。`,
        };
      }

      // 「延長一次到總共 30 天」，不是在剩餘天數上再加 30 天：以裝置的試用起算錨點
      // （結合本機回報起算時間與伺服器記錄取較早者）+ PROMO_TOTAL_TRIAL_DAYS 為到期時間。
      const anchor = await this.getOrCreateDeviceTrialAnchor(deviceFingerprint, clientFirstLaunchAt);
      const anchorMs = new Date(anchor.first_seen_at).getTime();
      const promoExpiresMs = anchorMs + PROMO_TOTAL_TRIAL_DAYS * 24 * 60 * 60 * 1000;

      // 若首次啟動已超過 30 天，推廣體驗期已過，不扣抵次數、明確回報錯誤（spec 項目 3）。
      // 刻意放在建立帳號與設備綁定之前，避免留下孤兒資料（spec 項目 4）。
      if (promoExpiresMs <= Date.now()) {
        return {
          success: false,
          error: `此推廣代碼體驗期限為首次啟用起算 ${PROMO_TOTAL_TRIAL_DAYS} 天。本設備首次啟用已超過 30 天，無法再使用此代碼，請升級專業年繳版。`,
        };
      }

      expiresAt = new Date(promoExpiresMs).toISOString();
      durationDays = PROMO_TOTAL_TRIAL_DAYS;
    } else {
      durationDays = vipSerialInfo!.planDays;
      expiresAt = new Date(Date.now() + durationDays * 24 * 60 * 60 * 1000).toISOString();
    }

    // 付費序號限制：同一組序號只能在一台裝置上開通過一次，防止序號外流後被無限次開通。
    // 認領動作刻意放在建立帳號／裝置／授權之前 —— 認領失敗就直接結束，不會留下半套資料。
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
        };
      }
    }

    if (!userId) {
      userId = crypto.randomUUID();
      await this.createUser({
        id: userId,
        email: `coach_${deviceFingerprint.slice(0, 8)}@fitnessrider.local`,
        password_hash: 'local_license_auth',
        name: '飛輪教練',
      });
      dev = await this.bindDevice({
        id: crypto.randomUUID(),
        user_id: userId,
        device_fingerprint: deviceFingerprint,
        platform: platform,
        device_model: deviceModel,
      });
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

    // 開通成功即建立裝置密鑰，之後 /api/license/verify 用它簽章請求。
    // 付費 VIP：出示合法 ECDSA 簽署序號已完成身分證明，直接簽發/刷新密鑰，確保重灌設備可復原密鑰（spec 項目 2）。
    // 公開推廣代碼：若裝置早已存在密鑰則回傳 null，絕不對外洩漏既有密鑰（spec 項目 1）。
    let deviceSecret: string | null = null;
    if (!isPromo) {
      deviceSecret = await this.setOrRotateDeviceSecret(deviceFingerprint, crypto.randomBytes(32).toString('hex'));
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
