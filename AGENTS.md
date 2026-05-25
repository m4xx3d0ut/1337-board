# Repository Guidelines

## Project Structure & Module Organization

This repository now contains the 1337 Board Android IME prototype. `SCOPE.md` remains the product and phasing source of truth. Android source lives in `app/src/main/java/org/leetboard/ime/`, with the IME service under `ime/`, core logic under `engine/` and `model/`, and UI surfaces under `ui/`. Android resources are in `app/src/main/res/`; IME metadata is in `app/src/main/res/xml/method.xml`. Unit tests live in `app/src/test/`.

Treat `.codex/`, `.android-sdk/`, `local.properties`, `.gradle/`, and `app/build/` as local generated state unless explicitly asked to change them.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` builds the debug APK.
- `./gradlew testDebugUnitTest` runs JVM unit tests.
- `./gradlew lintDebug` runs Android lint for the debug variant.
- `./gradlew assembleDebug testDebugUnitTest lintDebug` is the current local green gate.
- `scripts/setup-android-sdk.sh` installs an ignored local Android SDK when the host does not provide one.

The project expects Android SDK platform 35 and build tools 35.0.0. If no system SDK exists, use an ignored local SDK and point `local.properties` at it.

## Coding Style & Naming Conventions

Use Kotlin with four-space indentation and official Kotlin style. Classes and composables use `PascalCase`; functions, properties, and package names use `camelCase` or lowercase package segments. Keep Android package paths under `org.leetboard.ime`. Prefer small engine classes with explicit data models over ad hoc UI logic.

## Testing Guidelines

Add JVM tests in `app/src/test/` for layout selection, text-context privacy rules, key action behavior, and customization validation. Add instrumentation tests once emulator access is available. Sensitive input behavior must be covered before enabling speech or swipe typing.

## Commit & Pull Request Guidelines

There is no committed history yet. Use short, imperative commit messages such as `Add IME scaffold` or `Implement numpad toggle`. Pull requests should include a summary, linked scope item, screenshots or recordings for UI/IME behavior, and the exact Gradle/WorkerBee checks run.

## Agent-Specific Instructions

Preserve user changes and avoid unrelated cleanup. Report long-running implementation progress through Agent PBX. For feature slices, build, test, lint, and run emulator/WorkerBee validation when available before checkpoint commits.
