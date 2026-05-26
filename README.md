# 1337 Board

![1337 Board keyboard preview](1337-board-hero.png)

1337 Board is a desktop-style Android keyboard for terminals, editors, tablets, and power-user text entry. It is inspired by the practical layout ideas in Hacker's Keyboard, but implemented as a modern Android IME with current APIs, custom rendering, and privacy-focused defaults.

## Highlights

- Five-row PC-style QWERTY layout with number row, Esc, Tab, Ctrl, Alt, Shift, Enter, Backspace, Delete, and arrow keys.
- Phone, compact, landscape, Fn, symbol, and numpad-oriented layouts.
- Configurable optional keys, preset action slots, key labels/icons, sticky modifiers, and key preview behavior.
- Local glide typing with bundled word list, ranked suggestions, optional imported word lists, and an opt-in local correction map.
- Mic input through Android speech recognition, with offline preference requested when supported by the device recognizer.
- Theme presets and a custom theme slot including light, dark, alternate dark, cyberpunk, terminal, high contrast, and 1337 green-on-black.
- Separate portrait and landscape controls for keyboard height, margins, gaps, borders, radius, and label styling.

![1337 Board theme grid captured on OnePlus Pad3](1337-board-theme-grid.png)

## Privacy

1337 Board is designed to avoid broad data access. The base app does not request network or contacts permissions. Preferences, customizations, imported glide words, and learned glide corrections stay local to the device. Microphone permission is requested only when mic input is enabled.

## Status

This project is an active prototype. Core keyboard entry, terminal keys, layout customization, themes, glide typing, speech input, and settings are available for testing, but the app should still be treated as pre-release software.

## Build And Install

Requirements:

- Android Studio or Android SDK command-line tools
- JDK 17
- USB debugging enabled for device testing

Build a debug APK:

```sh
./gradlew assembleDebug
```

Install on a connected device:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run unit tests:

```sh
./gradlew testDebugUnitTest
```

After installing, open 1337 Board, open input settings, enable the IME, then choose it from the keyboard picker.

## Project Direction

The near-term goal is a reliable, customizable Android keyboard for shell, code, remote access, and tablet workflows. Planned follow-up work includes stronger local speech packs, expanded layout packs, richer language support, improved autocorrect, and deeper non-alphanumeric key placement controls.
