/**
 * 商業模式權威數字。
 *
 * - 基礎試用：裝置首次啟動起 7 天（BASE_TRIAL_DAYS）。
 * - 推廣代碼：輸入後把試用「延長一次到總共 30 天」（PROMO_TOTAL_TRIAL_DAYS），
 *   不是在剩餘天數上再加 30 天 —— 見 backend/src/lib/db.ts 的
 *   activateLicenseWithCode()：到期時間 = 裝置試用起算時間 + PROMO_TOTAL_TRIAL_DAYS。
 * - 之後必須付費購買 VIP（見 vipSerial.ts 的 P-256 序號機制）。
 *
 * 這三個數字同時存在於：
 *   - 本檔案（backend）
 *   - promo.properties 的 BASE_TRIAL_DAYS / TRIAL_DAYS（文件性權威來源，Android 直接讀取）
 *   - android/app/build.gradle.kts（實際從 promo.properties 讀值注入 BuildConfig，機械化單一來源）
 *   - ios/FitnessRider/App/VersionLifecycleManager.swift 的常數（Xcode 沒有現成的建置期讀檔機制，
 *     且專案禁止為此新增建置腳本工具鏈，因此用硬編常數 + 測試互相校驗取代）
 * 三邊數字一致性由 tools/check_promo_code.sh 與各平台測試檔案的
 * 「跨檔案數字一致性」測試把關，修改任一處都務必同步其餘四處。
 */
export const BASE_TRIAL_DAYS = 7;
export const PROMO_TOTAL_TRIAL_DAYS = 30;

/** 付費 VIP 序號若簽章中未帶天數時的保底方案天數（目前簽發工具一律會帶明確天數，此值僅供防呆）。 */
export const VIP_FALLBACK_PLAN_DAYS = 365;

/**
 * 強制更新門檻（App 版本碼）。0 = 永不強制更新。
 * 只有「有新版可拿」時才推升這個數字，不是建置日期到期。見 spec 項目 F。
 * 用環境變數設定，預設 0（不強制），避免忘記設定值時誤鎖所有使用者。
 */
export const MIN_SUPPORTED_VERSION_CODE = {
  android: parseInt(process.env.MIN_SUPPORTED_VERSION_CODE_ANDROID || '0', 10) || 0,
  ios: parseInt(process.env.MIN_SUPPORTED_VERSION_CODE_IOS || '0', 10) || 0,
} as const;

export function minSupportedVersionCodeFor(platform: string | null | undefined): number {
  return platform === 'ios' ? MIN_SUPPORTED_VERSION_CODE.ios : MIN_SUPPORTED_VERSION_CODE.android;
}
