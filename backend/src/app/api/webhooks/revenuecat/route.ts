import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';
import type { User } from '@/lib/types';

async function resolveUserForAppUserId(appUserId: string): Promise<User> {
  const device = await db.getDeviceByFingerprint(appUserId);
  if (device) {
    const user = await db.getUserById(device.user_id);
    if (user) {
      return user;
    }
  }

  let user = await db.getUserById(appUserId);
  if (!user && appUserId.includes('@')) {
    user = await db.getUserByEmail(appUserId);
  }
  if (user) {
    return user;
  }

  return db.getOrCreateUserForDevice(appUserId);
}

export async function POST(req: NextRequest) {
  try {
    const authHeader = req.headers.get('authorization');
    const expectedSecret = process.env.REVENUECAT_WEBHOOK_SECRET;
    if (!expectedSecret) {
      console.error('[RevenueCat Webhook] REVENUECAT_WEBHOOK_SECRET is not configured; rejecting.');
      return NextResponse.json({ error: 'Webhook not configured' }, { status: 503 });
    }
    const expected = Buffer.from(`Bearer ${expectedSecret}`);
    const given = Buffer.from(authHeader ?? '');
    if (expected.length !== given.length || !crypto.timingSafeEqual(expected, given)) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const body = await req.json();
    const event = body.event;
    if (!event) {
      return NextResponse.json({ error: 'No event payload' }, { status: 400 });
    }

    const { type, app_user_id, product_id, expiration_at_ms, entitlement_id } = event;
    console.log(`[RevenueCat Webhook] Type: ${type}, AppUserID: ${app_user_id}, Product: ${product_id}`);

    if (typeof app_user_id !== 'string' || app_user_id.trim() === '') {
      return NextResponse.json({ error: 'Missing app_user_id' }, { status: 400 });
    }

    const user = await resolveUserForAppUserId(app_user_id);

    let planType: 'monthly' | 'quarterly' | 'yearly' = 'yearly';
    if (product_id?.toLowerCase().includes('month')) {
      planType = 'monthly';
    } else if (product_id?.toLowerCase().includes('quarter')) {
      planType = 'quarterly';
    } else if (product_id?.toLowerCase().includes('year')) {
      planType = 'yearly';
    }

    let expiresAt: string;
    if (expiration_at_ms) {
      expiresAt = new Date(expiration_at_ms).toISOString();
    } else {
      const days = planType === 'monthly' ? 30 : planType === 'quarterly' ? 90 : 365;
      expiresAt = new Date(Date.now() + days * 24 * 60 * 60 * 1000).toISOString();
    }

    if (type === 'INITIAL_PURCHASE' || type === 'RENEWAL' || type === 'PRODUCT_CHANGE') {
      await db.setLicense({
        id: crypto.randomUUID(),
        user_id: user.id,
        plan_type: planType,
        expires_at: expiresAt,
        status: 'active',
        revenuecat_entitlement_id: entitlement_id || 'pro_access',
      });
      console.log(`[RevenueCat Webhook] License updated for ${user.email} -> ${expiresAt}`);
    } else if (type === 'EXPIRATION' || type === 'CANCELLATION') {
      await db.setLicense({
        id: crypto.randomUUID(),
        user_id: user.id,
        plan_type: planType,
        expires_at: expiresAt,
        status: 'expired',
        revenuecat_entitlement_id: entitlement_id,
      });
      console.log(`[RevenueCat Webhook] License expired for ${user.email}`);
    }

    return NextResponse.json({ success: true, message: 'Webhook processed' });
  } catch (error) {
    console.error('RevenueCat Webhook Error:', error);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
