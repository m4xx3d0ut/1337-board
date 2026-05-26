#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_GRADLE="$ROOT_DIR/app/build.gradle.kts"

version_name() {
  sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$APP_GRADLE" | head -n 1
}

android_sdk_dir() {
  if [[ -n "${ANDROID_HOME:-}" ]]; then
    printf '%s\n' "$ANDROID_HOME"
    return
  fi
  if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
    printf '%s\n' "$ANDROID_SDK_ROOT"
    return
  fi
  if [[ -f "$ROOT_DIR/local.properties" ]]; then
    sed -nE 's/^sdk\.dir=(.*)$/\1/p' "$ROOT_DIR/local.properties" | head -n 1
  fi
}

latest_build_tool() {
  local tool="$1"
  local sdk_dir
  sdk_dir="$(android_sdk_dir)"
  if [[ -z "$sdk_dir" || ! -d "$sdk_dir/build-tools" ]]; then
    return 1
  fi
  find "$sdk_dir/build-tools" -type f -name "$tool" | sort -V | tail -n 1
}

VERSION_NAME="$(version_name)"
if [[ -z "$VERSION_NAME" ]]; then
  echo "Could not read versionName from $APP_GRADLE" >&2
  exit 1
fi

RELEASE_TAG="${1:-v$VERSION_NAME}"
if [[ "$RELEASE_TAG" != v"$VERSION_NAME" ]]; then
  echo "Release tag $RELEASE_TAG does not match versionName $VERSION_NAME" >&2
  exit 1
fi

ARTIFACT_DIR="$ROOT_DIR/app/build/release-artifacts/$RELEASE_TAG"
APK_DIR="$ROOT_DIR/app/build/outputs/apk/release"

cd "$ROOT_DIR"
./gradlew clean testDebugUnitTest assembleRelease

rm -rf "$ARTIFACT_DIR"
mkdir -p "$ARTIFACT_DIR"

SIGNED_APK="$APK_DIR/app-release.apk"
UNSIGNED_APK="$APK_DIR/app-release-unsigned.apk"
COPIED_APKS=()

if [[ -f "$SIGNED_APK" ]]; then
  signed_name="1337-board-$RELEASE_TAG-release-signed.apk"
  cp "$SIGNED_APK" "$ARTIFACT_DIR/$signed_name"
  COPIED_APKS+=("$signed_name")

  APKSIGNER="$(latest_build_tool apksigner || true)"
  if [[ -z "$APKSIGNER" ]]; then
    echo "Could not locate apksigner; signed APK was built but not verified" >&2
    exit 1
  fi
  "$APKSIGNER" verify --verbose --print-certs "$ARTIFACT_DIR/$signed_name"
fi

if [[ -f "$UNSIGNED_APK" ]]; then
  unsigned_name="1337-board-$RELEASE_TAG-release-unsigned.apk"
  cp "$UNSIGNED_APK" "$ARTIFACT_DIR/$unsigned_name"
  COPIED_APKS+=("$unsigned_name")
fi

if [[ "${#COPIED_APKS[@]}" -eq 0 ]]; then
  echo "No release APK found in $APK_DIR" >&2
  exit 1
fi

(
  cd "$ARTIFACT_DIR"
  sha256sum "${COPIED_APKS[@]}" > SHA256SUMS
)

printf 'Release artifacts written to %s\n' "$ARTIFACT_DIR"
