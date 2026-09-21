#!/usr/bin/env bash
set -e

PROJECT_DIR="/Users/tunghunglu/projects/fitnessrider"
APK_PATH="${PROJECT_DIR}/fitnessrider-v1.0.0-beta.apk"
APP_ID="1:1039441032082:android:06a2f064f7dc6005dcc74b"

TARGET="${1:-testers}"
RELEASE_NOTES="${2:-v1.0.0-beta 最新版本：HUD 左側握把把位大圖示、右側經典騎乘姿勢圖示回歸、40+教練口訣庫、10首完整示範課表}"

if [[ "$TARGET" == *"@"* ]]; then
    DIST_FLAG="--testers"
else
    DIST_FLAG="--groups"
fi

echo "=========================================="
echo "  FitnessRider Firebase App Distribution  "
echo "=========================================="
echo "專案目錄: ${PROJECT_DIR}"
echo "APK 檔案: ${APK_PATH}"
echo "Firebase App ID: ${APP_ID}"
echo "分發對象 ($DIST_FLAG): ${TARGET}"
echo "發布說明: ${RELEASE_NOTES}"
echo "=========================================="

BUILD_APK="${PROJECT_DIR}/android/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$BUILD_APK" ]; then
    echo "同步最新編譯之 APK: ${BUILD_APK} -> ${APK_PATH}"
    cp "$BUILD_APK" "$APK_PATH"
fi

if [ ! -f "$APK_PATH" ]; then
    echo "錯誤: 找不到 APK 檔案: $APK_PATH"
    echo "請先執行: cd android && ./gradlew assembleDebug"
    exit 1
fi

echo "正在檢查 Firebase 登入狀態..."
if ! firebase login:list 2>&1 | grep -q "@"; then
    echo "尚未登入 Firebase，請先執行 firebase login 完成登入授權"
    firebase login
fi

echo "開始上傳 APK 到 Firebase App Distribution..."
firebase appdistribution:distribute "$APK_PATH" \
    --app "$APP_ID" \
    $DIST_FLAG "$TARGET" \
    --release-notes "$RELEASE_NOTES"

echo "上傳完成！測試人員將收到下載通知。"
