# Changelog

All notable changes to 1337 Board are documented here.

## [Unreleased]

## [0.1.1] - 2026-05-26

### Added

- Added local release build and GitHub release publishing scripts.
- Added a GitHub Actions workflow that validates tagged release builds and uploads unsigned review artifacts.
- Added a local release signing environment template.
- Added an adjustable glide dwell key threshold setting, with the current tuned value as the default.

### Changed

- Added a secondary glide toggle action to the settings key for quick keyboard-side enable/disable.
- Improved release signing documentation for production-signed APKs, unsigned CI artifacts, and future F-Droid review.

### Fixed

- Aligned the Fn-layer up arrow over the down arrow.
- Sent Ctrl/Alt number combinations as Android key events so terminal shortcuts such as Termux session switching can work.
- Improved glide dwell scoring and regression coverage for slow, intentional paths such as `testing`.

## [0.1.0] - 2026-05-26

### Added

- Initial public prerelease with modern Android keyboard layout, customization, themes, glide typing, speech input, and settings.
