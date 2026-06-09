#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_GRADLE="$ROOT_DIR/app/build.gradle.kts"
CHANGELOG="$ROOT_DIR/CHANGELOG.md"
GITHUB_REPO="${GITHUB_REPO:-m4xx3d0ut/1337-board}"

version_name() {
  sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$APP_GRADLE" | head -n 1
}

release_notes() {
  local section="## [$VERSION_NAME]"
  awk -v section="$section" '
    index($0, section) == 1 { found = 1; next }
    found && /^## \[/ { exit }
    found { print }
  ' "$CHANGELOG"
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

cd "$ROOT_DIR"

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Working tree must be clean before publishing" >&2
  exit 1
fi

if [[ "$(git rev-parse "$RELEASE_TAG^{commit}")" != "$(git rev-parse HEAD)" ]]; then
  echo "Tag $RELEASE_TAG must point at HEAD" >&2
  exit 1
fi

ARTIFACT_DIR="$ROOT_DIR/app/build/release-artifacts/$RELEASE_TAG"
if [[ ! -d "$ARTIFACT_DIR" ]]; then
  echo "Missing artifact directory: $ARTIFACT_DIR" >&2
  echo "Run scripts/build-release.sh $RELEASE_TAG first." >&2
  exit 1
fi

mapfile -t RELEASE_ASSETS < <(find "$ARTIFACT_DIR" -maxdepth 1 -type f \( -name '*.apk' -o -name 'SHA256SUMS' \) | sort)
if [[ "${#RELEASE_ASSETS[@]}" -eq 0 ]]; then
  echo "No release assets found in $ARTIFACT_DIR" >&2
  exit 1
fi

NOTES_FILE="$(mktemp)"
trap 'rm -f "$NOTES_FILE"' EXIT
release_notes > "$NOTES_FILE"
if ! grep -q '[^[:space:]]' "$NOTES_FILE"; then
  cat > "$NOTES_FILE" <<NOTES
Production release artifacts for 1337 Board $RELEASE_TAG.

Install the signed APK for sideload testing. Unsigned artifacts are included only when produced by the build for review and reproducibility checks. Verify downloads with SHA256SUMS.
NOTES
fi

if gh release view "$RELEASE_TAG" --repo "$GITHUB_REPO" >/dev/null 2>&1; then
  gh release edit "$RELEASE_TAG" \
    --repo "$GITHUB_REPO" \
    --title "1337 Board $RELEASE_TAG" \
    --prerelease \
    --notes-file "$NOTES_FILE"
  gh release upload "$RELEASE_TAG" "${RELEASE_ASSETS[@]}" --repo "$GITHUB_REPO" --clobber
else
  gh release create "$RELEASE_TAG" "${RELEASE_ASSETS[@]}" \
    --repo "$GITHUB_REPO" \
    --title "1337 Board $RELEASE_TAG" \
    --prerelease \
    --notes-file "$NOTES_FILE"
fi
