#!/usr/bin/env bash
#
# build_mame_core.sh — Build the MAME core shared library (.so) for Android
# and copy it into the app's jniLibs so Gradle can package it into the APK.
#
# IMPORTANT: The exact NDK build entry for the MAME *core* is project-specific.
# MAME4droid compiles the emulator core with its own Android.mk / makefile that
# lives alongside the prepared MAME source (see scripts/prepare_mame_src.sh).
# This script builds whatever Android.mk is present, then copies any produced
# libmame*.so into:
#   android-MAME4droid/app/src/main/jniLibs/arm64-v8a/
#
# If no core .so is produced, the APK built by Gradle will install but the
# emulator core will not load at runtime — supply your core build entry here.
#
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MAME_SRC="$REPO_ROOT/android-MAME4droid/app/src/main/jni/MAME-src"
JNI="$REPO_ROOT/android-MAME4droid/app/src/main/jni"
OUT="$REPO_ROOT/android-MAME4droid/app/src/main/jniLibs/arm64-v8a"

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
[ -n "$NDK" ] || { echo "ERROR: ANDROID_NDK_HOME / ANDROID_NDK_ROOT not set"; exit 1; }
[ -x "$NDK/ndk-build" ] || { echo "ERROR: ndk-build not found at $NDK/ndk-build"; exit 1; }
[ -d "$MAME_SRC" ] || { echo "ERROR: MAME-src not found — run prepare_mame_src.sh first"; exit 1; }

echo "==> Using NDK: $NDK"

# ndk-build expects NDK_PROJECT_PATH to be the project ROOT that contains a
# `jni/Android.mk`. The JNI shim's Android.mk lives at app/src/main/jni/Android.mk,
# so the project root is app/src/main (NOT app/src/main/jni).
APP_MAIN="$REPO_ROOT/android-MAME4droid/app/src/main"

# Build the JNI shim (Android.mk in app/src/main/jni).
if [ -f "$JNI/Android.mk" ]; then
  echo "==> ndk-build: JNI shim ($APP_MAIN)"
  ( cd "$APP_MAIN" && "$NDK/ndk-build" APP_ABI=arm64-v8a APP_PLATFORM=android-29 NDK_PROJECT_PATH="$APP_MAIN" )
fi

# Build the MAME core if a core Android.mk is present in the prepared source.
# NOTE: adjust the path / invocation to match your local MAME core build.
if [ -f "$MAME_SRC/jni/Android.mk" ]; then
  echo "==> ndk-build: MAME core ($MAME_SRC)"
  ( cd "$MAME_SRC" && "$NDK/ndk-build" APP_ABI=arm64-v8a APP_PLATFORM=android-29 NDK_PROJECT_PATH="$MAME_SRC" )
elif [ -f "$MAME_SRC/makefile" ]; then
  echo "==> MAME core uses its own makefile — invoke your core build command here."
  echo "    e.g. ( cd \"$MAME_SRC\" && <your ndk/make build command> )"
fi

# Collect produced core libraries into jniLibs.
mkdir -p "$OUT"
found=0
while IFS= read -r -d '' so; do
  echo "   * copy $so -> $OUT/"
  cp -v "$so" "$OUT/"
  found=1
done < <(find "$JNI" "$MAME_SRC" -name 'libmame*.so' -print0)

if [ "$found" -eq 0 ]; then
  echo "==> WARNING: no libmame*.so produced. The APK will build but the emulator"
  echo "   core will not load at runtime. Provide your core ndk-build entry above."
fi
echo "==> Core build step complete."
