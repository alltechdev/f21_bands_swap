#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ANDROID_JAR="${ANDROID_JAR:-$HOME/Android/Sdk/platforms/android-35/android.jar}"
TEST_CLASSES="$(mktemp -d)"
trap 'rm -rf "$TEST_CLASSES"' EXIT
TEST_CP="build/classes:$ANDROID_JAR:libs/xz-1.9.jar"
javac -cp "$TEST_CP" -d "$TEST_CLASSES" \
    tests/support/android/os/Build.java tests/com/flipphoneguy/f21bands/*.java
java -cp "$TEST_CLASSES:$TEST_CP" com.flipphoneguy.f21bands.DeviceDetectorTest
java -cp "$TEST_CLASSES:$TEST_CP" com.flipphoneguy.f21bands.M5SupportTest
