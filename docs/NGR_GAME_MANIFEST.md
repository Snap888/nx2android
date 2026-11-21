# NGR game manifest v1

The catalog entry is the launcher-facing record. A manifest is the runtime-facing contract and must be versioned independently from both the game payload and shared runtime.

Example:

    {
      "manifest_version": 1,
      "game_id": "0100abcd12340000",
      "title": "Example title",
      "platform": "switch",
      "profile": { "id": "example-profile" },
      "payload": { "path": "/absolute/path/to/payload" },
      "runtime": {
        "id": "nx2android-legacy",
        "shared_runtime_ready": false
      },
      "launch": {
        "state": "legacy_payload",
        "settings": {
          "graphics": {},
          "input": {},
          "performance": {}
        }
      }
    }

## Fields

- `manifest_version`: required integer. Reject unknown major versions instead of guessing.
- `game_id`: stable NGR catalog ID, not necessarily the platform title ID.
- `platform`: source platform identifier (for example `switch`).
- `profile.id`: recompiler profile identifier/revision selector.
- `payload.path`: current local payload location. A later schema should use a relocatable payload URI and integrity hashes.
- `runtime.id`: runtime distribution/version identifier.
- `runtime.shared_runtime_ready`: explicit compatibility gate. Never infer readiness from the existence of an APK, .so, or data folder.
- `launch.state`: `registered`, `legacy_payload`, `ready`, or `blocked`.
- `launch.settings`: per-game overrides; global defaults remain in the application settings.

## Current compatibility truth

nx2android currently compiles `game_profile.h` and `android_profile.h` into a game-specific `libdmhost.so` and packages it in a game-specific NativeActivity APK. Its payload cannot yet be launched by a single generic host. The initial catalog therefore labels these entries `legacy_payload` and emits `shared_runtime_ready: false`. This is intentional: the launcher must never show a false-ready Play action.

## Migration requirements

Before setting `shared_runtime_ready` to true:

1. Move profile-specific constants and behavior behind a versioned runtime-loaded profile interface.
2. Audit every consumer of generated profile headers, including host, NVN, Android input and system-service shims.
3. Make the runtime validate profile ABI, payload hashes, required services and graphics features before launch.
4. Prove two different profiles can be loaded sequentially in one installed NGR app without state leakage.
5. Keep save data, writable files, shader caches and settings isolated by `game_id`.
