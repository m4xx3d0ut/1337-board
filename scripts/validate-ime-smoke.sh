#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ADB="${ANDROID_SDK_ROOT:-$ROOT_DIR/.android-sdk}/platform-tools/adb"
PACKAGE="org.leetboard.ime"
IME_ID="$PACKAGE/.ime.ModernKeyboardImeService"
DIAGNOSTICS="$PACKAGE/.DiagnosticsActivity"

if [ ! -x "$ADB" ]; then
    ADB="$(command -v adb || true)"
fi

if [ -z "$ADB" ] || [ ! -x "$ADB" ]; then
    echo "adb not found. Run scripts/setup-android-sdk.sh or set ANDROID_SDK_ROOT." >&2
    exit 2
fi

"$ROOT_DIR/gradlew" -p "$ROOT_DIR" assembleDebug testDebugUnitTest lintDebug

DEVICE_COUNT="$("$ADB" devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count + 0 }')"
if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo "No ready adb device/emulator found; IME smoke validation cannot run." >&2
    exit 3
fi

"$ADB" install -r "$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" shell ime enable "$IME_ID"
"$ADB" shell ime set "$IME_ID"
"$ADB" shell am start -n "$DIAGNOSTICS"
"$ADB" shell ime list -s | grep -F "$IME_ID" >/dev/null
echo "IME smoke validation started diagnostics with $IME_ID selected."
