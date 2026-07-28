# Building MAME-Android from source

This document describes how to build the **MAME-Android** APK, including the
automated GitHub Actions pipeline.

> The repository ships the **Android wrapper + the MAME OSD layer (`src/`)** only.
> The actual MAME emulator core is downloaded at build time (see below).

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 17 | `sourceCompatibility = VERSION_17` |
| Android SDK | platform-36 / build-tools 36.0.0 | `compileSdk = 36`, `targetSdk = 36` |
| Android NDK | 28.2.13676358 | pinned in `app/build.gradle` (`ndkVersion`) |
| MAME core source | 0.288 | downloaded by the prepare script |
| Free disk | ~6 GB | MAME source + NDK + build outputs |
| RAM | >= 8 GB | MAME core is a large C++ compile |

Target ABI is `arm64-v8a` only (`minSdk 29`).

## Build steps

### 1. Prepare the MAME core source (download + patch + OSD overlay)

```bash
bash scripts/prepare_mame_src.sh 0.288
```

This will, into `android-MAME4droid/app/src/main/jni/MAME-src/`:

1. Download the MAME 0.288 source tarball from `mamedev/mame`.
2. Apply every patch under `src/` (`emu/`, `devices/`, `frontend/`, `osd/`).
3. Copy the `myosd` OSD implementation into `src/osd/myosd/`.

The directory is git-ignored (see `.gitignore`). Re-running skips the download
if `makefile` is already present.

### 2. Build the MAME core `.so`

```bash
export ANDROID_NDK_HOME="$ANDROID_SDK_ROOT/ndk/28.2.13676358"
bash scripts/build_mame_core.sh
```

The core is a large native compile. The script builds any `Android.mk` present
in the prepared source and copies the resulting `libmame*.so` into
`app/src/main/jniLibs/arm64-v8a/`.

> **Note:** the precise NDK build entry for the MAME *core* is project-specific
> and not committed in this snapshot. Wire your local core build command into
> `scripts/build_mame_core.sh` (the block marked *"MAME core uses its own
> makefile"*) so the core library is produced and packaged.

### 3. Build the APK

```bash
cd android-MAME4droid
./gradlew assembleDebug      # debug APK (signed with debug key)
# ./gradlew assembleRelease  # release APK (also signed with debug key here)
```

The APK lands in `app/build/outputs/apk/debug/` (or `release/`). The JNI shim
`libmame4droid-jni.so` is built by Gradle's `externalNativeBuild`; the MAME
core `libmame*.so` (from step 2) is packaged from `jniLibs/`.

## GitHub Actions (CI)

`.github/workflows/build.yml` automates the whole pipeline on every push to
`main`, on version tags (`v*`), and manually (`workflow_dispatch`):

1. Sets up JDK 17 + Android SDK (platform-36, build-tools) + NDK 28.2.13676358.
2. Runs `scripts/prepare_mame_src.sh`.
3. Runs `scripts/build_mame_core.sh`.
4. Runs `gradle assembleDebug`.
5. Uploads the APK as a build artifact.
6. On a tag push, attaches the APK to the corresponding GitHub Release.

To run it manually: **Actions → Build APK → Run workflow**.

### Tuning the CI

- **MAME version:** workflow `env.MAME_VERSION` (default `0.288`).
- **Core build:** if your core uses a custom `ndk-build` entry, edit
  `scripts/build_mame_core.sh` — the CI calls it verbatim.
- **Long builds:** the job `timeout-minutes` is 360; raise it if the core
  compile needs more time on GitHub-hosted runners.
- **Signing:** the release build type currently uses the debug signing config.
  For a properly signed release APK, add a signing key to repo secrets and
  configure `signingConfigs.release`.

## Verification checklist

- [ ] `prepare_mame_src.sh` downloads MAME 0.288 and all patches apply cleanly.
- [ ] `build_mame_core.sh` produces `libmame*.so` in `jniLibs/arm64-v8a/`.
- [ ] `assembleDebug` succeeds and the APK contains both `libmame4droid-jni.so`
      and the core `libmame*.so`.
- [ ] Sideloaded APK launches on Android TV (16:9) and an Xbox One controller
      auto-hides the on-screen virtual buttons.
