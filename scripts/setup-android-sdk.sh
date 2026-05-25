#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-$ROOT_DIR/.android-sdk}"
CMDLINE_ZIP="${TMPDIR:-/tmp}/leetboard-commandlinetools.zip"
CMDLINE_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"

mkdir -p "$SDK_DIR/cmdline-tools"

if [ ! -x "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    curl -L --fail --retry 3 -o "$CMDLINE_ZIP" "$CMDLINE_URL"
    rm -rf "$SDK_DIR/cmdline-tools/latest" "${TMPDIR:-/tmp}/leetboard-cmdline-tools"
    mkdir -p "${TMPDIR:-/tmp}/leetboard-cmdline-tools"
    unzip -q "$CMDLINE_ZIP" -d "${TMPDIR:-/tmp}/leetboard-cmdline-tools"
    mv "${TMPDIR:-/tmp}/leetboard-cmdline-tools/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
fi

yes | "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" --licenses >/dev/null
"$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK_DIR" \
    "platform-tools" \
    "platforms;android-35" \
    "build-tools;35.0.0"

printf 'sdk.dir=%s\n' "$SDK_DIR" > "$ROOT_DIR/local.properties"
echo "Android SDK ready at $SDK_DIR"
