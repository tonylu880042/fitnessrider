#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_PROPS="${PROJECT_DIR}/version.properties"
APP_ID="1:1039441032082:android:06a2f064f7dc6005dcc74b"

TARGET="${1:-testers}"
RELEASE_NOTES="${2:-M6 雲端授權與 30 天全功能免費試用（設備首次啟動起算、iOS Keychain/Android ANDROID_ID 防重裝刷試用、時鐘倒撥防禦、序號開通 VIP）}"
AUTO_BUMP="${3:-true}"

# 只認得這三種值，其餘一律視為使用者打錯字並中止——不要悄悄把 "no"、"0" 之類的
# 誤植當成「進版」處理（那樣會在使用者以為關掉自動進版時，仍然默默把版號吃掉）。
case "$AUTO_BUMP" in
    true|false|--no-bump) ;;
    *)
        echo "錯誤: 無法辨識的 AUTO_BUMP 參數 '${AUTO_BUMP}'（必須是 true / false / --no-bump）" >&2
        exit 1
        ;;
esac

# 讀「目前」的版號（版號的推進被移到本腳本最後、且只在編譯與上傳都成功後才執行，
# 見檔案底部的說明）。version.properties 因此代表「即將發布的版號」，直到成功發布
# 為止；一旦發布成功才會進版，準備給下一次發布使用。
VERSION_NAME=$(grep -E "^VERSION_NAME=" "$VERSION_PROPS" | cut -d'=' -f2 | tr -d ' "')
VERSION_CODE=$(grep -E "^VERSION_CODE=" "$VERSION_PROPS" | cut -d'=' -f2 | tr -d ' "')

VERSION_NAME="${VERSION_NAME:-1.0.1}"
VERSION_CODE="${VERSION_CODE:-2}"

APK_PATH="${PROJECT_DIR}/fitnessrider-v${VERSION_NAME}.apk"

if [[ "$TARGET" == *"@"* ]]; then
    DIST_FLAG="--testers"
else
    DIST_FLAG="--groups"
fi

echo "=========================================="
echo "  FitnessRider Firebase App Distribution  "
echo "=========================================="
echo "專案目錄: ${PROJECT_DIR}"
echo "發布版號: v${VERSION_NAME} (Build ${VERSION_CODE})"
echo "APK 檔案: ${APK_PATH}"
echo "Firebase App ID: ${APP_ID}"
echo "分發對象 ($DIST_FLAG): ${TARGET}"
echo "發布說明: ${RELEASE_NOTES}"
echo "=========================================="

# 檢查年度推廣代碼有效性與過期提醒
"${PROJECT_DIR}/tools/check_promo_code.sh"

echo ">> 正在編譯 Android Debug APK (Version ${VERSION_NAME}, Code ${VERSION_CODE})..."
(cd "${PROJECT_DIR}/android" && ./gradlew assembleDebug)

BUILD_APK="${PROJECT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$BUILD_APK" ]; then
    echo "錯誤: 找不到剛編譯完成的 APK: $BUILD_APK"
    exit 1
fi

echo ">> 同步 APK 至: ${APK_PATH}"
cp "$BUILD_APK" "$APK_PATH"

echo ">> 正在檢查 Firebase 登入狀態..."
if ! firebase login:list 2>&1 | grep -q "@"; then
    echo "尚未登入 Firebase，請先執行 firebase login 完成登入授權"
    firebase login
fi

FULL_RELEASE_NOTES="v${VERSION_NAME} (Build ${VERSION_CODE})：${RELEASE_NOTES}"

echo ">> 開始上傳 APK 到 Firebase App Distribution..."
firebase appdistribution:distribute "$APK_PATH" \
    --app "$APP_ID" \
    $DIST_FLAG "$TARGET" \
    --release-notes "$FULL_RELEASE_NOTES"

echo "=========================================="
echo "✔ 發布完成！v${VERSION_NAME} (Build ${VERSION_CODE}) 已成功推播至測試人員。"
echo "=========================================="

# 進版移到這裡、且只在編譯與上傳都成功「之後」才執行：原本在腳本一開頭就先進版，
# 編譯或上傳只要失敗，版號就已經被吃掉，下次重跑會直接跳號，且發布的 APK 版號跟
# 「這次到底發布成功了沒」完全脫鉤。set -e 保證只要上面任何一步失敗，腳本會在
# 到達這裡之前就中止，不會執行這次的進版。
#
# bump_version.sh 會原子性地同時更新 version.properties、ios/project.yml、
# ios/FitnessRider.xcodeproj/project.pbxproj 三份檔案，維持三者版號一致——即使
# 這裡只發布 Android，iOS 那兩份檔案的版號也會跟著一起推進，這是既有、刻意共用
# 單一版號的設計（雙平台共用同一個 marketing version），不在這次修正範圍內。
if [ "$AUTO_BUMP" != "false" ] && [ "$AUTO_BUMP" != "--no-bump" ]; then
    echo ">> 發布成功，執行自動進版號 (Auto-Bump Version) 供下次發布使用..."
    "${PROJECT_DIR}/tools/bump_version.sh" patch
fi
