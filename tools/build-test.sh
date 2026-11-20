#!/bin/bash
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# MIRACLO test build for the POCO F5: GApps, userdebug, signed with the MIRACLO release keys,
# delivered as the update zip (installs from recovery). Everything shows live in the terminal and
# is also saved to ~/miraclo-build.log. Run from the source root:
#
#   bash vendor/miraclo/tools/build-test.sh "<Pixel 7 factory image URL or zip>"
#
# The Pixel image is needed only the first time (Google Phone, Messages and Clock come from it);
# later runs reuse the prepared Google apps and can leave it out.
set -eo pipefail
[ -d .repo ] || { echo "Run this from the source root (where .repo is)."; exit 2; }
TOP=$(pwd); DEVICE=marble; D=device/xiaomi/marble; O=out/target/product/$DEVICE
PIXEL="${1:-}"
exec > >(tee ~/miraclo-build.log) 2>&1
step() { echo; echo "=== $* ($(date +%H:%M))"; }
stop() { echo; echo "STOPPED: $*"; exit 1; }

step "1/7 Signing keys"
[ -f vendor/miraclo-keys/releasekey.x509.pem ] && [ -f vendor/miraclo-keys/platform.x509.pem ] \
  || stop "the MIRACLO release keys are not in vendor/miraclo-keys (releasekey, platform, shared, media...)."
[ -f "$D/aurora/boot.img" ] || stop "the aurora kernel is not built (vendor/miraclo/kernel/aurora-lts.sh)."
echo "   keys and kernel in place"

step "2/7 Build patches (release-keys tag, camera classes)"
for p in vendor/miraclo/patches/*/*.patch; do
  d=$(basename "$(dirname "$p")" | tr _ /)
  if git -C "$d" apply --check "$TOP/$p" 2>/dev/null; then git -C "$d" apply "$TOP/$p"; echo "   applied: $d $(basename "$p")"
  elif git -C "$d" apply -R --check "$TOP/$p" 2>/dev/null; then echo "   already in: $d $(basename "$p")"
  else stop "$p does not apply to $d"; fi
done

step "3/7 Host tools"
set +u; source build/envsetup.sh >/dev/null; export WITH_GMS=true
lunch miraclo_${DEVICE}-cp2a-userdebug
m ota_extractor fsck.erofs aapt2 simg2img

step "4/7 Stock firmware, vendor images and performance libraries"
if [ -f "$D/stock/vendor.img" ] && [ -f "$D/stock/boot.img" ]; then echo "   already extracted"
else
  export STOCK_LOCAL_ZIP="$(ls ~/stock/marble_global-ota_full-*.zip 2>/dev/null | head -1)"
  [ -n "$STOCK_LOCAL_ZIP" ] || stop "no HyperOS update zip in ~/stock"
  echo "   from $STOCK_LOCAL_ZIP"
  bash "$D/fetch-stock.sh"
fi
bash vendor/miraclo/perf/setup-perf-libs.sh
PERF_MK=$D/stock/perf.mk; PERF_BP=$D/stock/Android.bp
: > "$PERF_MK"; rm -f "$PERF_BP"
for jar in QPerformance UxPerformance; do
  [ -f "$D/stock/system_ext/framework/$jar.jar" ] || continue
  printf 'dex_import {\n    name: "%s",\n    jars: ["system_ext/framework/%s.jar"],\n    system_ext_specific: true,\n}\n' "$jar" "$jar" >> "$PERF_BP"
  echo "PRODUCT_PACKAGES += $jar" >> "$PERF_MK"; echo "   $jar"
done

step "5/7 Google apps"
if grep -q "com.google.android.gmssystem" vendor/miraclo/gapps/mtg/mtg.mk 2>/dev/null; then echo "   already prepared"
else
  [ -n "$PIXEL" ] || stop "the Google apps are not prepared yet: run again with the Pixel 7 factory image URL."
  bash vendor/miraclo/gapps/prepare-gapps.sh "$PIXEL"
fi
grep -q "com.google.android.gmssystem" vendor/miraclo/gapps/mtg/mtg.mk || stop "Play services are missing from the Google apps."

step "6/7 Build (userdebug, GApps, release keys) - the long part"
[ "$(cat out/.miraclo-variant 2>/dev/null)" = vanilla ] && m installclean
m
echo gapps > out/.miraclo-variant

step "7/7 Update zip, signed with the release key"
if ! m otapackage; then
  echo "   (no otapackage target: target files, then the update zip)"
  m target-files-package
  TF=$(ls -t $O/obj/PACKAGING/target_files_intermediates/*/*.zip $O/*target_files*.zip 2>/dev/null | head -1)
  [ -n "$TF" ] || stop "no target files zip"
  ota_from_target_files -k vendor/miraclo-keys/releasekey "$TF" "$O/miraclo_${DEVICE}-ota.zip"
fi
OTA=$(ls -t $O/*-ota*.zip 2>/dev/null | grep -v target_files | head -1)
[ -n "$OTA" ] || stop "no update zip was made"
NAME=MIRACLO-1.1-Aurora-${DEVICE}-GApps-$(date +%Y%m%d-%H%M)-userdebug.zip
mkdir -p ~/release && cp "$OTA" ~/release/"$NAME"
( cd ~/release && sha256sum "$NAME" | tee "$NAME.sha256" )
grep -m1 "ro.build.tags=" $O/system/build.prop || true
grep -m1 "ro.build.version.security_patch=" $O/system/build.prop || true
echo
echo "DONE: ~/release/$NAME"
