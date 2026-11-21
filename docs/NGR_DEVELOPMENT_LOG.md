# NGR development journal

## 2026-11-21 — Project kickoff

- Created branch feature/ngr-universal-launcher from main.
- Confirmed the base repository is the user's Snap888/nx2android fork.
- Read the repository README, architecture and the port pipeline's Options/inspect entry points.
- Confirmed the current app is a PySide6 desktop GUI, not an Android launcher. The headless Python porter builds a game-specific host and emits an APK plus a data pack.
- Confirmed the Android native runtime lives in nxnative/dm/android/; profile headers are generated and compiled into the host today.
- Recorded the target architecture and staged migration in docs/NGR_ARCHITECTURE.md.

## Current status

- [x] Create development branch.
- [x] Record product requirements from the supplied home-screen reference.
- [x] Document existing architecture constraints and target shared-runtime model.
- [ ] Audit exact GUI files and port pipeline CLI/progress/cancellation APIs.
- [ ] Audit generated game profile headers and all runtime consumers.
- [ ] Choose Android launcher UI implementation and persistent catalog storage.
- [ ] Define and test the versioned game manifest.
- [ ] Prototype dynamic profile loading without breaking legacy APK builds.
- [ ] Split runtime artifact from per-game payload.
- [ ] Connect Add Game UI to the real recompiler pipeline.
- [ ] Add CI and on-device acceptance tests.

## Next actions

1. Inspect gui/app.py, GUI screens and porter/cli.py; identify reusable workflow logic and JSON progress protocol.
2. Inspect nxnative/dm/android/build.py, profile generation and front_android.c; map compile-time profile dependencies.
3. Define a minimal manifest and library schema with migration/version rules.
4. Implement library/catalog and home screen only after confirming the Android app/module structure; do not replace the working native runtime or porter.
5. Keep legacy APK packaging as a regression path until shared runtime has passed device tests.

## Rules for this project

- Do not claim code is built or device-tested unless CI/device evidence exists.
- Keep commits small and use English Conventional Commit messages.
- Never commit user game files, keys, extracted content or copyrighted game assets.
- Do not remove legacy APK output until shared-runtime launch is proven.
- Report the exact branch, commit, changed files, tests and remaining blockers after each stage.
