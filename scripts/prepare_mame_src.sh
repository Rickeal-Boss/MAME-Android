#!/usr/bin/env bash
#
# prepare_mame_src.sh — Download the MAME core source and apply the
# MAME4droid patches + the myosd OSD overlay shipped in this repo's `src/`.
#
# This is the *deterministic* part of the build: it does not compile anything,
# it only fetches MAME and applies the modifications so the NDK build can run.
#
# Usage:
#   bash scripts/prepare_mame_src.sh [MAME_VERSION]   # default 0.288
#
set -euo pipefail

MAME_VER="${1:-0.288}"
# MAME tags drop the dot: 0.288 -> mame0288
MAME_TAG="mame$(echo "$MAME_VER" | tr -d '.')"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST="$REPO_ROOT/android-MAME4droid/app/src/main/jni/MAME-src"
PATCH_ROOT="$REPO_ROOT/src"
SRC_TARBALL="/tmp/${MAME_TAG}.tar.gz"
SRC_URL="https://github.com/mamedev/mame/archive/refs/tags/${MAME_TAG}.tar.gz"

echo "==> Target MAME version: $MAME_VER (tag $MAME_TAG)"

# --- Download MAME source (skip if already prepared) -------------------------
if [ -f "$DEST/makefile" ] || [ -f "$DEST/makefile.mak" ]; then
  echo "==> MAME-src already present at $DEST — skipping download."
else
  mkdir -p "$DEST"
  echo "==> Downloading $SRC_URL"
  curl -fSL "$SRC_URL" -o "$SRC_TARBALL"
  echo "==> Extracting into $DEST"
  tar -xzf "$SRC_TARBALL" -C "$DEST" --strip-components=1
  rm -f "$SRC_TARBALL"
fi

# --- Apply patches -----------------------------------------------------------
# Every patch in src/<dir>/<file>.patch was generated from inside src/<dir>/,
# so its internal paths look like `a/<file>`. We cd into the matching
# MAME-src/src/<dir> and apply with `patch -p1`.
echo "==> Applying patches from $PATCH_ROOT"
find "$PATCH_ROOT" -name '*.patch' | sort | while read -r patch; do
  rel="${patch#"$PATCH_ROOT"/}"        # emu/machine.cpp.patch
  dir="$(dirname "$rel")"              # emu
  base="$(basename "$rel" .patch)"     # machine.cpp
  target_dir="$DEST/src/$dir"
  if [ ! -d "$target_dir" ]; then
    echo "   ! SKIP $rel (no dir $target_dir in MAME-src)"
    continue
  fi
  if [ ! -f "$target_dir/$base" ]; then
    echo "   ! SKIP $rel (no file $base in $target_dir)"
    continue
  fi
  echo "   * $rel -> src/$dir/$base"
  patch -p1 --forward -d "$target_dir" -i "$patch" \
    || echo "     (already applied or failed — continuing)"
done

# --- Overlay myosd OSD -------------------------------------------------------
echo "==> Overlaying myosd OSD into MAME-src"
mkdir -p "$DEST/src/osd/myosd"
cp -r "$PATCH_ROOT/osd/myosd/." "$DEST/src/osd/myosd/"

echo "==> Done. MAME source prepared at $DEST"
