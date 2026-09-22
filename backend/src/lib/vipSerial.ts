import crypto from 'crypto';

/**
 * 付費 VIP 序號：ECDSA P-256 (secp256r1 / prime256v1) 簽章序號，取代原本可偽造的
 * `code.startsWith("RIDER-VIP-") && code.length >= 14` 規則與寫死序號
 * (`RIDER-VIP-2026-PASS`、`FITNESS-PRO-ANNUAL-KEY`)。
 *
 * 選 P-256 而非 Ed25519：Android minSdk 26，平台原生 `java.security.Signature`
 * 對 Ed25519 要 API 33+ 才支援；P-256 三邊都原生支援
 * （Android `Signature("SHA256withECDSA")`、iOS CryptoKit `P256.Signing`、
 * Node 內建 `crypto`），不需要新增任何依賴。
 *
 * 序號格式：`FRVIP-<payload hex>-<signature hex>`
 *   payload（7 bytes，全部大寫 hex 顯示）：
 *     byte 0      version（目前固定為 1）
 *     byte 1-4    serialId（4 bytes 隨機亂數，同時是後端序號兌換次數表的主鍵）
 *     byte 5-6    planDays（uint16 big-endian，此序號開通的天數）
 *   signature：對 payload 的 ECDSA-P256-SHA256 簽章，DER 編碼
 *     （Node `crypto.sign`/`verify` 預設輸出/驗證 DER，Android `Signature` 與
 *     iOS `P256.Signing.ECDSASignature(derRepresentation:)` 也都直接吃 DER，
 *     三邊完全不用做格式轉換）。
 *
 * 私鑰只存在簽發序號的工具執行環境（`tools/generate_vip_serial.js` 讀取的
 * `VIP_SERIAL_PRIVATE_KEY_PEM` 環境變數），後端伺服器本身只需要「公鑰」即可完成驗簽，
 * 完全不需要載入私鑰 —— 攻擊面比把私鑰放進執行中的伺服器行程更小。
 * 公鑰不是機密，直接寫死在後端與雙平台 App 原始碼中（App 內只放公鑰、驗過才開通）。
 */

const SERIAL_PREFIX = 'FRVIP-';
const PAYLOAD_BYTES = 7;

/**
 * P-256 公鑰（SubjectPublicKeyInfo, DER, Base64）。可用環境變數覆蓋以便金鑰輪替，
 * 未設定時使用內建的預設金鑰（私鑰請見交付說明，僅存放於後端環境變數，未進 repo）。
 */
export const VIP_SERIAL_PUBLIC_KEY_SPKI_B64 =
  process.env.VIP_SERIAL_PUBLIC_KEY_SPKI_B64 ||
  'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjwz6HDGkpRRv8HNT+3HqVORKB6ByIDcHXG5StD9sr0fc2fmZs6R19dObhW2U75iKdEILOefyPHF/E7yY1mVC1g==';

export interface VipSerialInfo {
  serialId: string; // 8 位大寫 hex（4 bytes）
  planDays: number;
}

function toPublicKeyObject(spkiB64: string): crypto.KeyObject {
  return crypto.createPublicKey({
    key: Buffer.from(spkiB64, 'base64'),
    format: 'der',
    type: 'spki',
  });
}

export function buildVipSerialPayload(serialIdHex: string, planDays: number): Buffer {
  if (!/^[0-9a-fA-F]{8}$/.test(serialIdHex)) {
    throw new Error('serialId 必須是 4 bytes（8 個 hex 字元）');
  }
  if (!Number.isInteger(planDays) || planDays <= 0 || planDays > 0xffff) {
    throw new Error('planDays 必須是 1..65535 的整數');
  }
  const buf = Buffer.alloc(PAYLOAD_BYTES);
  buf.writeUInt8(1, 0); // version
  Buffer.from(serialIdHex, 'hex').copy(buf, 1);
  buf.writeUInt16BE(planDays, 5);
  return buf;
}

export function encodeVipSerial(payload: Buffer, signatureDer: Buffer): string {
  return `${SERIAL_PREFIX}${payload.toString('hex').toUpperCase()}-${signatureDer.toString('hex').toUpperCase()}`;
}

function parseVipSerial(raw: string): { payload: Buffer; signature: Buffer } | null {
  const code = raw.trim().toUpperCase();
  if (!code.startsWith(SERIAL_PREFIX)) return null;
  const rest = code.slice(SERIAL_PREFIX.length);
  const dashIndex = rest.indexOf('-');
  if (dashIndex <= 0) return null;
  const payloadHex = rest.slice(0, dashIndex);
  const sigHex = rest.slice(dashIndex + 1);

  if (payloadHex.length !== PAYLOAD_BYTES * 2 || !/^[0-9A-F]+$/.test(payloadHex)) return null;
  if (sigHex.length < 16 || sigHex.length % 2 !== 0 || !/^[0-9A-F]+$/.test(sigHex)) return null;

  try {
    return { payload: Buffer.from(payloadHex, 'hex'), signature: Buffer.from(sigHex, 'hex') };
  } catch {
    return null;
  }
}

/** 是否「長得像」一組付費 VIP 序號（格式相符），不代表簽章有效。 */
export function looksLikeVipSerial(raw: string): boolean {
  return raw.trim().toUpperCase().startsWith(SERIAL_PREFIX);
}

/**
 * 驗證付費 VIP 序號簽章，回傳解出的序號 ID 與天數；簽章或格式錯誤一律回傳 null。
 * 這是唯一判斷「這組序號合不合法」的地方 —— 不再有任何寫死序號或前綴長度規則。
 */
export function verifyVipSerial(
  raw: string,
  publicKeySpkiB64: string = VIP_SERIAL_PUBLIC_KEY_SPKI_B64
): VipSerialInfo | null {
  const parsed = parseVipSerial(raw);
  if (!parsed) return null;

  try {
    const publicKey = toPublicKeyObject(publicKeySpkiB64);
    const ok = crypto.verify('sha256', parsed.payload, publicKey, parsed.signature);
    if (!ok) return null;

    const version = parsed.payload.readUInt8(0);
    if (version !== 1) return null;

    const serialId = parsed.payload.subarray(1, 5).toString('hex').toUpperCase();
    const planDays = parsed.payload.readUInt16BE(5);
    if (planDays <= 0) return null;

    return { serialId, planDays };
  } catch {
    return null;
  }
}
