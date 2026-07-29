# Changelog

## v1.0.1-review-fixes (2026-07-29)

Code-review-driven fixes (see `CODE_REVIEW_MAME_Android_part2.docx`). Grouped by source.

### Fork TV / gamepad fixes (high + medium)
- **1.1 (high) TV D-pad mouse drift** — `GameController.handleTvDpadMouse()` now tracks held
  directions in a `Set` and recomputes the pointer delta on every press/release instead of
  accumulating `+=` on each (repeated) `ACTION_DOWN`. Holding a direction no longer drifts forever.
- **1.2 (high) device-type constant clash** — `MainHelper.DEVICE_ANDROIDTV` reused value `5`,
  colliding with `DEVICE_AGAMEPAD2`, so on Android TV a `joy_key` device wrongly took the
  AGAMEPAD2 mapping branch. Reassigned `DEVICE_ANDROIDTV = 7` (unique).
- **1.3 (medium) connectivity data race** — `gamepadConnected` / `xboxConnected` are now
  `volatile`; the scattered `xboxConnected = true` write in `detectDevice()` is replaced by a
  call to the single canonical writer `refreshGamepadConnected()`, keeping write points consistent.
- **1.4 (medium) device-id pack overflow** — `getPersistentDeviceId()` fallback `idev.getId()`
  (which can exceed 65535) is now masked to 16 bits, and `makeKeyCodeWithDeviceID()` masks the
  device id before the `<< 16` pack, so descriptor-less controllers can't corrupt the packed keycode.
- **1.5 (low) options-menu keyboard entry** — `DialogHelper` now filters the virtual-keyboard
  item by content (`!sKeyboard.equals(it)`) instead of dropping the last array element.

### Upstream inherited fixes (stability)
- **2.1 (high) static Activity leak** — `MAME4droid.onDestroy()` now calls
  `Emulator.setMAME4droid(null)` to release the static Activity reference.
- **2.2 (high) main-thread sleeps** — `Emulator.initInput()` (100 ms) and `MainHelper.copyFiles()`
  (1000 ms) no longer `Thread.sleep()` on the UI thread; the work is deferred via
  `Handler.postDelayed` on the main looper (same thread, no block).
- **2.3 (high) HDR reset key names** — `GLNativeRenderer.restoreHDRDefaults()` tested the
  non-existent `PREF_BLOOM_BASE_NITS` / `PREF_BLOOM_MAX_NITS`; corrected to the real
  `PREF_VECTOR_EFFECT_BASE_NITS` / `PREF_VECTOR_EFFECT_MAX_NITS` so HDR defaults actually reset.
- **2.4–2.10 (medium) robustness** — `getROMsDIR() != ""` → `!isEmpty()`; `getFileName()` guards a
  `-1` column index; `checkNewViewIntent()` restores the `romName != null` guard; `MainHelper`
  key-mapping parse wrapped in try/catch; `WarnWidget.added` made `volatile`; `copyFiles()` zip
  extraction uses try-with-resources (no stream leak on error); `reload()` dead `if (true) return;`
  removed; `PrefsHelper.HIGHT` typo corrected (2 → 3, no longer collides with `NORMAL`); all
  `PrefsHelper` integer preference reads go through a new `safeInt()` helper (corrupt prefs can't crash).

### Netplay (fork enhancement)
- **3.x** — `NetPlayHelper.createGame()` now null-guards `netplayGetPublicAddr()` (joinGame already
  did). The per-second `netplayInit` call inside the join wait-loop was reviewed: left as-is
  pending confirmation of native-side join-poll semantics (avoids changing join behavior blindly).

### Files changed
- `GameController.java` — 1.1 mouse drift, 1.3 volatile + write convergence, 1.4 id pack mask
- `MainHelper.java` — 1.2 device constant, 2.2 sleep→postDelayed, 2.4/2.5/2.6/2.7/2.9/2.10 hardening
- `DialogHelper.java` — 1.5 content-based keyboard-item filter
- `MAME4droid.java` — 2.1 null the static Emulator ref in onDestroy
- `Emulator.java` — 2.2 sleep→postDelayed
- `GLNativeRenderer.java` — 2.3 correct HDR reset key names
- `WarnWidget.java` — 2.8 volatile `added`
- `PrefsHelper.java` — 2.10 HIGHT typo + 2.7 `safeInt()` hardening
- `NetPlayHelper.java` — 3.x null-guard public addr

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
- **USB / Xbox physical multi-controller (1–4 players)**
  - Root cause: `onInputDeviceAdded()` only refreshed the connect toast and bound a
    player slot on the *first* key/axis event, so plugging 2–4 Xbox/USB pads into a TV
    before launch left them all unassigned until each was poked.
  - `GameController.onInputDeviceAdded()` now eagerly calls `checkAndRegisterDevice(dev)`
    on the UI thread the instant a gamepad (GAMEPAD / JOYSTICK source) is attached, so each
    pad is bound to the first free slot (P1–P4) and announced ("Detected XBox controller as P2")
    without requiring any key press — matches the Android Developers multi-controller pattern.
  - `GameController.handleGameController()` Dynamic Bridge fallback now maps the Xbox Guide
    button (`KEYCODE_BUTTON_MODE`, the center button) to the MAME options menu (`OPTION_VALUE`)
    when delivered.
  - Disconnect still frees the slot (`onInputDeviceRemoved`) and shows "Disconnected controller (Pn)";
    `MAX_DEVICES = 4` caps at 4 players with "Unassigned (Max 4)" beyond that.

### Build / CI fixes
- `scripts/build_mame_core.sh`: fixed `NDK_PROJECT_PATH` (was pointing one `jni/`
  too deep, causing `jni/jni/Android.mk` "unknown file") so the JNI shim builds.
- `.github/workflows/build.yml`: use `build-root-directory` + `gradle-version: 8.9`
  (repo ships no Gradle wrapper) so `assembleDebug` actually runs in CI.
- `gradle.properties`: removed `-XX:MaxPermSize=512m` (invalid on JDK 17) which
  prevented the Gradle daemon from starting. `assembleDebug` now builds successfully;
  the full MAME core `.so` still requires a local/core build entry (see `BUILD.md`).

### Files changed
- `AndroidManifest.xml` — leanback / gamepad / touchscreen features + configChanges
- `GameController.java` — connection-state tracking, Xbox detection, TV remote D-pad dual-mode, multi-controller hot-plug (1–4 players) + Xbox Guide button map
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
