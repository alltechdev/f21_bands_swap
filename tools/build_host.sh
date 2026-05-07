#!/usr/bin/env bash
# Linux-host build of F21BandsSwap.apk for development. Mirrors what the
# official Termux-side build.sh does, but uses Android SDK build-tools for
# aapt2/d8 (the system-shipped Debian aapt2 is too old to link against modern
# theme attributes) and javac for Java. Same outputs, same artifact name.
#
# Requirements:
#   ANDROID_HOME pointing at an SDK install that has build-tools and an
#     android-XX platform with android.jar.
#   ~/.android/framework-res.apk pulled once from the target device:
#     adb pull /system/framework/framework-res.apk ~/.android/framework-res.apk
#   ~/.android/debug.keystore (created by Android Studio / SDK by default).
set -e

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

[ -n "$ANDROID_HOME" ] || { echo "ANDROID_HOME not set"; exit 1; }

BT_VER="$(ls -1 "$ANDROID_HOME/build-tools/" 2>/dev/null | sort -V | tail -1)"
BT="$ANDROID_HOME/build-tools/$BT_VER"
[ -x "$BT/aapt2" ] || { echo "no build-tools at $BT"; exit 1; }

PLATFORM_VER="$(ls -1 "$ANDROID_HOME/platforms/" 2>/dev/null | sort -V | tail -1)"
ANDROID_JAR_DEFAULT="$ANDROID_HOME/platforms/$PLATFORM_VER/android.jar"
ANDROID_JAR="${ANDROID_JAR:-${HOME}/.android/android.jar}"
[ -f "$ANDROID_JAR" ] || ANDROID_JAR="$ANDROID_JAR_DEFAULT"
[ -f "$ANDROID_JAR" ] || { echo "no android.jar found"; exit 1; }

LINK_INCLUDE="$ANDROID_JAR"

KEYSTORE="${HOME}/.android/debug.keystore"
KEYSTORE_ALIAS="androiddebugkey"
KEYSTORE_PASS="android"
KEY_PASS="android"
[ -f "$KEYSTORE" ] || { echo "no $KEYSTORE"; exit 1; }

APK_OUT="F21BandsSwap.apk"
BUILD_DIR="build"
LIBS_DIR="libs"

XZ_VERSION="1.9"
XZ_JAR="$LIBS_DIR/xz-${XZ_VERSION}.jar"
XZ_URL="https://repo1.maven.org/maven2/org/tukaani/xz/${XZ_VERSION}/xz-${XZ_VERSION}.jar"

VERSION_NAME="$(tr -d '[:space:]' < VERSION 2>/dev/null || echo "1.0.0")"
VERSION_CODE="$(awk -F. '{ printf "%d%02d%02d", $1,$2,$3 }' <<< "$VERSION_NAME")"

mkdir -p "$LIBS_DIR"
if [ ! -f "$XZ_JAR" ]; then
    curl -fsSL "$XZ_URL" -o "$XZ_JAR"
fi

echo "════════════════════════════════════"
echo "  Building F21BandsSwap v${VERSION_NAME} (code ${VERSION_CODE})"
echo "  build-tools: $BT_VER   platform: $PLATFORM_VER"
echo "════════════════════════════════════"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex"

echo "[1/5] Compiling resources..."
"$BT/aapt2" compile --dir res/ -o "$BUILD_DIR/resources.zip"

echo "[2/5] Linking resources..."
LINK_ASSETS=()
[ -d assets ] && LINK_ASSETS=(-A assets)
"$BT/aapt2" link \
    -o "$BUILD_DIR/app_res.apk" \
    --manifest AndroidManifest.xml \
    -I "$LINK_INCLUDE" \
    --java "$BUILD_DIR/gen" \
    --min-sdk-version 23 \
    --target-sdk-version 35 \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    "${LINK_ASSETS[@]}" \
    "$BUILD_DIR/resources.zip"

echo "[3/5] Compiling Java..."
find src/ "$BUILD_DIR/gen/" -name "*.java" > "$BUILD_DIR/sources.txt"
javac \
    -source 1.8 -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR:$XZ_JAR" \
    -d "$BUILD_DIR/classes" \
    @"$BUILD_DIR/sources.txt"

echo "[4/5] Dexing..."
CLASS_FILES=()
while IFS= read -r f; do CLASS_FILES+=("$f"); done < <(find "$BUILD_DIR/classes" -name "*.class")
"$BT/d8" \
    --output "$BUILD_DIR/dex" \
    --lib "$ANDROID_JAR" \
    --min-api 23 \
    "${CLASS_FILES[@]}" \
    "$XZ_JAR"

echo "[5/5] Packaging and signing..."
cp "$BUILD_DIR/app_res.apk" "$BUILD_DIR/app_unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -j "../app_unsigned.apk" classes.dex)

"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEYSTORE_ALIAS" \
    --ks-pass "pass:$KEYSTORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --out "$APK_OUT" \
    "$BUILD_DIR/app_unsigned.apk"

rm -f "${APK_OUT}.idsig"

SIZE=$(stat -c%s "$APK_OUT")
echo ""
echo "════════════════════════════════════"
echo "  Built: $APK_OUT  ($((SIZE/1024)) KB)"
echo "════════════════════════════════════"
