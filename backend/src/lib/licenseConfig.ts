export const BASE_TRIAL_DAYS = 7;
export const PROMO_TOTAL_TRIAL_DAYS = 30;

export const VIP_FALLBACK_PLAN_DAYS = 365;

export const MIN_SUPPORTED_VERSION_CODE = {
  android: parseInt(process.env.MIN_SUPPORTED_VERSION_CODE_ANDROID || '0', 10) || 0,
  ios: parseInt(process.env.MIN_SUPPORTED_VERSION_CODE_IOS || '0', 10) || 0,
} as const;

export function minSupportedVersionCodeFor(platform: string | null | undefined): number {
  return platform === 'ios' ? MIN_SUPPORTED_VERSION_CODE.ios : MIN_SUPPORTED_VERSION_CODE.android;
}
