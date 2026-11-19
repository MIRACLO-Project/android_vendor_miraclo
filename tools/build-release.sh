#!/bin/bash
#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# MIRACLO full release build (userdebug, MIRACLO keys). Run on the SERVER, in tmux:
#
#   bash vendor/miraclo/tools/build-release.sh "<Pixel 7 (panther) Android 17 factory image URL or zip>"
#   VARIANT=vanilla bash vendor/miraclo/tools/build-release.sh      (no Google apps)
#
# 1. Host tools.
# 2. Stock firmware from Xiaomi's update: bootloader, modem, DSP, TrustZone... (all of it),
#    the stock vendor images, and Qualcomm's performance libraries.
# 3. Google apps from the Pixel 7 image (its Chrome and WebView carry 32- and 64-bit code,
#    which this phone's 32-bit apps need; the Pixel 8's are 64-bit only).
# 4. Full build, then the update package (OTA).
# 5. The release: a fastboot package (every image + firmware + flash scripts for Linux and
#    Windows), the OTA, checksums and notes, in ~/release/.
set -euo pipefail

VARIANT="${VARIANT:-gapps}"
case "${VARIANT}" in
  gapps)   export WITH_GMS=true;  LABEL=GApps ;;
  vanilla) export WITH_GMS=false; LABEL=Vanilla ;;
  *) echo "VARIANT must be gapps or vanilla" >&2; exit 1 ;;
esac
GAPPS_SRC="${1:-}"
if [ "${VARIANT}" = gapps ] && [ -z "${RESUME:-}" ] && [ -z "${GAPPS_SRC}" ]; then
  echo "usage: $0 <Pixel 7 (panther) Android 17 factory image URL or zip>   (or VARIANT=vanilla)" >&2; exit 1
fi

TOP=~/aurora
cd "${TOP}"
LOG=~/release-build.log
: > "${LOG}"
step() { echo; echo "== $*" | tee -a "${LOG}"; }
run() { "$@" >> "${LOG}" 2>&1 || { echo "FAILED: $* (last lines of ${LOG}):"; tail -40 "${LOG}"; exit 1; }; }

[ -f vendor/miraclo-keys/releasekey.x509.pem ] || { echo "MIRACLO keys missing (vendor/miraclo-keys)"; exit 1; }
DEVICE="${DEVICE:-marble}"
DEVICE_DIR="${DEVICE_DIR:-$(ls -d device/*/"${DEVICE}" 2>/dev/null | head -1)}"
[ -n "${DEVICE_DIR}" ] || { echo "No device tree for ${DEVICE} under device/"; exit 1; }
if [ "${DEVICE}" = marble ]; then DEVICE_NAME="${DEVICE_NAME:-POCO F5}"; else DEVICE_NAME="${DEVICE_NAME:-${DEVICE}}"; fi
if [ "${DEVICE}" = marble ]; then
    [ -f "${DEVICE_DIR}/aurora/boot.img" ] || { echo "aurora kernel missing: run use-aurora.sh first"; exit 1; }
fi

step "1/6 Environment and host tools"
set +u
source build/envsetup.sh > /dev/null
lunch miraclo_${DEVICE}-cp2a-userdebug > /dev/null
set -u
run m ota_extractor fsck.erofs aapt2 simg2img

if [ -z "${RESUME:-}" ]; then
step "2/6 Stock firmware and vendor images"
LOCAL_ZIP="$(ls "$HOME"/stock/marble_global-ota_full-*.zip 2>/dev/null | head -1 || true)"
export STOCK_LOCAL_ZIP="${LOCAL_ZIP}"
run ${DEVICE_DIR}/fetch-stock.sh
run vendor/miraclo/perf/setup-perf-libs.sh
run vendor/miraclo/apps/fetch-apps.sh
ls ${DEVICE_DIR}/stock/firmware/*.img | xargs -n1 basename | tr '\n' ' ' | sed 's/^/   firmware: /'; echo

# Qualcomm performance libraries: installed where this framework looks for them.
PERF_MK=${DEVICE_DIR}/stock/perf.mk
PERF_BP=${DEVICE_DIR}/stock/Android.bp
: > "${PERF_MK}"; rm -f "${PERF_BP}"
for jar in QPerformance UxPerformance; do
    src="${DEVICE_DIR}/stock/system_ext/framework/${jar}.jar"
    [ -f "${src}" ] || { echo "   ${jar}: not in this firmware"; continue; }
    want="$(grep -rhoE "\"/[a-z_]+/framework/${jar}\.jar\"" frameworks/base/core/java 2>/dev/null | head -1 | tr -d '"' || true)"
    part="system_ext"; [ "${want#/system/}" != "${want}" ] && part="system"
    {
        echo "dex_import {"
        echo "    name: \"${jar}\","
        echo "    jars: [\"system_ext/framework/${jar}.jar\"],"
        [ "${part}" = "system_ext" ] && echo "    system_ext_specific: true,"
        echo "}"
    } >> "${PERF_BP}"
    echo "PRODUCT_PACKAGES += ${jar}" >> "${PERF_MK}"
    echo "   ${jar}: installed in /${part}/framework (framework looks in: ${want:-not referenced})"
done

if [ "${VARIANT}" = gapps ]; then
step "3/6 Google apps (Pixel 7 image)"
run vendor/miraclo/gapps/prepare-gapps.sh "${GAPPS_SRC}"
grep -E "^\s+MiraGms_" vendor/miraclo/gapps/gapps.mk | sed 's/ \\$//; s/^ */   /' | head -80
grep -q "com.google.android.gmssystem" vendor/miraclo/gapps/mtg/mtg.mk || { echo "Play services missing: stopping."; exit 1; }
else
step "3/6 Vanilla: no Google apps"
fi

fi
step "4/6 Full build: ${LABEL} (this takes a while)"
# The other variant was built last: clear its installed files, so none of them is packaged.
STAMP=out/.miraclo-variant
if [ -f "${STAMP}" ] && [ "$(cat "${STAMP}")" != "${VARIANT}" ]; then run m installclean; fi
run m
echo "${VARIANT}" > "${STAMP}"
step "4b/6 Preflight checks (stops here if the build would not boot cleanly)"
if ! python3 ~/preflight.py out/target/product/${DEVICE} --expect \
        "system_ext/lib64/libimsmedia_jni.so" \
        "system_ext/lib64/libimscamera_jni.so" \
        $([ "${VARIANT}" = gapps ] && echo "product/etc/permissions/privapp-permissions-miraclo-gapps.xml::com.android.deskclock") \
        "system/app/webview/webview.apk|product/app/webview/webview.apk|system_ext/app/webview/webview.apk" \
        "system/build.prop::persist.sys.disable_rescue=true" \
        "system_ext/priv-app/MiuiCamera/MiuiCamera.apk" \
        "system_ext/etc/permissions/privapp-permissions-miuicamera.xml::com.android.camera" \
        "system_ext/lib64/libcamera_algoup_jni.xiaomi.so" \
        2>&1 | tee -a "${LOG}"; then
    echo "Preflight failed: nothing was packaged. Send me the lines above."
    exit 1
fi
step "5/6 Update package (OTA)"
if [ -n "${SKIP_OTA:-}" ]; then echo "   skipped (SKIP_OTA)"; else run bash ~/ota-step.sh; fi

step "6/6 Packaging the release"
O=out/target/product/${DEVICE}
VER="$(grep -m1 '^ro.miraclo.version=' ${O}/system/build.prop ${O}/system_ext/etc/build.prop 2>/dev/null | head -1 | cut -d= -f2 || true)"
[ -n "${VER}" ] || VER="1.0"
NAME="MIRACLO-${VER}-Aurora-${DEVICE}-${LABEL}-$(date +%Y%m%d)-userdebug"
R=~/release/${NAME}
rm -rf "${R}"; mkdir -p "${R}/images" "${R}/firmware"

for p in boot dtbo vendor_boot vbmeta vbmeta_system; do
    cp "${O}/${p}.img" "${R}/images/"
done
# One super image (system, system_ext, product, vendor, odm, vendor_dlkm), the layout taken from
# the build's own misc_info.txt. It is flashed from the bootloader's fastboot mode, so the
# install never depends on fastbootd or on a recovery.
MI="$(ls -t ${O}/misc_info.txt ${O}/obj/PACKAGING/target_files_intermediates/*/META/misc_info.txt 2>/dev/null | head -1 || true)"
[ -n "${MI}" ] || { echo "FAILED: no misc_info.txt for the super image"; exit 1; }
SD="${R}/super.dict"
grep -E '^(super_|ab_update|virtual_ab|use_dynamic_partitions|dynamic_partition_retrofit)' "${MI}" > "${SD}"
grep -q '^super_metadata_device=' "${SD}" || echo "super_metadata_device=super" >> "${SD}"
echo "lpmake=${TOP}/out/host/linux-x86/bin/lpmake" >> "${SD}"
for p in $(grep '_partition_list=' "${SD}" | cut -d= -f2); do
    [ -f "${O}/${p}.img" ] || { echo "FAILED: ${O}/${p}.img missing for the super image"; exit 1; }
    echo "${p}_image=${TOP}/${O}/${p}.img" >> "${SD}"
done
run python3 build/make/tools/releasetools/build_super_image.py "${SD}" "${R}/images/super.img"
rm -f "${SD}"
ls ${DEVICE_DIR}/stock/firmware/*.img >/dev/null 2>&1 && cp ${DEVICE_DIR}/stock/firmware/*.img "${R}/firmware/"
OTA="$(ls -t ${O}/*-ota*.zip 2>/dev/null | grep -v target_files | head -1 || true)"
[ -n "${OTA}" ] && cp "${OTA}" "${R}/${NAME}-ota.zip"

FW="$(cd "${R}/firmware" && ls *.img | sed 's/\.img$//' | tr '\n' ' ')"
cat > "${R}/flash_all.sh" <<EOF
#!/bin/bash
# MIRACLO for the ${DEVICE_NAME} (${DEVICE}). Flash from a computer (Linux or macOS) with the phone in
# fastboot mode (Power + Volume down), bootloader unlocked. Keeps your data unless you choose
# to wipe it (needed the first time, coming from another ROM).
set -e
cd "\$(dirname "\$0")"
FB=\${FASTBOOT:-\$(command -v fastboot || echo ~/platform-tools/fastboot)}
\$FB getvar product 2>&1 | grep -q ${DEVICE} || { echo "This is not a ${DEVICE_NAME} (${DEVICE}). Stopping."; exit 1; }
read -p "Wipe all data too? Needed the first time (y/N): " w
echo "Firmware..."
for p in ${FW}; do for s in a b; do \$FB flash \${p}_\$s firmware/\$p.img; done; done
echo "Boot images..."
for p in boot dtbo vendor_boot vbmeta vbmeta_system; do for s in a b; do \$FB flash \${p}_\$s images/\$p.img; done; done
echo "System..."
\$FB flash super images/super.img
\$FB set_active a
if [ "\$w" = "y" ] || [ "\$w" = "Y" ]; then \$FB erase userdata; \$FB erase metadata; fi
\$FB reboot
echo "Done. The first start takes a few minutes."
EOF
chmod +x "${R}/flash_all.sh"
grep -v -e '^echo "Firmware...' -e 'firmware/' "${R}/flash_all.sh" \
    | sed '4a # This one keeps the firmware already on the phone (use it when yours is newer).' \
    > "${R}/flash_keep_firmware.sh"
chmod +x "${R}/flash_keep_firmware.sh"

{
    echo "@echo off"
    echo "rem MIRACLO for the ${DEVICE_NAME} (${DEVICE}). Phone in fastboot mode, bootloader unlocked."
    echo "cd /d %~dp0"
    echo "set FB=fastboot"
    echo "%FB% getvar product 2>&1 | findstr /c:\"${DEVICE}\" >nul || (echo This is not a ${DEVICE_NAME} ^(${DEVICE}^). & pause & exit /b 1)"
    echo "set /p W=Wipe all data too? Needed the first time (y/N): "
    echo "echo Firmware..."
    for p in ${FW}; do for s in a b; do echo "%FB% flash ${p}_${s} firmware\\${p}.img || goto :fail"; done; done
    echo "echo Boot images..."
    for p in boot dtbo vendor_boot vbmeta vbmeta_system; do for s in a b; do echo "%FB% flash ${p}_${s} images\\${p}.img || goto :fail"; done; done
    echo "echo System..."
    echo "%FB% flash super images\\super.img || goto :fail"
    echo "%FB% set_active a || goto :fail"
    echo "if /i \"%W%\"==\"y\" (%FB% erase userdata & %FB% erase metadata)"
    echo "%FB% reboot"
    echo "echo Done. The first start takes a few minutes."
    echo "pause"
    echo "exit /b 0"
    echo ":fail"
    echo "echo Flashing failed. Nothing was wiped. Send the output above."
    echo "pause"
    echo "exit /b 1"
} | sed 's/$/\r/' > "${R}/flash_all.bat"

FP="$(grep -m1 '^ro.system.build.fingerprint=' ${O}/system/build.prop | cut -d= -f2 || true)"
SPL="$(grep -m1 '^ro.build.version.security_patch=' ${O}/system/build.prop | cut -d= -f2 || true)"
cat > "${R}/README.txt" <<EOF
MIRACLO ${VER} Aurora for the ${DEVICE_NAME} (${DEVICE}), ${LABEL}
Build: userdebug, signed with MIRACLO keys. $(date -u +"%Y-%m-%d %H:%M UTC")
Fingerprint: ${FP}
Android security patch: ${SPL}
Firmware: Xiaomi $(cat ${DEVICE_DIR}/stock/VERSION) (bootloader, modem, DSP and all other firmware included)

Install (computer, fastboot):  flash_all.sh (Linux/macOS) or flash_all.bat (Windows)
  - flash_keep_firmware.sh: the same, but keeps the firmware already on the phone.
  - Phone in fastboot mode (Power + Volume down), bootloader unlocked.
  - First install (coming from another ROM): answer y to wipe data.
Update (from MIRACLO): ${NAME}-ota.zip

Contents
  images/    super (system, system_ext, product, vendor, odm, vendor_dlkm), boot (aurora), vendor_boot, dtbo, vbmeta
  firmware/  ${FW}
EOF
(cd "${R}" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd ~/release && rm -f "${NAME}.zip" && zip -qr -9 "${NAME}.zip" "${NAME}")
echo
echo "RELEASE READY: ~/release/${NAME}.zip ($(du -h ~/release/${NAME}.zip | cut -f1))"
echo "   (also unpacked in ~/release/${NAME}/; the OTA alone: ${NAME}-ota.zip inside)"
