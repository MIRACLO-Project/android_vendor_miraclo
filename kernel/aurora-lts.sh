#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# Run: bash vendor/miraclo/kernel/aurora-lts.sh   (about 30-60 minutes)
#
# The next aurora v1 test kernel: Google's official android12-5.10-lts branch (5.10.272), built
# the same way as the first test. Before any boot image is made, check-modules.py proves that
# every stock Xiaomi driver (vendor_boot and vendor_dlkm) will load on it. The first test image
# stays where it is; this one goes to ~/aurora-kernel/miraclo-out/lts/.
set -e
K=~/aurora-kernel
H=$(cd "$(dirname "$0")" && pwd)
OUT=$K/miraclo-out/lts
T=~/aurora
mkdir -p "$OUT"
W=$(mktemp -d)
trap 'rm -rf "$W"' EXIT

echo "== Stock Xiaomi drivers (from ~/stock/fw, read only)"
python3 "$T/system/tools/mkbootimg/unpack_bootimg.py" --boot_img ~/stock/fw/vendor_boot.img --out "$W/vb" >/dev/null
mkdir -p "$W/mods/vendor_boot" "$W/mods/vendor_dlkm"
for r in "$W"/vb/vendor_ramdisk*; do
  ( cd "$W/mods/vendor_boot" && (lz4 -dc "$r" 2>/dev/null || gzip -dc "$r" 2>/dev/null || cat "$r") | cpio -idm --quiet 2>/dev/null ) || true
done
fsck.erofs --extract="$W/mods/vendor_dlkm" ~/stock/fw/vendor_dlkm.img >/dev/null
echo "   $(find "$W/mods" -name '*.ko' | wc -l) module files"

cd "$K"
if ls out/*/dist/vmlinux.symvers >/dev/null 2>&1; then
  echo "== Check against the first test kernel (5.10.270, the one that booted)"
  python3 "$H/check-modules.py" out/*/dist/vmlinux.symvers "$W/mods" | tail -3 || true
fi

echo "== Source: android12-5.10-lts (android.googlesource.com, official)"
cd common
GD=$(git rev-parse --git-dir); rm -f "$GD"/index.lock "$GD"/shallow.lock
git reset -q --hard; git clean -qfd
git fetch -q --depth=1 https://android.googlesource.com/kernel/common android12-5.10-lts
git checkout -q FETCH_HEAD
echo "common: $(git log -1 --format='%h %cs %s')"
echo "version: $(make -s kernelversion)"
cd ..
echo "-aurora" > common/localversion-miraclo

echo "== aurora patches"
for p in "$H"/patches/*.patch; do
  ( cd common && patch -p1 --forward --no-backup-if-mismatch -s < "$p" ) || { echo "FAILED to apply $(basename "$p")"; exit 1; }
  echo "   applied $(basename "$p")"
done
if grep -q drm_atomic_check_valid_clones common/drivers/gpu/drm/drm_atomic_helper.c; then
  echo "STOPPED: the clone check is still in drm_atomic_helper.c"; exit 1
fi

echo "== Build (Google's GKI config, ThinLTO)"
rm -rf out
LTO=thin BUILD_CONFIG=common/build.config.gki.aarch64 SKIP_MRPROPER=1 build/build.sh > "$OUT/build.log" 2>&1 \
  || { tail -30 "$OUT/build.log"; echo "Build failed. Log: $OUT/build.log"; exit 1; }
DIST=$(ls -d out/*/dist | head -1)
strings "$DIST/Image" | grep -m1 "Linux version"

echo "== Do the stock drivers load on it?"
if ! python3 "$H/check-modules.py" "$DIST/vmlinux.symvers" "$W/mods" > "$OUT/check.txt"; then
  head -40 "$OUT/check.txt"; tail -1 "$OUT/check.txt"
  echo "STOPPED: some stock drivers would not load. No boot image made. Full list: $OUT/check.txt"
  exit 1
fi
tail -1 "$OUT/check.txt"

echo "== Test boot image (stock boot image, header v4, with this kernel)"
python3 "$T/system/tools/mkbootimg/unpack_bootimg.py" --boot_img ~/stock/fw/boot.img --out "$W/boot" --format=mkbootimg > "$W/args"
if file "$W/boot/kernel" | grep -qi gzip; then gzip -9c "$DIST/Image" > "$OUT/Image.aurora"
elif file "$W/boot/kernel" | grep -qi lz4; then lz4 -l -12 -f "$DIST/Image" "$OUT/Image.aurora" >/dev/null
else cp "$DIST/Image" "$OUT/Image.aurora"; fi
[ -f "$OUT/boot-aurora-test.img" ] && mv "$OUT/boot-aurora-test.img" "$OUT/boot-aurora-test-prev.img"
eval python3 "$T/system/tools/mkbootimg/mkbootimg.py" $(sed "s|$W/boot/kernel|$OUT/Image.aurora|" "$W/args") --output "$OUT/boot-aurora-test.img"
cp "$DIST/Image" "$DIST/vmlinux.symvers" "$OUT/"
sha256sum "$OUT/boot-aurora-test.img" | tee "$OUT/boot-aurora-test.img.sha256"
echo "Done. Test image: $OUT/boot-aurora-test.img"
