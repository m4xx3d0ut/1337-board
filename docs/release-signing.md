# Release Signing

1337 Board uses `org.leetboard.ime` as its Android application ID. Once a production-signed APK is published, future APK updates for that package must be signed with the same app signing key.

## Key Custody

Use one developer-owned production keystore for GitHub APK releases and future F-Droid continuity. Keep it out of git and back it up offline.

Recommended local path and alias:

```sh
keytool -genkeypair \
  -keystore ~/.android/1337-board-release.jks \
  -alias leetboard-release \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Record the certificate fingerprint after generating the key:

```sh
keytool -list -v \
  -keystore ~/.android/1337-board-release.jks \
  -alias leetboard-release
```

Publish the SHA-256 certificate fingerprint in the README once production signing begins.

## Local Signing Environment

Set these variables only in a local shell or local-only env file:

```sh
export LEETBOARD_RELEASE_STORE_FILE="$HOME/.android/1337-board-release.jks"
export LEETBOARD_RELEASE_STORE_PASSWORD="..."
export LEETBOARD_RELEASE_KEY_ALIAS="leetboard-release"
export LEETBOARD_RELEASE_KEY_PASSWORD="..."
```

When all four variables are set, `assembleRelease` signs the release APK. When any variable is missing, the release build remains unsigned for CI and F-Droid review.

## Build And Publish

Build release artifacts:

```sh
scripts/build-release.sh v0.1.1
```

The script runs unit tests, builds the release APK, verifies signed APKs with `apksigner`, and writes artifacts to `app/build/release-artifacts/v0.1.1/`.

Publish artifacts after the tag points at `HEAD`:

```sh
scripts/publish-github-release.sh v0.1.1
```

Push GitHub branches or tags with the project key wrapper:

```sh
GIT_SSH_COMMAND="ssh -i ~/.ssh/github-m -o IdentitiesOnly=yes -o IdentityAgent=none -v" git push github dev
GIT_SSH_COMMAND="ssh -i ~/.ssh/github-m -o IdentitiesOnly=yes -o IdentityAgent=none -v" git push github v0.1.1
```

## Distribution Notes

- GitHub releases should publish the production-signed APK and `SHA256SUMS`.
- GitHub Actions builds unsigned APK artifacts only. Do not add production keystore secrets to GitHub for the first production path.
- F-Droid packaging should prefer reproducible builds before official submission.
- If Google Play is used later, enroll Play App Signing with this existing app signing identity and use a separate upload key.
- Users who installed a debug-key preview APK may need to uninstall before installing the first production-signed APK.
