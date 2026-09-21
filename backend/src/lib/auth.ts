import bcrypt from 'bcryptjs';
import * as jose from 'jose';

const JWT_SECRET = process.env.JWT_SECRET || 'fitnessrider-secret-key-2026-super-secure';
const key = new TextEncoder().encode(JWT_SECRET);

export async function hashPassword(password: string): Promise<string> {
  return bcrypt.hash(password, 10);
}

export async function comparePassword(password: string, hash: string): Promise<boolean> {
  return bcrypt.compare(password, hash);
}

export async function signJwt(payload: {
  userId: string;
  email: string;
  deviceFingerprint: string;
}): Promise<string> {
  return new jose.SignJWT(payload)
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime('30d')
    .sign(key);
}

export async function verifyJwt(token: string): Promise<{
  userId: string;
  email: string;
  deviceFingerprint: string;
} | null> {
  try {
    const { payload } = await jose.jwtVerify(token, key);
    return {
      userId: payload.userId as string,
      email: payload.email as string,
      deviceFingerprint: payload.deviceFingerprint as string,
    };
  } catch {
    return null;
  }
}
