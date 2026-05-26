# Hacker's Keyboard Modernization Scope

Date: 2026-05-24

## Purpose

Define the scope for a modern Android keyboard inspired by Hacker's Keyboard: a compact PC-like layout with number row, punctuation, arrow keys, Esc, Tab, Ctrl, Alt, and programmer/terminal-oriented options. This should be a new implementation using current Android APIs, current build tooling, modern theming, and deeper customization than the original app.

Hacker's Keyboard is useful as a product reference, but its implementation should not be treated as a modern base without a serious rewrite.

## Research Summary

Hacker's Keyboard is old by its own maintainer's description. The official README says the project began in 2011 from the Android 2.3 Gingerbread AOSP keyboard, needs major rewrites for newer APIs, and has known modern Android breakage around language switching and popup keys. The maintainer also notes no current plan for significant updates.

Current public package metadata also points to staleness. F-Droid lists v1.41.1 as the suggested version, added on 2018-12-07, with Android 4.0+ support. The upstream Gradle file still uses `compileSdkVersion 26`, `targetSdkVersion 26`, `minSdkVersion 14`, legacy `com.android.support` dependencies, and an old Android Gradle Plugin generation. Google Play now requires new apps and updates to target Android 15 / API 35 or higher, so the old target SDK is not viable for store distribution in 2026.

Android's old `android.inputmethodservice.Keyboard` and related `KeyboardView` convenience UI are deprecated as of API 29. The modern direction is still `InputMethodService`, `EditorInfo`, and `InputConnection`, but the keyboard surface itself should be reimplemented as a custom UI instead of depending on deprecated keyboard widgets.

Android's platform speech APIs are useful references but are not enough for a privacy-first, Google-free voice input requirement. `SpeechRecognizer` can use system recognizers and Android exposes offline preference flags, but documentation warns recognizer implementations may ignore those flags. A Google-free path should use an app-controlled local speech engine or optional model pack.

## Current Hacker's Keyboard Feature Inventory

- Four-row and five-row soft keyboard modes.
- Five-row layout with separate number keys, familiar punctuation placement, Tab, Ctrl, and arrow keys.
- Esc/Ctrl/Tab/arrow key support aimed at SSH, terminals, and devices without physical navigation keys.
- Multitouch support for modifier keys inherited from the old AOSP Gingerbread keyboard.
- About 30 language layouts, including QWERTY, QWERTZ, AZERTY, Dvorak, Carpalx, Neo2, Cyrillic, Greek, Hebrew, Arabic, Persian, Thai, Tamil, and others.
- Compact five-row options and configurable behavior such as Ctrl-A handling.
- Optional dictionary packs, with dictionaries delivered as plug-in packages rather than bundled source templates.
- Theme history includes Holo/ICS-style themes and later Material-ish light/dark additions, but not modern Material 3/dynamic color or detailed geometry controls.

## Modernization Goals

- Preserve the Hacker's Keyboard value proposition: desktop-style keys on Android, especially for terminals, remote shells, programming, text editing, and tablet use.
- Build a clean Kotlin Android app with a modern Gradle/AGP stack, AndroidX, and a target SDK that satisfies current Play policy.
- Use `InputMethodService` as the IME entry point and implement a custom keyboard rendering/touch engine rather than `Keyboard`/`KeyboardView`.
- Provide a robust settings system for layout, optional keys, action keys, numpad behavior, gestures, privacy, speech input, and themes.
- Support enable/disable controls for optional keys and custom action keys, starting with safe preset slots before full freeform layout editing.
- Support a landscape numpad layout and an optional numpad toggle key.
- Support swipe typing through a local gesture engine and dictionary/model path.
- Support speech-to-text without GApps or Google services through optional local engine/model packs suitable for F-Droid and Play distribution.
- Provide Material 3 settings and theme controls, with dynamic color support on Android 12+, system light/dark modes, alternate dark mode, cyberpunk, and 1337 green-on-black themes.
- Allow separate portrait and landscape tuning for key border radius, border weight, gaps, and margins.
- Make privacy a product requirement: no ads, no trackers, no network permission by default, no contacts access in the base app, no password learning, and clear data-safety posture.
- Prioritize modern phones, foldables, tablets, DeX/desktop modes, and ChromeOS-style large screens.

## Non-Goals For The First Release

- Do not attempt a line-by-line fork modernization unless the team explicitly chooses an Apache-2.0 code reuse path.
- Do not target feature parity with Gboard, SwiftKey, or full ML prediction stacks.
- Do not include cloud sync, remote personalization, ads, analytics SDKs, or remote model downloads in the base app.
- Do not ship all legacy Hacker's Keyboard language layouts in MVP. Treat them as importable/configurable follow-up packs.
- Do not make platform `SpeechRecognizer` the only voice path, because it may depend on OEM or Google services and may not honor offline-only preferences.
- Do not ship a full drag-any-key editor in MVP. Start with configurable preset slots and promote freeform non-alphanumeric positioning after usability validation.
- Do not depend on deprecated platform keyboard widgets for core rendering.

## Recommended Technical Direction

### Platform Baseline

- Language: Kotlin.
- Minimum SDK: recommend API 26 as the default floor for review. It aligns with modern Android behavior while still covering Android 8.0+ devices. If the product is only for newer devices, consider API 29 or API 31 after a device-support decision.
- Target SDK: API 35 or newer as of the current Play requirement; use the newest stable compile SDK available at project start.
- Build: current Android Gradle Plugin, Gradle version catalog, AndroidX, Kotlin serialization or Moshi for layout/theme data, DataStore for preferences.
- UI split: custom View or custom rendering layer for the keyboard surface; Jetpack Compose + Material 3 for settings, onboarding, diagnostics, and layout/theme editors.

### IME Architecture

- `ModernKeyboardImeService : InputMethodService`
  - Owns lifecycle callbacks, `EditorInfo` handling, input view creation, candidate/toolbar view creation, and system integration.
- `KeyboardSurfaceView`
  - Low-latency rendering and touch handling.
  - Supports multitouch modifiers, repeat keys, long press, popup previews, swipe path capture, and per-orientation geometry.
  - Avoids deprecated `Keyboard` and `KeyboardView`.
- `LayoutEngine`
  - Data-driven rows/layers/keys with percentage or weight-based sizing.
  - Handles portrait, landscape, tablet, foldable, one-handed, compact, full five-row, and landscape numpad modes.
- `CustomizationEngine`
  - Enables/disables optional keys and maps custom actions to safe preset slots.
  - Supports action keys such as Esc, Tab, Ctrl, Alt, arrows, clipboard, settings, language switch, microphone, numpad toggle, symbol layer, and user-defined text snippets.
  - Owns validation rules for future non-alphanumeric key repositioning so users cannot create unreachable or overlapping layouts.
- `KeyActionEngine`
  - Maps keys to `InputConnection` operations, `performEditorAction`, `commitText`, `deleteSurroundingText`, and raw `KeyEvent` dispatch where terminal compatibility requires it.
  - Tracks Shift/Ctrl/Alt/meta state and sticky/locked modifier behavior.
- `GestureTypingEngine`
  - Captures swipe paths, normalizes them against the active layout, ranks local dictionary/model candidates, and commits the selected word.
  - Runs locally with no network dependency and can be disabled per user preference or sensitive input type.
- `SpeechInputEngine`
  - Provides a pluggable local STT interface for optional engine/model packs.
  - Candidate engines include Vosk, whisper.cpp, and sherpa-onnx, subject to license, size, latency, accuracy, and Android packaging validation.
  - Uses microphone permission only when speech input is enabled and active.
- `TextContextPolicy`
  - Interprets `EditorInfo.inputType`, `imeOptions`, password/private fields, multiline fields, URL/email/number/phone modes, and `IME_FLAG_NO_PERSONALIZED_LEARNING`.
  - Disables learning, gesture suggestions, previews, and speech capture in fields where privacy or input type requires it.
- `ThemeEngine`
  - Material 3 token model, dynamic color on Android 12+, custom palettes, high contrast, popup/candidate styling, and named presets.
  - Supports system light/dark, alternate dark, terminal, cyberpunk, and 1337 green-on-black themes.
  - Stores separate portrait/landscape controls for key radius, border weight, gaps, margins, and row spacing.
- `Preferences`
  - DataStore-backed settings for layout, theme, feedback, privacy, languages, terminal options, custom keys, speech packs, and gestures.

## MVP Scope

### Product UX

- Onboarding flow that explains enabling the IME and selecting it as the current keyboard.
- Settings screen for layouts, optional keys, custom action keys, numpad behavior, theme, feedback, privacy, speech packs, gestures, and advanced terminal behavior.
- Keyboard switch key and direct access to settings from the keyboard toolbar.
- Diagnostic/test input screen for validating modifier keys, action keys, terminal combos, orientation layouts, and theme geometry.

### Layouts

- English QWERTY four-row phone layout.
- English QWERTY five-row PC-style layout.
- Compact five-row portrait layout.
- Landscape/tablet layout with wider modifier/navigation keys.
- Optional landscape numpad layout.
- Numpad toggle key that can be enabled, disabled, or assigned to an action slot.
- Symbol layer and shifted symbol layer.
- Terminal/navigation row containing Esc, Tab, Ctrl, Alt, arrow keys, slash, pipe, tilde, backtick, and common shell punctuation.
- Optional split or thumb-oriented tablet mode as a stretch goal.

### Key Behavior And Customization

- Tap, long press, repeat, popup alternatives, key preview, and swipe-up alternatives for selected keys.
- Sticky and lockable Shift/Ctrl/Alt.
- Ctrl combos for terminal and editor use, including Ctrl-C, Ctrl-D, Ctrl-L, Ctrl-A/E, Ctrl-K/U, and Ctrl-Alt variants where apps support them.
- Arrow-key repeat and optional selection mode with Shift+arrows.
- Enter key adapts to `EditorInfo.imeOptions` for Done, Go, Search, Send, Next, and Previous.
- Enable/disable optional keys such as Esc, Tab, Ctrl, Alt, arrows, microphone, settings, language switch, numpad toggle, and clipboard.
- Assign custom actions to preset toolbar/modifier/navigation slots.
- Password/private fields suppress suggestions, gesture decoding, speech input, previews where appropriate, and any learning.

### Themes

- Material 3 light and dark themes.
- Follow-system light/dark mode.
- Dynamic color on Android 12+.
- High-contrast theme.
- Terminal/dark theme.
- Alternate dark theme.
- Cyberpunk theme.
- 1337 green-on-black theme.
- Per-key visual states for pressed, locked modifier, sticky modifier, disabled/unavailable, and popup.
- Adjustable key border radius, border weight, gaps, margins, and row spacing with separate portrait and landscape values.

### Privacy And Permissions

- No network permission in the base app.
- No contacts permission in the base app.
- No microphone permission unless speech input is enabled.
- Speech input must run through local engine/model packs for the privacy-first path.
- Avoid reading or writing global user dictionary unless a later feature explicitly needs it and the user opts in.
- Local-only preferences.
- Clear privacy policy and Play data-safety declaration.

### Compatibility

- Test on Android 8, 9, 10, 11, 12, 13, 14, 15, and 16 where devices/emulators are available.
- Test phones, tablets, foldables, landscape, hardware-keyboard attached mode, and terminal apps such as Termux, ConnectBot-like SSH clients, and common text editors.
- Support inline autofill presentation on Android 11+ if a candidate/toolbar strip is part of MVP; otherwise explicitly defer and allow the platform fallback menu.

## Phase 2 Scope

- Offline speech-to-text spike and first implementation using optional local engine/model packs. Validate Vosk, whisper.cpp, and sherpa-onnx against F-Droid compatibility, APK/model size, latency, accuracy, language coverage, battery use, and licensing.
- Swipe typing spike and first implementation using local path capture, dictionary/model ranking, and no network dependency.
- Additional layouts: Dvorak, Colemak, QWERTZ, AZERTY, programmer Dvorak, Neo2, and user-defined JSON layouts.
- Language/layout packs, using Hacker's Keyboard layouts as a reference inventory.
- Import/conversion tooling for legacy XML layout definitions where license review allows.
- Candidate strip with simple punctuation shortcuts, clipboard actions, inline autofill support, and gesture/speech candidates.
- Optional local dictionary and autocorrect prototype.
- One-handed mode and split tablet mode.
- Configurable gesture shortcuts: spacebar cursor movement, delete-word gesture, selection mode, and quick symbol swipes.
- Expanded preset-slot editor for non-alphanumeric keys.
- Backup/restore of settings as a local file.

## Phase 3 Scope

- Full non-alphanumeric key position editor if preset-slot customization validates well. It must prevent overlapping keys, inaccessible critical actions, and layouts that break orientation changes.
- On-device prediction and autocorrect for selected languages.
- Dictionary pack SDK or documented package format.
- Production swipe typing engine for more languages, with documented dictionary/model format.
- Production speech pack ecosystem, including model installation/update UX and language management.
- Stylus handwriting support through Android's stylus handwriting APIs.
- Advanced accessibility mode for switch access, larger hit targets, and spoken feedback tuning.
- Optional F-Droid reproducible build pipeline.

## Reuse And Licensing

Hacker's Keyboard is Apache-2.0, so code reuse is possible with proper notices and attribution. The recommended path is still a clean implementation:

- Reuse product ideas and layout behavior as requirements.
- Audit layout XML/assets separately before importing any content.
- If copying code, isolate it behind clear modules and preserve license headers.
- Expect copied code to require modernization because it was written around old AOSP/Android APIs.

Offline speech, swipe typing, dictionaries, and theme/layout packs require separate license review. Prefer permissive or clearly F-Droid-compatible dependencies and models.

## Feature Validation Workflow

Each major feature phase should be validated independently before moving to the next one:

- Implement one feature slice at a time: custom keys, numpad, themes, speech, swipe typing, then advanced layout editing.
- Run emulator-based testing in a WorkerBee project environment after each slice once an Android scaffold exists.
- If validation is green, stage and commit that slice before starting the next slice.
- If validation fails, fix or revert the slice before continuing.
- Report progress, blockers, and validation results through Agent PBX while working.

## Key Risks

- IME trust and privacy: keyboards handle sensitive text, so even benign telemetry or broad permissions will hurt user trust and store review.
- Offline speech complexity: local STT adds microphone permission, model packaging, CPU/battery load, language-pack UX, and dependency/model licensing risk.
- Platform speech ambiguity: Android's offline preference flags may be ignored by recognizer implementations, so platform STT cannot satisfy the Google-free requirement by itself.
- Swipe typing complexity: useful glide typing needs dictionaries or models, path scoring, correction behavior, and sensitive-field suppression.
- Terminal compatibility: some terminal apps need raw key events while normal text fields prefer `InputConnection` edits. The action engine needs app-field-aware fallbacks.
- Touch accuracy: five rows, optional keys, and numpad modes create small targets on phones. Layout tuning and preview behavior need real-device testing.
- Custom layout safety: freeform non-alphanumeric positioning can create overlapping, inaccessible, or confusing layouts without strong validation.
- Popup and overlay behavior: upstream has known modern Android issues around popup keys, so this deserves an early spike.
- Internationalization scope: legacy language breadth is large and should not be bundled into MVP without owners and test coverage.
- Store policy drift: target SDK and data-safety requirements can change yearly. Confirm final requirements before release submission.
- Accessibility: custom key surfaces need deliberate TalkBack labels, focus behavior, touch exploration handling, and large-target modes.

## Open Decisions

- Minimum SDK: API 26, 29, or 31.
- Store strategy: Play only, F-Droid only, or both.
- License strategy: clean-room inspired implementation vs. Apache-2.0 code/layout import.
- Core UI technology: custom View for keyboard plus Compose settings is recommended; confirm before prototype.
- MVP language count: English only vs. a small set of high-demand layouts.
- Whether inline autofill is MVP or Phase 2.
- Whether local suggestions/autocorrect are in initial beta or deferred.
- Which local STT engine/model path should be the first supported speech pack.
- Whether swipe typing uses a custom model, adapted open-source engine, or dictionary/path heuristic for the first beta.
- How far non-alphanumeric key customization should go beyond preset slots.

## Suggested Milestones

1. Research spike, 1 week
   - Build a minimal `InputMethodService`.
   - Render a custom five-row QWERTY keyboard.
   - Verify Esc, Tab, Ctrl, Alt, arrows, delete repeat, and Enter actions in terminal and normal text fields.
   - Test popup/long-press behavior on Android 12+ and Android 15+.
   - Prototype optional key visibility, action-slot assignment, and landscape numpad switching.

2. MVP foundation, 4-6 weeks
   - Layout engine, action engine, customization engine, preferences, onboarding, settings, themes, and privacy posture.
   - English four-row/five-row layouts with compact, tablet, and landscape numpad variants.
   - Theme presets and per-orientation geometry controls.
   - Real-device QA loop for touch accuracy, modifier behavior, optional keys, and numpad toggle.

3. Speech and swipe feasibility, 2-4 weeks
   - Compare local STT candidates and select one first implementation path.
   - Prototype swipe path capture and local candidate ranking.
   - Validate both features in emulator/device testing without network or Google services dependencies.

4. Beta hardening, 3-5 weeks
   - Accessibility pass.
   - Inline autofill decision and implementation or explicit deferral.
   - Store metadata, privacy policy, signing, data-safety review, crash-free smoke tests, and release checklist.

5. Expansion, ongoing
   - Layout packs, dictionary work, advanced gestures, production speech packs, production swipe typing, stylus handwriting, freeform non-alphanumeric key editing, and broader terminal/editor compatibility.

## Release Prerequisites

Complete these items before starting GitHub release artifact automation or F-Droid submission prep:

- Custom theme v1: add one custom theme slot that can clone a current preset, persist local colors/opacities, and optionally render a user-selected keyboard background image.
- Settings UX: expose clone, color/opacity adjustment, background image picker/removal, and custom-theme reset controls without expanding into a full multi-theme library.
- Rendering QA: verify custom colors, key fill opacity, border opacity, label opacity, image opacity, and image fallback in portrait and landscape.
- Pad3 smoke test: install the debug APK on the OnePlus Pad3 over ADB, validate typing, glide suggestions, mic input, preset themes, custom theme, and orientation behavior.
- README visual: capture cyberpunk, 1337 green, light, and high-contrast themes from the Pad3 using a 45% height landscape keyboard, compose a 2x2 PNG, and place it under the README Highlights list.
- Release handoff: resume GitHub/F-Droid release preparation only after the milestone above has passed smoke testing and the working tree is committed.

## Source Notes

- Official Hacker's Keyboard repository and README: https://github.com/klausw/hackerskeyboard
- Hacker's Keyboard app Gradle file showing SDK/dependency baseline: https://github.com/klausw/hackerskeyboard/blob/master/app/build.gradle
- Hacker's Keyboard root Gradle file showing legacy build setup: https://github.com/klausw/hackerskeyboard/blob/master/build.gradle
- Hacker's Keyboard releases: https://github.com/klausw/hackerskeyboard/releases
- F-Droid package metadata: https://f-droid.org/en/packages/org.pocketworkstation.pckeyboard/
- Android IME creation guide: https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method
- Android `Keyboard` API deprecation: https://developer.android.com/reference/android/inputmethodservice/Keyboard
- Android `KeyboardView` API deprecation: https://developer.android.com/reference/android/inputmethodservice/KeyboardView
- Google Play target API requirement: https://developer.android.com/google/play/requirements/target-sdk
- Android inline autofill for IMEs: https://developer.android.com/identity/autofill/ime-autofill
- Android `EditorInfo` privacy/input flags: https://developer.android.com/reference/android/view/inputmethod/EditorInfo
- Android stylus handwriting IME APIs: https://developer.android.com/reference/android/inputmethodservice/InputMethodService
- Android `SpeechRecognizer`: https://developer.android.com/reference/android/speech/SpeechRecognizer
- Android `RecognitionService`: https://developer.android.com/reference/android/speech/RecognitionService
- Android offline speech preference caveat: https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_PREFER_OFFLINE
- Material 3 and dynamic color in Compose: https://developer.android.com/develop/ui/compose/designsystems/material3
- Vosk offline Android-capable STT candidate: https://github.com/alphacep/vosk-api
- whisper.cpp Android-capable STT candidate: https://github.com/ggml-org/whisper.cpp
- sherpa-onnx offline Android-capable STT candidate: https://github.com/k2-fsa/sherpa-onnx
- Nearby open-source reference projects: https://github.com/florisboard/florisboard, https://f-droid.org/en/packages/dev.patrickgold.florisboard/, and https://f-droid.org/packages/juloo.keyboard2/
