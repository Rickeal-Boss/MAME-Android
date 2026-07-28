# Changelog

## v1.0.0-tv-xbox (2026-07-28)

Android TV (16:9) & Xbox One controller adaptation of MAME4droid-current.

### Features
- **Android TV 16:9 support**
  - Declare `android.software.leanback` (required=false) — single APK for phone + TV.
  - Declare `android.hardware.gamepad` / `android.hardware.touchscreen` (required=false).
  - `configChanges` adds `keyboard|keyboardHidden|navigation` to avoid Activity
    restart on gamepad hot-plug (per Android developer docs).
  - On Android TV: default overscan safe-area, landscape lock, hide-on-pad/stick.
- **Xbox One controller auto-hide virtual buttons**
  - `InputManager.InputDeviceListener` tracks gamepad/Xbox connection state
    (`refreshGamepadConnected()` scans `InputDevice.getDeviceIds()`).
  - Xbox name matching: Wireless / One / Bluetooth / Elite / Adaptive / Microsoft.
  - `InputHandler.isHideTouchController()` hides `InputView` when a gamepad is
    connected (`PREF_HIDE_ON_PAD`, default on).
  - Settings toggle under "External controller" + zh/en connect toasts.

### Files changed
- `AndroidManifest.xml` — leanback / gamepad / touchscreen features + configChanges
- `GameController.java` — connection-state tracking & Xbox detection
- `InputHandler.java` — auto-hide logic
- `MainHelper.java` — TV defaults & InputView visibility
- `PrefsHelper.java` — `PREF_HIDE_ON_PAD`
- `userpreferences.xml` — settings toggle
- `values/strings.xml`, `values-zh/strings.xml` — toasts
- `TV_XBOX_ADAPTATION.md` — adaptation docs

### Repository / build
- Added `scripts/prepare_mame_src.sh` (download MAME 0.288 + apply patches + myosd overlay).
- Added `scripts/build_mame_core.sh` (NDK core build + jniLibs copy).
- Added `.github/workflows/build.yml` (GitHub Actions APK build & release attach).
- Added `BUILD.md`, `CHANGELOG.md`, root `.gitignore`.
