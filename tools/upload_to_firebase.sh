#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_PROPS="${PROJECT_DIR}/version.properties"
APP_ID="1:1039441032082:android:06a2f064f7dc6005dcc74b"

TARGET="${1:-testers}"
RELEASE_NOTES="${2:-M6 雲端授權與 30 天全功能免費試用（設備首次啟動起算、iOS Keychain/Android ANDROID_ID 防重裝刷試用、時鐘倒撥防禦、序號開通 VIP）}"
AUTO_BUMP="${3:-true}"

if [ "$AUTO_BUMP" != "false" ] && [ "$AUTO_BUMP" != "--no-bump" ]; then
    echo ">> 執行自動進版號 (Auto-Bump Version)..."
    "${PROJECT_DIR}/tools/bump_version.sh" patch
fi

# Read latest version properties
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
echo "✔ 發布完成！已升級為 v${VERSION_NAME} (Build ${VERSION_CODE}) 並成功推播至測試人員。"
echo "=========================================="
