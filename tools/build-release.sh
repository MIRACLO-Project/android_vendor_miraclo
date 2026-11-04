#!/bin/bash
#
# SPDX-FileCopyrightText: MIRACLO
# SPDX-License-Identifier: Apache-2.0
#
# MIRACLO full release build (userdebug, MIRACLO keys). Run on the SERVER, in tmux:
#
#   bash ~/release2/build-release.sh "<Pixel 7 (panther) Android 17 factory image URL>"
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

GAPPS_SRC="${1:-}"
[ -n "${GAPPS_SRC}" ] || { echo "usage: $0 <Pixel 7 (panther) Android 17 factory image URL or zip>" >&2; exit 1; }

TOP=~/aurora
cd "${TOP}"
LOG=~/release-build.log
: > "${LOG}"
step() { echo; echo "== $*" | tee -a "${LOG}"; }
run() { "$@" >> "${LOG}" 2>&1 || { echo "FAILED: $* (last lines of ${LOG}):"; tail -40 "${LOG}"; exit 1; }; }

[ -f vendor/miraclo-keys/releasekey.x509.pem ] || { echo "MIRACLO keys missing (vendor/miraclo-keys)"; exit 1; }

step "1/6 Environment and host tools"
set +u
source build/envsetup.sh > /dev/null
lunch miraclo_marble-cp2a-userdebug > /dev/null
set -u
run m ota_extractor fsck.erofs aapt2 simg2img

if [ -z "${RESUME:-}" ]; then
step "2/6 Stock firmware and vendor images"
LOCAL_ZIP="$(ls "$HOME"/stock/marble_global-ota_full-*.zip 2>/dev/null | head -1 || true)"
export STOCK_LOCAL_ZIP="${LOCAL_ZIP}"
run device/xiaomi/marble/fetch-stock.sh
ls device/xiaomi/marble/stock/firmware/*.img | xargs -n1 basename | tr '\n' ' ' | sed 's/^/   firmware: /'; echo

# Qualcomm performance libraries: installed where this framework looks for them.
PERF_MK=device/xiaomi/marble/stock/perf.mk
PERF_BP=device/xiaomi/marble/stock/Android.bp
: > "${PERF_MK}"; rm -f "${PERF_BP}"
for jar in QPerformance UxPerformance; do
    src="device/xiaomi/marble/stock/system_ext/framework/${jar}.jar"
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

step "3/6 Google apps (Pixel 7 image)"
run vendor/miraclo/gapps/extract-gapps.sh "${GAPPS_SRC}"
grep -E "^\s+MiraGms_" vendor/miraclo/gapps/gapps.mk | sed 's/ \\$//; s/^ */   /' | head -80
grep -q "PrebuiltGmsCore\|GmsCore" vendor/miraclo/gapps/Android.bp || { echo "Play services missing: stopping."; exit 1; }

fi
step "4/6 Full build (this takes a while)"
run m
step "4b/6 Preflight checks (stops here if the build would not boot cleanly)"
if ! python3 ~/preflight.py out/target/product/marble --expect \
        "system_ext/lib64/libimsmedia_jni.so" \
        "system_ext/lib64/libimscamera_jni.so" \
        "product/etc/permissions/privapp-permissions-miraclo-gapps.xml::com.android.deskclock" \
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
run bash ~/ota-step.sh

step "6/6 Packaging the release"
O=out/target/product/marble
VER="$(grep -m1 '^ro.miraclo.version=' ${O}/system/build.prop ${O}/system_ext/etc/build.prop 2>/dev/null | head -1 | cut -d= -f2 || true)"
[ -n "${VER}" ] || VER="1.0"
NAME="MIRACLO-${VER}-Aurora-marble-$(date +%Y%m%d)-userdebug"
R=~/release/${NAME}
rm -rf "${R}"; mkdir -p "${R}/images" "${R}/firmware"

for p in boot dtbo vendor_boot vbmeta vbmeta_system super_empty system system_ext product vendor odm vendor_dlkm; do
    cp "${O}/${p}.img" "${R}/images/"
done
cp device/xiaomi/marble/stock/firmware/*.img "${R}/firmware/"
OTA="$(ls -t ${O}/*-ota*.zip 2>/dev/null | grep -v target_files | head -1)"
[ -n "${OTA}" ] && cp "${OTA}" "${R}/${NAME}-ota.zip"

FW="$(cd "${R}/firmware" && ls *.img | sed 's/\.img$//' | tr '\n' ' ')"
cat > "${R}/flash_all.sh" <<EOF
#!/bin/bash
# MIRACLO for the POCO F5 (marble). Flash from a computer (Linux or macOS) with the phone in
# fastboot mode (Power + Volume down), bootloader unlocked. Keeps your data unless you choose
# to wipe it (needed the first time, coming from another ROM).
set -e
cd "\$(dirname "\$0")"
FB=\${FASTBOOT:-fastboot}
\$FB getvar product 2>&1 | grep -q marble || { echo "This is not a POCO F5 (marble). Stopping."; exit 1; }
read -p "Wipe all data too? Needed the first time (y/N): " w
echo "Firmware..."
for p in ${FW}; do for s in a b; do \$FB flash \${p}_\$s firmware/\$p.img; done; done
echo "Boot images..."
for p in boot dtbo vendor_boot vbmeta vbmeta_system; do for s in a b; do \$FB flash \${p}_\$s images/\$p.img; done; done
echo "System..."
\$FB reboot fastboot
\$FB wipe-super images/super_empty.img
for p in system system_ext product vendor odm vendor_dlkm; do \$FB flash \$p images/\$p.img; done
if [ "\$w" = "y" ] || [ "\$w" = "Y" ]; then \$FB -w; fi
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
    echo "rem MIRACLO for the POCO F5 (marble). Phone in fastboot mode, bootloader unlocked."
    echo "cd /d %~dp0"
    echo "set FB=fastboot"
    echo "%FB% getvar product 2>&1 | findstr /c:\"marble\" >nul || (echo This is not a POCO F5 ^(marble^). & pause & exit /b 1)"
    echo "set /p W=Wipe all data too? Needed the first time (y/N): "
    echo "echo Firmware..."
    for p in ${FW}; do for s in a b; do echo "%FB% flash ${p}_${s} firmware\\${p}.img || goto :fail"; done; done
    echo "echo Boot images..."
    for p in boot dtbo vendor_boot vbmeta vbmeta_system; do for s in a b; do echo "%FB% flash ${p}_${s} images\\${p}.img || goto :fail"; done; done
    echo "echo System..."
    echo "%FB% reboot fastboot"
    echo "%FB% wipe-super images\\super_empty.img || goto :fail"
    for p in system system_ext product vendor odm vendor_dlkm; do echo "%FB% flash ${p} images\\${p}.img || goto :fail"; done
    echo "if /i \"%W%\"==\"y\" %FB% -w"
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
MIRACLO ${VER} Aurora for the POCO F5 (marble)
Build: userdebug, signed with MIRACLO keys. $(date -u +"%Y-%m-%d %H:%M UTC")
Fingerprint: ${FP}
Android security patch: ${SPL}
Firmware: Xiaomi $(cat device/xiaomi/marble/stock/VERSION) (bootloader, modem, DSP and all other firmware included)

Install (computer, fastboot):  flash_all.sh (Linux/macOS) or flash_all.bat (Windows)
  - flash_keep_firmware.sh: the same, but keeps the firmware already on the phone.
  - Phone in fastboot mode (Power + Volume down), bootloader unlocked.
  - First install (coming from another ROM): answer y to wipe data.
Update (from MIRACLO): ${NAME}-ota.zip

Contents
  images/    system, system_ext, product, vendor, odm, vendor_dlkm, boot, vendor_boot, dtbo, vbmeta
  firmware/  ${FW}
EOF
(cd "${R}" && find . -type f ! -name SHA256SUMS -print0 | sort -z | xargs -0 sha256sum > SHA256SUMS)
(cd ~/release && rm -f "${NAME}.zip" && zip -qr -1 "${NAME}.zip" "${NAME}")
echo
echo "RELEASE READY: ~/release/${NAME}.zip ($(du -h ~/release/${NAME}.zip | cut -f1))"
echo "   (also unpacked in ~/release/${NAME}/; the OTA alone: ${NAME}-ota.zip inside)"
