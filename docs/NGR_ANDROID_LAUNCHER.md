# Building the NGR Android launcher

The new Android launcher is a separate Gradle application module. It does not replace the existing nx2android Python/PySide6 desktop porter or its per-game NativeActivity APK output.

## Local debug build

Requirements: JDK 17 and Android SDK with platform 35.

    gradle --no-daemon :app:assembleDebug

Output:

    app/build/outputs/apk/debug/app-debug.apk

The GitHub Actions workflow `.github/workflows/ngr-android-launcher.yml` builds the same target and uploads `NGR-Launcher-debug`.

## Current launcher scope

- Dark, cover-first three-column game library.
- Local persistent catalog, folder import and per-game settings.
- Per-game cover selection through Android's Storage Access Framework.
- Search and status filters.
- Add Game opens a Switch source-selection/inspection flow for NSP, XCI, NSZ and XCZ; selected source URIs can be kept in a pending queue.
- The app explicitly reports that the Android porter backend and shared native runtime are not connected yet. Imported folders and queued source files are not marked launch-ready.

## Not implemented yet

- Executing the Python porter pipeline inside Android.
- A remote/desktop worker protocol.
- Shared profile-loaded `libngr_runtime.so`.
- Real launch dispatch, saves integration and driver management.

Do not treat the current debug launcher as a playable emulator build. It is the first Android UI/catalog milestone.
