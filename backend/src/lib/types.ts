export interface User {
  id: string;
  email: string;
  password_hash: string;
  name: string;
  created_at: string;
}

export interface Device {
  id: string;
  user_id: string;
  device_fingerprint: string;
  platform: 'ios' | 'android';
  device_model: string;
  bound_at: string;
  last_active_at: string;
}

export interface License {
  id: string;
  user_id: string;
  plan_type: 'trial' | 'monthly' | 'quarterly' | 'yearly' | 'promo_trial_30d';
  expires_at: string;
  status: 'active' | 'expired' | 'canceled';
  revenuecat_entitlement_id?: string;
  updated_at: string;
}

export interface PromoRedemption {
  id: string;
  device_fingerprint: string;
  promo_code: string;
  redeemed_at: string;
  expires_at: string;
  trial_days: number;
}

export interface DeviceTransferLog {
  id: string;
  user_id: string;
  old_device_fingerprint: string;
  new_device_fingerprint: string;
  transferred_at: string;
}

export interface AuthResponse {
  success: boolean;
  token?: string;
  user?: {
    id: string;
    email: string;
    name: string;
  };
  device?: {
    device_fingerprint: string;
    device_model: string;
    bound_at: string;
  };
  license?: {
    plan_type: string;
    expires_at: string;
    status: string;
    days_remaining: number;
    is_valid: boolean;
  };
  error?: string;
  error_code?: 'INVALID_CREDENTIALS' | 'DEVICE_MISMATCH' | 'EMAIL_EXISTS' | 'LICENSE_EXPIRED' | 'TRANSFER_COOLDOWN' | 'SERVER_ERROR';
  current_bound_device?: {
    device_model: string;
    bound_at: string;
  };
}
