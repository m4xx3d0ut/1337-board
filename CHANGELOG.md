# Changelog

All notable changes to 1337 Board are documented here.

## [Unreleased]

### Changed

- Adjusted the four-row phone layout so lower-row alternates prioritize common punctuation and home-row alternates carry programmer symbols.

## [0.1.3] - 2026-05-27

### Added

- Added per-orientation layout selection for different portrait and landscape keyboard layouts.
- Added Num-hold Fn/navigation overlays for four-row, compact five-row, and full five-row layouts.
- Added four-row quick access to Ctrl, Alt, and F1-F12 from the overlay strip.
- Added compact five-row quick access to Ctrl and Alt from the overlay strip.
- Added quick navigation overlays for PgUp/PgDn, arrow movement, Home, and End.
- Added top-row number long-press access on the four-row layout.
- Added separate regular-key and special-action long-press timing settings.

### Changed

- Full five-row Fn hold and Num hold now expose the same F1-F12, Ins, Del, and Backspace top-row overlay.
- Shift-held number and punctuation keys now emit their shifted symbols while shift lock keeps number keys normal.
- Suggestion-bar punctuation handling now trims the pending trailing space after accepting a suggestion.
- Tab can accept the first available suggestion when no modifiers are active.
- Show `Fn` in the held-key preview while a Num-hold Fn overlay is active.
- Improved key label fitting for compact keys and secondary labels.
- Updated the README visual grid with Pad3 captures of custom four-row and compact five-row Num-hold overlays.

### Fixed

- Quick modifier sticky state is visually indicated while waiting for the next keypress.
- Num-hold overlays avoid applying Home/End behavior to WASD left/right overlays, preserving shift-arrow style selection behavior.

## [0.1.2] - 2026-05-27

### Added

- Added rollover-aware tap dispatch to improve fast multi-key typing order.
- Added conservative local typed suggestions and autocorrect controls.
- Added Fn long-hold access to function keys on five-row layouts, with adjustable timing.
- Added long-press emoji access from `#+`.
- Added top-right hold indicators for Fn and `#+` keys.

### Changed

- Trim glide word trailing space before punctuation.
- Use a custom material-style emoji key icon instead of a text glyph.
- Expand Fn and arrow navigation handling for Home, End, PgUp, and PgDn access.

### Fixed

- Improved backspace/delete handling around selected text and shifted delete behavior.
- Improved fast typing resilience with bounded touch rollover handling.

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
