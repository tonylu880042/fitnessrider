import { NextRequest, NextResponse } from 'next/server';
import crypto from 'crypto';
import { db } from '@/lib/db';

export async function POST(req: NextRequest) {
  try {
    const authHeader = req.headers.get('authorization');
    const expectedSecret = process.env.REVENUECAT_WEBHOOK_SECRET;
    if (expectedSecret && authHeader !== `Bearer ${expectedSecret}`) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const body = await req.json();
    const event = body.event;
    if (!event) {
      return NextResponse.json({ error: 'No event payload' }, { status: 400 });
    }

    const { type, app_user_id, product_id, expiration_at_ms, entitlement_id } = event;
    console.log(`[RevenueCat Webhook] Type: ${type}, AppUserID: ${app_user_id}, Product: ${product_id}`);

    let user = await db.getUserById(app_user_id);
    if (!user && app_user_id.includes('@')) {
      user = await db.getUserByEmail(app_user_id);
    }

    if (!user) {
      console.warn(`[RevenueCat Webhook] User not found for app_user_id: ${app_user_id}`);
      return NextResponse.json({ message: 'User not found, acknowledged' }, { status: 200 });
    }

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
