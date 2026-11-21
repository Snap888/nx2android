# NGR: Universal Game Launcher and Recompiler

## Goal

Turn nx2android into the Switch-game backend of NGR, a single Android application with a persistent game library. A successful port adds or updates a library entry and its runtime payload; it must not create and install a separate Android application for every game.

The repository's current pipeline produces a game-specific APK and data pack. Its Android host is compiled with a generated game profile, so removing the APK-per-game output is an architectural change, not just a UI change.

## Product shape

- **Library (home):** dark, cover-first 3-column grid inspired by the supplied reference screenshot; title below each cover; search, sort, filter, settings and user/profile actions in the top bar.
- **Add game (+):** opens the in-app Recompiler flow, beginning with source selection and game inspection, then profile/support status, options, progress and result.
- **Import (folder):** imports an already prepared game payload or supported source folder without confusing import with recompilation.
- **Game details/settings:** launch, edit per-game graphics/runtime/input options, manage data and saves, view logs, remove a library entry without deleting user data unless explicitly requested.
- **One installed NGR app:** one launcher package and a shared runtime; games are data/code payloads addressed by stable IDs and versions.

## Existing nx2android facts

- gui/ is a PySide6 desktop UI; it runs the headless python -m porter pipeline.
- porter/ owns inspection, extraction, rewrite, shader translation, host build, APK/data-pack packaging and optional adb install.
- nxnative/dm/android/ contains the Android Vulkan runtime and NativeActivity front end.
- The current pipeline compiles the game's profile into the host (game_profile.h and android_profile.h) and emits a per-game APK. Therefore, a shared launcher cannot safely launch arbitrary current output APKs as if they were library entries.
- The port pipeline depends on desktop-side Python, .NET, Android NDK and (for some workflows) adb. The current GUI is not an Android in-app recompiler.

## Target architecture

    NGR Android app
      ├─ Library UI / search / filters / cover metadata
      ├─ Game catalog (Room/SQLite; stable game ID, title, source, payload, profile, version)
      ├─ Game details and per-game settings
      ├─ Recompiler UI (inspect → validate → build → install payload → register)
      ├─ Runtime manager (shared runtime version, drivers, shader/cache data)
      └─ Launch dispatcher
           └─ Shared NGR runtime
                ├─ load game manifest + profile
                ├─ mount game payload / user data / saves
                └─ native host + Vulkan/NVN backend

### Game library record

Each entry should have a stable internal UUID and include: display title, title ID/platform, cover URI or local cover path, source format, source fingerprint, payload directory, profile ID and revision, runtime compatibility range, game version, last launch, play state, and per-game settings. Do not store copyrighted game data, keys, or extracted game assets in the repository.

### Runtime contract

Define a versioned manifest for each ported game. It references the game payload and profile data, declares required runtime ABI/features and optional driver requirements, and stores launch arguments/environment overrides. The shared runtime validates the manifest before launch and reports actionable compatibility errors. Runtime and game payload updates must be independently versioned.

### Recompiler contract

Refactor the pipeline output into:
1. a shared runtime build/distribution artifact, built once;
2. a game payload (native modules, shader data, required game files and metadata);
3. a profile/manifest consumed by the shared runtime;
4. a registration result for the NGR library.

The existing per-game APK path remains available behind a compatibility mode until the shared-runtime path has equivalent device coverage. Do not silently break existing nx2android ports.

### Important engineering constraint

The current engine compiles profile data into the host. Before a shared runtime can launch multiple games, profile-dependent data must be moved behind a runtime-loaded, versioned interface or another proven mechanism. This needs an audit of every generated profile header and every runtime consumer; merely placing old APKs in a grid does not satisfy the goal.

### Recompiler execution on Android

The current recompiler is a desktop Python/PySide6 application and its toolchain is designed around desktop NDK/.NET/adb. The Android Add Game screen can be designed now, but a fully on-device recompiler requires a separate feasibility and dependency audit (ARM64 host tools, Android filesystem/process restrictions, .NET availability, storage permissions, memory and thermal limits). Until that is proven, expose a clear backend status and do not claim a port is running when it is not. A remote/desktop worker may be an optional fallback, not a hidden requirement.

## Delivery phases

1. **Repository and architecture audit:** map UI entry points, pipeline stages, generated profile headers, APK packaging, runtime startup and all profile consumers.
2. **Library foundation:** persistent catalog, game model/manifest, cover handling, import/registration APIs and migration-safe storage.
3. **NGR launcher UI:** library grid matching the reference, details/settings screen, search/filter/sort, Add Game entry.
4. **Shared runtime design:** runtime ABI and dynamic profile loading; keep legacy APK mode working.
5. **Payload pipeline:** split shared runtime from game-specific payload and register results directly in the library.
6. **Recompiler integration:** connect the UI to the real pipeline with streamed progress, cancellation, diagnostics and retry.
7. **Device validation:** install one NGR APK, add two different supported games, launch both from the same app, verify settings/save isolation and update behavior.

## Acceptance criteria

- One NGR APK is installed for all supported games.
- At least two distinct game entries coexist in the library and launch through the same runtime.
- Adding a game invokes the actual recompiler workflow and registers the result only after a successful build.
- Reopening NGR preserves entries and per-game settings.
- A failed/cancelled build does not create a broken library shortcut.
- Each game has isolated saves, writable data and settings.
- Existing per-game APK output remains usable during migration.
- CI builds and tests the launcher/runtime and the recompiler pipeline separately.
