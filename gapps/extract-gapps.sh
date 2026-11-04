#!/bin/bash
#
# SPDX-FileCopyrightText: MiraCLO
# SPDX-License-Identifier: Apache-2.0
#
# Builds MiraCLO's Google apps from Google's own Android 17 Pixel factory image:
# Play Store and Google Play services, Phone and Contacts, Messages, the setup
# wizard with data restore, Gmail, Chrome with its WebView, Gboard, and Google's everyday
# apps (YouTube, YouTube Music, Photos, Drive, Maps, Calendar, the Google app and Gemini,
# Clock, Calculator, Files, Keep, Meet, Google One, Speech Services, Digital Wellbeing). The
# files are never stored in git (proprietary/ is ignored); this script writes
# them and the build files that use them (Android.bp, gapps.mk).
#
# Run from the top of the source tree after building the host tools once:
#   m aapt2 simg2img fsck.erofs
#   vendor/miraclo/gapps/extract-gapps.sh <factory image zip, or its URL>
# Factory images: https://developers.google.com/android/images (accept Google's
# terms there and copy the link of an Android 17 image for a recent Pixel).

set -e

SRC="$1"
if [ -z "${SRC}" ]; then
    echo "usage: $0 <Pixel factory image .zip or URL>" >&2
    exit 1
fi

TOP="$(pwd)"
GAPPS="${TOP}/vendor/miraclo/gapps"
OUT="${GAPPS}/proprietary"
WORK="${GAPPS}/.work"
HOST="${TOP}/out/host/linux-x86/bin"
for t in aapt2 simg2img fsck.erofs; do
    [ -x "${HOST}/${t}" ] || { echo "Host tool missing: m aapt2 simg2img fsck.erofs" >&2; exit 1; }
done

rm -rf "${WORK}" "${OUT}" "${GAPPS}/skipped.txt"
mkdir -p "${WORK}" "${OUT}"

if [ -f "${SRC}" ]; then
    ZIP="${SRC}"
else
    echo "Downloading the factory image..."
    ZIP="${WORK}/factory.zip"
    wget -q --show-progress -O "${ZIP}" "${SRC}"
fi

echo "Unpacking..."
unzip -q -o "${ZIP}" -d "${WORK}/f"
INNER="$(find "${WORK}/f" -name 'image-*.zip' | head -1)"
[ -n "${INNER}" ] || { echo "No image-*.zip in the factory image" >&2; exit 1; }
unzip -q -o "${INNER}" system.img system_ext.img product.img -d "${WORK}/img"

extract() { # <name>
    local img="${WORK}/img/$1.img" raw="${WORK}/img/$1.raw" dst="${WORK}/root/$1"
    mkdir -p "${dst}"
    if "${HOST}/simg2img" "${img}" "${raw}" 2>/dev/null; then img="${raw}"; fi
    if "${HOST}/fsck.erofs" --extract="${dst}" "${img}" > /dev/null 2>&1; then
        return
    fi
    debugfs -R "rdump / ${dst}" "${img}" > /dev/null 2>&1
}
for p in system system_ext product; do
    echo "Extracting ${p}..."
    extract "${p}"
done
ROOT="${WORK}/root"
# The system partition is a full root; its files live under system/.
[ -d "${ROOT}/system/system" ] && SYSROOT="${ROOT}/system/system" || SYSROOT="${ROOT}/system"

# Google Play services (and the apps around it) now ship inside a Google system
# APEX on Pixels. Open it up and treat its contents like another partition.
mkdir -p "${ROOT}/gmsapex"
for apex in "${ROOT}"/product/apex/com.google.android.gmssystem*.apex; do
    [ -f "${apex}" ] || continue
    echo "Opening $(basename "${apex}")..."
    rm -rf "${WORK}/apex" && mkdir -p "${WORK}/apex"
    unzip -q -o "${apex}" apex_payload.img -d "${WORK}/apex"
    "${HOST}/fsck.erofs" --extract="${ROOT}/gmsapex" "${WORK}/apex/apex_payload.img" > /dev/null 2>&1 \
        || debugfs -R "rdump / ${ROOT}/gmsapex" "${WORK}/apex/apex_payload.img" > /dev/null 2>&1
    find "${ROOT}/gmsapex" -maxdepth 3 -type d | head -20
done

# package name -> what it replaces in MiraCLO (overrides)
declare -A WANT=(
    [com.google.android.gms]=""
    [com.google.android.gsf]=""
    [com.android.vending]=""
    [com.google.android.syncadapters.calendar]=""
    [com.google.android.syncadapters.contacts]=""
    [com.google.android.partnersetup]=""
    [com.google.android.onetimeinitializer]=""
    [com.google.android.configupdater]=""
    [com.google.android.setupwizard]="Provision"
    [com.google.android.apps.restore]=""
    [com.google.android.dialer]="Dialer"
    [com.google.android.contacts]=""
    [com.google.android.apps.messaging]="messaging"
    [com.google.android.ims]=""
    [com.google.android.gm]=""
    [com.android.chrome]=""
    [com.google.android.trichromelibrary]=""
    [com.google.android.inputmethod.latin]="LatinIME"
    # Google's everyday apps
    [com.google.android.youtube]=""
    [com.google.android.apps.youtube.music]=""
    [com.google.android.apps.photos]=""
    [com.google.android.apps.docs]=""
    [com.google.android.apps.maps]=""
    [com.google.android.calendar]="Calendar"
    [com.google.android.googlequicksearchbox]="QuickSearchBox"
    [com.google.android.apps.bard]=""
    [com.google.android.deskclock]="DeskClock"
    [com.google.android.calculator]="ExactCalculator"
    [com.google.android.apps.nbu.files]=""
    [com.google.android.keep]=""
    [com.google.android.tts]=""
    [com.google.android.apps.wellbeing]=""
    [com.google.android.apps.photos.scanner]=""
    [com.google.android.feedback]=""
    [com.google.android.printservice.recommendation]=""
    [com.google.android.apps.subscriptions.red]=""
    [com.google.android.apps.tachyon]=""
    [com.google.ar.core]=""
)

BP="${GAPPS}/Android.bp"
MK="${GAPPS}/gapps.mk"
cat > "${BP}" <<'HDR'
//
// SPDX-FileCopyrightText: MiraCLO
// SPDX-License-Identifier: Apache-2.0
//
// Generated by extract-gapps.sh from Google's Pixel factory image. Google's
// apps keep Google's signatures (presigned).

HDR
{
    echo "#"
    echo "# SPDX-FileCopyrightText: MiraCLO"
    echo "# SPDX-License-Identifier: Apache-2.0"
    echo "#"
    echo "# Generated by extract-gapps.sh. Google apps for MiraCLO (WITH_GMS=true)."
    echo
    echo "PRODUCT_PACKAGES += \\"
} > "${MK}"

found=0
declare -A GOT=()
for part in gmsapex product system_ext system; do
    base="${ROOT}/${part}"
    [ "${part}" = "system" ] && base="${SYSROOT}"
    for kind in app priv-app; do
        [ -d "${base}/${kind}" ] || continue
        for dir in "${base}/${kind}"/*/; do
            dname="$(basename "${dir}")"
            # Stub apps (Chrome-Stub, WebViewGoogle-Stub...): the real app is the
            # compressed .apk.gz in the folder without "-Stub"; take that instead.
            if [[ "${dname}" == *-Stub ]] && ls "${base}/${kind}/${dname%-Stub}/"*.apk.gz >/dev/null 2>&1; then
                continue
            fi
            gz="$(ls "${dir}"*.apk.gz 2>/dev/null | head -1)"
            if [ -n "${gz}" ]; then
                apk="${WORK}/unz/${dname}.apk"
                mkdir -p "${WORK}/unz"
                gunzip -c "${gz}" > "${apk}"
            else
                apk="$(ls "${dir}"*.apk 2>/dev/null | head -1)"
            fi
            [ -n "${apk}" ] || continue
            pkg="$("${HOST}/aapt2" dump packagename "${apk}" 2>/dev/null || true)"
            if [ -z "${pkg}" ]; then
                # Some apps (Play services, Carrier Services...) are too new for
                # this aapt2 to read; known by their folder name instead.
                case "${dname}" in
                    PrebuiltGmsCore*|GmsCore*) pkg=com.google.android.gms ;;
                    GoogleCalendarSyncAdapter*) pkg=com.google.android.syncadapters.calendar ;;
                    GoogleContactsSyncAdapter*) pkg=com.google.android.syncadapters.contacts ;;
                    GoogleOneTimeInitializer*) pkg=com.google.android.onetimeinitializer ;;
                    CarrierServices*|PrebuiltCarrierServices*) pkg=com.google.android.ims ;;
                    *) echo "  (unreadable) ${part}/${kind}/${dname}" >> "${GAPPS}/skipped.txt" ;;
                esac
            fi
            if [ -n "${pkg}" ] && [ -z "${WANT[${pkg}]+x}" ]; then
                case "${pkg}" in com.google.*|com.android.vending|com.android.chrome) echo "  (not taken) ${pkg} in ${part}/${kind}/${dname}" >> "${GAPPS}/skipped.txt" ;; esac
            fi
            [ -n "${pkg}" ] && [ -n "${WANT[${pkg}]+x}" ] || continue
            [ -z "${GOT[${pkg}]+x}" ] || continue
            name="MiraGms_${dname}"
            dest="${OUT}/${kind}/${dname}"
            mkdir -p "${dest}"
            cp "${apk}" "${dest}/${dname}.apk"
            rel="proprietary/${kind}/${dname}/${dname}.apk"
            {
                echo "android_app_import {"
                echo "    name: \"${name}\","
                echo "    apk: \"${rel}\","
                echo "    presigned: true,"
                echo "    preprocessed: true,"
                echo "    product_specific: true,"
                [ "${kind}" = "priv-app" ] && echo "    privileged: true,"
                echo "    dex_preopt: { enabled: false },"
                echo "    enforce_uses_libs: false,"
                if [ -n "${WANT[${pkg}]}" ]; then
                    printf '    overrides: ['
                    for o in ${WANT[${pkg}]}; do printf '"%s", ' "${o}"; done
                    echo '],'
                fi
                echo "}"
                echo
            } >> "${BP}"
            echo "    ${name} \\" >> "${MK}"
            echo "  ${pkg} -> ${name}"
            GOT[${pkg}]=1
            found=$((found + 1))
        done
    done
done

# Permissions, system configs, default permissions and the libraries some apps need.
for sub in etc/permissions etc/sysconfig etc/default-permissions; do
    for part in gmsapex product system_ext; do
        src="${ROOT}/${part}/${sub}"
        [ -d "${src}" ] || continue
        for x in "${src}"/*.xml; do
            [ -f "${x}" ] || continue
            grep -qiE 'com\.google|com\.android\.vending|com\.android\.chrome|com\.android\.deskclock' "${x}" || continue
            b="$(basename "${x}")"
            mod="MiraGms_${part}_$(echo "${sub}" | tr '/-' '__')_${b%.xml}"
            mkdir -p "${OUT}/${part}/${sub}"
            cp "${x}" "${OUT}/${part}/${sub}/"
            {
                echo "prebuilt_etc {"
                echo "    name: \"${mod}\","
                echo "    src: \"proprietary/${part}/${sub}/${b}\","
                echo "    filename: \"${b}\","
                echo "    sub_dir: \"${sub#etc/}\","
                echo "    product_specific: true,"
                echo "}"
                echo
            } >> "${BP}"
            echo "    ${mod} \\" >> "${MK}"
        done
    done
done
for jar in com.google.android.maps com.google.android.dialer.support; do
    j="$(find "${ROOT}/product/framework" "${ROOT}/system_ext/framework" -name "${jar}.jar" 2>/dev/null | head -1)"
    [ -n "${j}" ] || continue
    mkdir -p "${OUT}/framework"
    cp "${j}" "${OUT}/framework/"
    {
        echo "dex_import {"
        echo "    name: \"${jar}\","
        echo "    jars: [\"proprietary/framework/${jar}.jar\"],"
        echo "    product_specific: true,"
        echo "}"
        echo
    } >> "${BP}"
    echo "    ${jar} \\" >> "${MK}"
done

# Google's WebView takes over from the AOSP one.
mkdir -p "${GAPPS}/overlay/res/xml"
cat > "${GAPPS}/overlay/AndroidManifest.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="org.miraclo.overlay.gms.frameworks">
    <overlay android:targetPackage="android" android:isStatic="true" android:priority="20" />
</manifest>
XML
cat > "${GAPPS}/overlay/res/xml/config_webview_packages.xml" <<'XML'
<?xml version="1.0" encoding="utf-8"?>
<webviewproviders>
    <webviewprovider description="Android System WebView" packageName="com.android.webview" availableByDefault="true" />
    <webviewprovider description="Android System WebView" packageName="com.google.android.webview" availableByDefault="true" />
    <webviewprovider description="Chrome" packageName="com.android.chrome" availableByDefault="true" />
</webviewproviders>
XML
cat >> "${BP}" <<'BPX'
runtime_resource_overlay {
    name: "MiraGmsFrameworksOverlay",
    resource_dirs: ["overlay/res"],
    manifest: "overlay/AndroidManifest.xml",
    product_specific: true,
    sdk_version: "current",
}
BPX
cat >> "${MK}" <<'MKX'
    MiraGmsFrameworksOverlay

# Google's setup wizard on a non-Pixel phone: optional steps, no OEM pages.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.setupwizard.mode=OPTIONAL \
    ro.setupwizard.rotation_locked=true \
    setupwizard.theme=glif_v4 \
    setupwizard.feature.baseline_setupwizard_enabled=true \
    ro.opa.eligible_device=true \
    ro.com.google.gmsversion=17
MKX

rm -rf "${WORK}"
echo "Done: ${found} Google apps in vendor/miraclo/gapps."
for p in "${!WANT[@]}"; do
    [ -n "${GOT[${p}]+x}" ] || echo "  MISSING: ${p}"
done
echo "Google packages found but not taken: vendor/miraclo/gapps/skipped.txt"
