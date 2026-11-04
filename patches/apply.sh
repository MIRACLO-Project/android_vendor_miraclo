#!/bin/bash
# Applies MIRACLO's changes to Android repos that are not MIRACLO repos (run from the top of the tree).
cd "$(dirname "$0")/../../.."
P=vendor/miraclo/patches
git -C build/soong apply --check "$P/build_soong.patch" 2>/dev/null && git -C build/soong apply "$P/build_soong.patch" && echo "build/soong: applied"
for f in "$P"/*.patch; do
  b=$(basename "$f"); [ "$b" = build_soong.patch ] && continue
  n=${b%.commits.patch}; n=${n%.patch}; path=$(echo "$n" | tr '+' '/')
  [ -d "$path" ] || { echo "skip $b ($path not found)"; continue; }
  case "$b" in
    *.commits.patch) git -C "$path" am -q "$PWD/$f" && echo "$path: commits applied" || { git -C "$path" am --abort; echo "!! $path: commits did not apply"; } ;;
    *) git -C "$path" apply --check "$PWD/$f" 2>/dev/null && git -C "$path" apply "$PWD/$f" && echo "$path: applied" || echo "!! $path: did not apply (already applied?)" ;;
  esac
done
