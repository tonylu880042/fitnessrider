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

/**
 * 每台裝置的試用起算錨點（不需要帳號、也不需要已開通任何授權就會建立）。
 * 存在的目的是讓 Android 重灌後仍能以 ANDROID_ID 對回同一筆紀錄，試用不會被重置
 * （見 spec 項目 D）。`device_secret` 只在該裝置第一次成功開通授權/推廣代碼時才會產生，
 * 用來簽章後續的 /api/license/verify 請求（見 spec 項目 E），純試用中、尚未開通過
 * 任何東西的裝置沒有這組密鑰，也因此沒有需要保護的授權狀態可以被查詢。
 */
export interface DeviceTrialAnchor {
  device_fingerprint: string;
  first_seen_at: string;
  device_secret: string | null;
}

/** 付費 VIP 序號（P-256 簽章）兌換紀錄，一組序號只能在一台裝置上開通一次。 */
export interface VipSerialRedemption {
  serial_id: string;
  device_fingerprint: string;
  plan_days: number;
  redeemed_at: string;
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
  error_code?: 'INVALID_CREDENTIALS' | 'DEVICE_MISMATCH' | 'EMAIL_EXISTS' | 'DEVICE_ALREADY_BOUND' | 'LICENSE_EXPIRED' | 'TRANSFER_COOLDOWN' | 'SERVER_ERROR';
  current_bound_device?: {
    device_model: string;
    bound_at: string;
  };
}
