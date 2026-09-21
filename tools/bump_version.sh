#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION_PROPS="${PROJECT_DIR}/version.properties"
IOS_PROJECT_YML="${PROJECT_DIR}/ios/project.yml"
IOS_PBXPROJ="${PROJECT_DIR}/ios/FitnessRider.xcodeproj/project.pbxproj"

BUMP_TYPE="${1:-patch}"

if [ ! -f "$VERSION_PROPS" ]; then
    echo "VERSION_NAME=1.0.0" > "$VERSION_PROPS"
    echo "VERSION_CODE=1" >> "$VERSION_PROPS"
fi

# Read current values
CURRENT_VERSION_NAME=$(grep -E "^VERSION_NAME=" "$VERSION_PROPS" | cut -d'=' -f2 | tr -d ' "')
CURRENT_VERSION_CODE=$(grep -E "^VERSION_CODE=" "$VERSION_PROPS" | cut -d'=' -f2 | tr -d ' "')

CURRENT_VERSION_NAME="${CURRENT_VERSION_NAME:-1.0.0}"
CURRENT_VERSION_CODE="${CURRENT_VERSION_CODE:-1}"

# Calculate new version code
NEW_VERSION_CODE=$((CURRENT_VERSION_CODE + 1))

# Split version into components
IFS='.' read -r MAJOR MINOR PATCH <<< "$CURRENT_VERSION_NAME"
MAJOR="${MAJOR:-1}"
MINOR="${MINOR:-0}"
PATCH="${PATCH:-0}"

case "$BUMP_TYPE" in
    patch)
        PATCH=$((PATCH + 1))
        NEW_VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        NEW_VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
        ;;
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        NEW_VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"
        ;;
    *)
        # 明確指定版號字面值時，必須符合 X.Y.Z 格式，否則任何打錯字的參數
        # （例如 `./tools/bump_version.sh Patch`）都會被當成版號字面值，
        # 一路寫進 versionName、MARKETING_VERSION 與 APK 檔名。
        if [[ "$BUMP_TYPE" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
            NEW_VERSION_NAME="$BUMP_TYPE"
        else
            echo "錯誤: 無法辨識的版本參數 '${BUMP_TYPE}'（必須是 patch / minor / major，或符合 X.Y.Z 格式的版號，例如 1.2.3）" >&2
            exit 1
        fi
        ;;
esac

echo "=========================================="
echo "  FitnessRider 版本升級 (Bump Version)     "
echo "=========================================="
echo "目前版本: ${CURRENT_VERSION_NAME} (Build ${CURRENT_VERSION_CODE})"
echo "升級後版號: ${NEW_VERSION_NAME} (Build ${NEW_VERSION_CODE})"
echo "=========================================="

# 1. Update version.properties
cat <<EOF > "$VERSION_PROPS"
# FitnessRider Version Configuration
# Automatically managed by tools/bump_version.sh and tools/upload_to_firebase.sh
VERSION_NAME=${NEW_VERSION_NAME}
VERSION_CODE=${NEW_VERSION_CODE}
EOF

# 2. Update iOS project.yml if present
if [ -f "$IOS_PROJECT_YML" ]; then
    sed -i '' "s/MARKETING_VERSION: .*/MARKETING_VERSION: \"${NEW_VERSION_NAME}\"/" "$IOS_PROJECT_YML"
    sed -i '' "s/CURRENT_PROJECT_VERSION: .*/CURRENT_PROJECT_VERSION: \"${NEW_VERSION_CODE}\"/" "$IOS_PROJECT_YML"
fi

# 3. Update iOS project.pbxproj if present
if [ -f "$IOS_PBXPROJ" ]; then
    sed -i '' "s/MARKETING_VERSION = [^;]*;/MARKETING_VERSION = ${NEW_VERSION_NAME};/g" "$IOS_PBXPROJ"
    sed -i '' "s/CURRENT_PROJECT_VERSION = [^;]*;/CURRENT_PROJECT_VERSION = ${NEW_VERSION_CODE};/g" "$IOS_PBXPROJ"
fi

echo "✔ 已更新 version.properties"
echo "✔ 已同步 iOS project.pbxproj 與 project.yml"
echo "版號進版完成: ${NEW_VERSION_NAME} (${NEW_VERSION_CODE})"
