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
- **TV remote direction-pad dual-mode handling**
  - Root cause: TV remote D-pad events arrive with `SOURCE_DPAD` / `SOURCE_KEYBOARD`
    (not `SOURCE_GAMEPAD`/`SOURCE_JOYSTICK`), so `handleGameController()` dropped them.
  - `GameController.isTvRemoteDpad()` intercepts remote D-pad on Android TV (or explicit
    DPAD source), excluding real gamepads; `GameController.handleTvDpad()` dispatches
    per `PREF_TV_DPAD_MODE`:
    - Auto (default): mouse-pointer simulation in mouse games, else direct key navigation.
    - Mouse-pointer simulation: D-pad drives the emulated mouse cursor; OK = left click.
    - Direct key navigation: D-pad navigates the MAME OSD like a gamepad stick; OK in the
      frontend = confirm/launch (Enter/UI_SELECT), in-game = open options menu; Back = system back.
  - `InputHandler.isHideTouchController()` now always hides touch controls on Android TV
    (no-touch device), fixing the pure-remote scenario where no gamepad is present.
  - `PREF_TV_DPAD_MODE` preference (ListPreference) under "External controller", with en + zh
    title/summary and entry arrays (Auto / Mouse-pointer simulation / Direct key navigation).

### Files changed
- `AndroidManifest.xml` — leanback / gamepad / touchscreen features + configChanges
- `GameController.java` — connection-state tracking, Xbox detection, TV remote D-pad dual-mode
- `InputHandler.java` — auto-hide logic (TV) + TV remote D-pad dispatch hook
- `MainHelper.java` — TV defaults & InputView visibility (`PREF_TV_DPAD_MODE` init)
- `PrefsHelper.java` — `PREF_HIDE_ON_PAD`, `PREF_TV_DPAD_MODE`
- `userpreferences.xml` — settings toggles
- `values/strings.xml`, `values-zh/strings.xml` — toasts + TV D-pad strings
- `values/arrays.xml`, `values-zh/arrays.xml` — TV D-pad mode entry arrays
- `TV_XBOX_ADAPTATION.md` — adaptation docs

### Repository / build
- Added `scripts/prepare_mame_src.sh` (download MAME 0.288 + apply patches + myosd overlay).
- Added `scripts/build_mame_core.sh` (NDK core build + jniLibs copy).
- Added `.github/workflows/build.yml` (GitHub Actions APK build & release attach).
- Added `BUILD.md`, `CHANGELOG.md`, root `.gitignore`.
