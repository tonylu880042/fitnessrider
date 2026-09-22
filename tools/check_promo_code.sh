#!/usr/bin/env bash
# ==============================================================================
# FitnessRider - 年度推廣代碼有效性與過期提醒檢測腳本
# 用途: 檢測 promo.properties 中的年度代碼是否超過 1 年或即將過期，並提醒更新。
# ==============================================================================

set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROMO_PROPS="${PROJECT_DIR}/promo.properties"

if [ ! -f "$PROMO_PROPS" ]; then
    echo "⚠️  [提醒] 找不到 ${PROMO_PROPS}，請確認專案設定檔是否存在！" >&2
    exit 0
fi

# 讀取設定值
PROMO_YEAR=$(grep -E "^PROMO_YEAR=" "$PROMO_PROPS" | cut -d'=' -f2 | tr -d ' "\r\n')
PROMO_CODE=$(grep -E "^PROMO_CODE=" "$PROMO_PROPS" | cut -d'=' -f2 | tr -d ' "\r\n')
PROMO_YEAR="${OVERRIDE_PROMO_YEAR:-${PROMO_YEAR:-2026}}"
PROMO_CODE="${OVERRIDE_PROMO_CODE:-${PROMO_CODE:-26FR-NR}}"

CURRENT_YEAR="${OVERRIDE_CURRENT_YEAR:-$(date +%Y)}"
CURRENT_MONTH="${OVERRIDE_CURRENT_MONTH:-$(date +%m)}"
CURRENT_DAY="${OVERRIDE_CURRENT_DAY:-$(date +%d)}"

# 計算預期的新年度代碼
NEXT_YEAR=$((CURRENT_YEAR + 1))
CURRENT_YEAR_SHORT=$(printf "%02d" $((CURRENT_YEAR % 100)))
NEXT_YEAR_SHORT=$(printf "%02d" $((NEXT_YEAR % 100)))
EXPECTED_CURRENT_CODE="${CURRENT_YEAR_SHORT}FR-NR"
EXPECTED_NEXT_CODE="${NEXT_YEAR_SHORT}FR-NR"

echo "================================================================="
echo "  FitnessRider 年度推廣代碼檢查 (Promo Code Year Audit)"
echo "================================================================="
echo "目前系統年份 : ${CURRENT_YEAR} 年 (${CURRENT_MONTH} 月 ${CURRENT_DAY} 日)"
echo "設定年度代碼 : ${PROMO_CODE} (設定年份: ${PROMO_YEAR} 年)"
echo "================================================================="

# 檢查情況 1: 設定年份小於當前年份 (已超過一年，嚴重過期)
if [ "$PROMO_YEAR" -lt "$CURRENT_YEAR" ]; then
    echo ""
    echo "🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨"
    echo "【重大警告】推廣代碼已過期超過一年！"
    echo "目前為 ${CURRENT_YEAR} 年，但專案設定仍為 ${PROMO_YEAR} 年度代碼 [${PROMO_CODE}]！"
    echo "建議立即更新為當前年度專屬代碼: [${EXPECTED_CURRENT_CODE}]"
    echo ""
    echo "請依循 docs/PROMO_CODES.md 執行年度代碼更新步驟，同步更新以下檔案："
    echo "  1. promo.properties (更新 PROMO_YEAR=${CURRENT_YEAR}, PROMO_CODE=${EXPECTED_CURRENT_CODE})"
    echo "  2. backend/src/lib/db.ts"
    echo "  3. backend 官網文案 (Hero.tsx, Pricing.tsx, DownloadSection.tsx, Faq.tsx, terms/page.tsx)"
    echo "  4. ios/FitnessRider/App/VersionLifecycleManager.swift 與 UI 提示"
    echo "  5. android/app/src/main/java/.../VersionLifecycleManager.kt 與 UI 提示"
    echo "🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨🚨"
    echo ""
    
    if [ "$1" == "--strict" ] || [ "$1" == "--fail-on-expired" ]; then
        exit 1
    fi

# 檢查情況 2: 當前為該年度最後一個月 (12 月)，提醒即將交接
elif [ "$PROMO_YEAR" -eq "$CURRENT_YEAR" ] && [ "$CURRENT_MONTH" -eq "12" ]; then
    echo ""
    echo "⚠️  【即將交更提醒】"
    echo "當前年度 (${CURRENT_YEAR}) 僅剩最後一個月，推廣代碼 [${PROMO_CODE}] 即將於跨年時失效。"
    echo "請預備於 ${NEXT_YEAR} 年 1 月 1 日前更新為下一年度推廣代碼: [${EXPECTED_NEXT_CODE}]。"
    echo "詳情請參考 docs/PROMO_CODES.md 的更新手冊。"
    echo ""

else
    echo "✔ 推廣代碼檢查通過：${PROMO_YEAR} 年度專屬代碼 [${PROMO_CODE}] 正常生效中。"
    echo "  （學員享有 30 天全功能免費 VIP 試用，單機限領一次）"
fi

# 檢查情況 4: 商業常數一致性檢查 (promo.properties vs backend licenseConfig.ts)
LICENSE_CONFIG="${PROJECT_DIR}/backend/src/lib/licenseConfig.ts"
if [ -f "$LICENSE_CONFIG" ]; then
    PROP_BASE=$(grep -E "^BASE_TRIAL_DAYS=" "$PROMO_PROPS" | cut -d'=' -f2 | tr -d ' "\r\n')
    PROP_TRIAL=$(grep -E "^TRIAL_DAYS=" "$PROMO_PROPS" | cut -d'=' -f2 | tr -d ' "\r\n')
    
    CONF_BASE=$(grep -E "export const BASE_TRIAL_DAYS" "$LICENSE_CONFIG" | grep -oE "[0-9]+")
    CONF_TRIAL=$(grep -E "export const PROMO_TOTAL_TRIAL_DAYS" "$LICENSE_CONFIG" | grep -oE "[0-9]+")

    if [ "$PROP_BASE" != "$CONF_BASE" ] || [ "$PROP_TRIAL" != "$CONF_TRIAL" ]; then
        echo "🚨 [錯誤] backend/src/lib/licenseConfig.ts 與 promo.properties 常數不一致！" >&2
        echo "  promo.properties: BASE_TRIAL_DAYS=${PROP_BASE}, TRIAL_DAYS=${PROP_TRIAL}" >&2
        echo "  licenseConfig.ts: BASE_TRIAL_DAYS=${CONF_BASE}, PROMO_TOTAL_TRIAL_DAYS=${CONF_TRIAL}" >&2
        exit 1
    else
        echo "✔ 後端商業常數一致性檢查通過：BASE_TRIAL_DAYS=${CONF_BASE}, PROMO_TOTAL_TRIAL_DAYS=${CONF_TRIAL}"
    fi
fi
echo "================================================================="

