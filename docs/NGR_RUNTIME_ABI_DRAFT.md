# NGR runtime-loaded game profile ABI — design draft

This is a design contract, not an implemented loader. Do not set any game manifest to shared-runtime-ready until the migration and device acceptance gates are met.

## 1. What is compiled into each current game host

The current generator is `nxnative/dm/games/game.py:header()`. It emits `game_profile.h`, included by the native host build. The Android profile generator is `nxnative/dm/android/tools/android_profile.py:header()`; it emits `android_profile.h`.

The profile data currently compiled into the host includes:

- Identity and module selection: `DM_GAME_ID`, `DM_GAME_NAME`, `DM_GAME_PROGRAM_ID`, `DM_GAME_MODULES`, `DM_GAME_NMOD`, `DM_GAME_ROMFS_MARKER`.
- Memory contract: `DM_GAME_MEM_RESERVE`, `DM_GAME_MEM_ALIAS`, `DM_GAME_MEM_STACK`, `DM_GAME_APP_POOL`, `DM_GAME_MEM_USED`, `DM_GAME_SYS_RESOURCE`.
- Startup configuration: `DM_GAME_ARGS`, `DM_GAME_ENV`, `DM_GAME_DISPLAY_VERSIONS`, `DM_GAME_PRESENT_DISPLAY`.
- HLE/system behavior: `DM_GAME_MEM_MAP_LIMIT`, `DM_GAME_SOCK_LOOPBACK`, `DM_GAME_SMALL_VPOOL_SPARSE`, `DM_GAME_CORE_PREEMPT`, `DM_GAME_ALLOC_BIG_ALIGN`.
- Frame pacing: `DM_GAME_FPS_TARGET`, `DM_GAME_FPS_CEILING`, `DM_GAME_FPS_HOLD`.
- Font and input: optional `DM_GAME_SHARED_FONT_TTF`, `DM_GAME_PAD_LAYOUT`, `DM_GAME_OPMODE`, `DM_GAME_PERF_BOOST`.
- Graphics workarounds: `DM_GAME_FEEDBACK_SNAP`, `DM_GAME_ALPHA_FROM_RED`, `DM_GAME_TEX_FLUSH_UPLOAD`, `DM_GAME_RENDER_COND`, `DM_GAME_TEX_DIRTY_UPLOAD`, `DM_GAME_TEX_DYN_SLOTS`, `DM_GAME_TEX_CPU_UNCACHED`, `DM_GAME_GPU_TS_SEGMENT`, `DM_GAME_VCONV_RANGE`, `DM_GAME_ORDER_KEEP`.
- Optional profile tables: `DM_GAME_FPS_LOCKS` and `DM_GAME_UE_MALLOC` (plus their generated counts).
- Optional NVN API override: `DM_GAME_NVN_API_MAJOR`, `DM_GAME_NVN_API_MINOR`.

The Android profile header separately embeds `DM_ANDROID_STATUS`, `DM_AOV_N`, `DM_AOV_TABLET` and `DM_AOV_PHONE`. These describe profile status and the gamepad overlay layout.

The host startup consumes at least the compile-time default module list and default game arguments in `nxnative/dm/host/main.c`: explicit module/argument command-line values override the defaults; absent module arguments fall back to `DM_GAME_MODULES`, and absent game arguments use `DM_GAME_ARGS`. This behavior must remain identical after the refactor.

## 2. Target payload layout

Each library entry has a relocatable root, for example:

    games/<game-id>/
      manifest.json
      profile/
        profile.json
        android-overlay.json
      modules/
        main
        sdk
        ...
      data/
        RomFS/
        shaders/
        ...
      saves/
      cache/

The actual payload may use the current data-pack layout during migration. The manifest must not embed an absolute build-machine path.

## 3. Versioned profile document

The profile document is JSON (or a compact binary encoding generated from the same schema) with:

- `profile_schema_version`
- `profile_id`, `title_id`, `display_name`
- `modules[]`, `romfs_marker`
- `memory`: reserve, alias, stack, app pool, used memory, system resource
- `startup`: default args, environment map, display-version map, present policy
- `system`: memory map limit, loopback sockets, sparse small virtual pools, core preemption, big-allocation alignment
- `frame_pacing`: target, ceiling, hold
- `input`: pad layout, operation mode, performance mode
- `graphics_workarounds`: the per-profile flags and shader-key lists above
- `tables`: typed FPS-lock and UE allocator rows
- `nvn_api`: optional major/minor override
- `android_overlay`: status and tablet/phone layout entries

Values must be parsed and range-validated before the host initializes. Unknown required fields or unsupported schema versions block launch; unknown optional fields may be ignored only when explicitly marked optional.

## 4. Native ABI proposal

Expose a narrow C ABI from the shared runtime:

- `ngr_runtime_abi_version()`
- `ngr_runtime_validate_manifest(manifest_fd, error_buffer, error_capacity)`
- `ngr_runtime_load_profile(manifest_fd, profile_fd, game_root_fd, error_buffer, error_capacity)`
- `ngr_runtime_launch(argc, argv)`
- `ngr_runtime_request_stop()`
- `ngr_runtime_unload_game()`

The Java launcher passes file descriptors opened from app-private files (not raw content-provider URIs). The runtime duplicates descriptors it needs and owns their lifetime according to a documented contract. Do not expose C structs across the JNI boundary until their size, alignment and version negotiation are fixed.

The runtime must not retain profile pointers after unload. Any mutable caches, environment overrides, module lists, overlay tables and game-specific global state must be reset between game sessions. If a game is launched in its own process, process teardown is an acceptable isolation boundary for the first implementation; same-process multi-game switching is a later requirement.

## 5. Migration sequence

1. Add a profile serializer beside `game.py:header()` and `android_profile.py:header()`; compare its output against generated headers for every profile.
2. Introduce a runtime profile context and convert one low-risk field at a time, preserving the existing macro path as a legacy build option.
3. Convert module selection and default arguments, then memory/system fields, then rendering/input workarounds and generated tables.
4. Remove the game-specific header from the shared-host build only after all references have a runtime-context equivalent.
5. Build one shared `libngr_runtime.so`; keep game modules, profile data and data pack per game.
6. Launch two known profiles from one installed NGR APK and verify module loading, HLE, graphics, gamepad, saves and settings isolation.

## 6. Hard gates

- No per-game profile header required by the shared runtime build.
- Every profile field is serialized, validated and covered by tests.
- Legacy per-game APK builds still pass unchanged during migration.
- Runtime rejects mismatched ABI/schema and corrupt payloads with a readable diagnostic.
- A/B test at least two different games on device; no cross-game state leakage.
- Only then may manifest `runtime.shared_runtime_ready` become true.
