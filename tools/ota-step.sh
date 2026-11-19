#!/bin/bash
# Step 5 on Android 17 (no "otapackage" target): the update zip, signed with the MIRACLO release key.
set -uo pipefail
cd ~/aurora
set +u; source build/envsetup.sh >/dev/null; lunch miraclo_marble-cp2a-userdebug >/dev/null; set -u
O=out/target/product/marble
m otapackage && exit 0
echo "otapackage target not available: building target files instead"
m target-files-package || m target_files_package || m dist || exit 1
m ota_from_target_files || exit 1
OTA="$(ls -t ${O}/*-ota*.zip out/dist/*-ota*.zip 2>/dev/null | grep -v target_files | head -1)"
if [ -n "${OTA}" ]; then cp -n "${OTA}" ${O}/ 2>/dev/null; echo "OTA: ${OTA}"; exit 0; fi
TF="$(find out/target/product/marble out/dist out/soong -name '*target_files*.zip' -newer ${O}/system.img 2>/dev/null | xargs -r ls -t | head -1)"
[ -n "${TF}" ] || { echo "No target files zip found"; exit 1; }
echo "target files: ${TF}"
ota_from_target_files -k vendor/miraclo-keys/releasekey "${TF}" ${O}/miraclo_marble-ota.zip && echo "OTA: ${O}/miraclo_marble-ota.zip"
