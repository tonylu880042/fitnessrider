import crypto from 'crypto';

const SERIAL_PREFIX = 'FRVIP-';
const PAYLOAD_BYTES = 7;

export const VIP_SERIAL_PUBLIC_KEY_SPKI_B64 =
  process.env.VIP_SERIAL_PUBLIC_KEY_SPKI_B64 ||
  'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEjwz6HDGkpRRv8HNT+3HqVORKB6ByIDcHXG5StD9sr0fc2fmZs6R19dObhW2U75iKdEILOefyPHF/E7yY1mVC1g==';

export interface VipSerialInfo {
  serialId: string;
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
  buf.writeUInt8(1, 0);
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

export function looksLikeVipSerial(raw: string): boolean {
  return raw.trim().toUpperCase().startsWith(SERIAL_PREFIX);
}

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
