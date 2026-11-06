#!/bin/bash
# MIRACLO virtual POCO F5 on an Apple Silicon Mac.
#   bash miraclo-emu-mac.sh install <sdk-repo-linux-system-images.zip>   once per new image
#   bash miraclo-emu-mac.sh run                                          start it
#   bash miraclo-emu-mac.sh push <App.apk> [<App2.apk> ...]               try a rebuilt app
# Needs Android Studio (its SDK in ~/Library/Android/sdk, with "Android Emulator" installed).
set -u
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
AVD=MIRACLO_POCO_F5
EMU="$SDK/emulator/emulator"
ADB="$SDK/platform-tools/adb"
[ -x "$EMU" ] || { echo "Android Emulator not found in $SDK: open Android Studio > Settings > Android SDK > SDK Tools, tick Android Emulator and Android SDK Platform-Tools"; exit 1; }

case "${1:-}" in
install)
  Z="${2:?give the image zip}"
  W=$(mktemp -d); unzip -q "$Z" -d "$W" || exit 1
  SP=$(find "$W" -name source.properties | head -1); [ -n "$SP" ] || { echo "no source.properties in the zip"; exit 1; }
  ROOT=$(dirname "$SP")
  get() { sed -n "s/^$1=//p" "$SP" | tr -d '\r' | head -1; }
  API=$(get AndroidVersion.ApiLevel); CODE=$(get AndroidVersion.CodeName)
  TAG=$(get SystemImage.TagId); ABI=$(get SystemImage.Abi)
  PLAT="android-${CODE:-$API}"; TAG=${TAG:-default}; ABI=${ABI:-arm64-v8a}
  DEST="$SDK/system-images/$PLAT/miraclo/$ABI"
  rm -rf "$DEST"; mkdir -p "$(dirname "$DEST")"; mv "$ROOT" "$DEST"; rm -rf "$W"
  # mark it as MIRACLO's own image
  sed -i '' "s/^SystemImage.TagId=.*/SystemImage.TagId=miraclo/" "$DEST/source.properties" 2>/dev/null
  mkdir -p "$HOME/.android/avd/$AVD.avd"
  cat > "$HOME/.android/avd/$AVD.ini" <<INI
avd.ini.encoding=UTF-8
path=$HOME/.android/avd/$AVD.avd
path.rel=avd/$AVD.avd
target=$PLAT
INI
  cat > "$HOME/.android/avd/$AVD.avd/config.ini" <<INI
AvdId=$AVD
avd.ini.displayname=MIRACLO POCO F5
abi.type=$ABI
hw.cpu.arch=arm64
hw.cpu.ncore=6
image.sysdir.1=system-images/$PLAT/miraclo/$ABI/
tag.id=miraclo
tag.display=MIRACLO
target=$PLAT
hw.lcd.width=1080
hw.lcd.height=2400
hw.lcd.density=440
hw.ramSize=6144
vm.heapSize=512
disk.dataPartition.size=16G
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.keyboard=yes
hw.mainKeys=no
hw.audioInput=yes
hw.sensors.orientation=yes
showDeviceFrame=no
fastboot.forceColdBoot=yes
INI
  echo "Installed: $DEST"
  echo "Virtual phone: $AVD (1080 x 2400, density 440, 6 GB RAM). Start it: bash $0 run"
  ;;
run)
  echo "First start takes a few minutes. Close the window to stop it."
  "$EMU" -avd "$AVD" -writable-system -no-snapshot-load >/tmp/miraclo-emu.log 2>&1 &
  "$ADB" wait-for-device; echo "Running. Logs: /tmp/miraclo-emu.log"
  ;;
push)
  shift; [ $# -gt 0 ] || { echo "give one or more .apk files"; exit 1; }
  "$ADB" root >/dev/null; sleep 2; "$ADB" wait-for-device
  "$ADB" remount 2>&1 | grep -qi "reboot" && { echo "First time: rebooting to make the system writable, then pushing"; "$ADB" reboot; "$ADB" wait-for-device; "$ADB" root >/dev/null; sleep 2; "$ADB" wait-for-device; "$ADB" remount >/dev/null; }
  for apk in "$@"; do
    name=$(basename "$apk" .apk)
    dir=$("$ADB" shell "ls -d /system_ext/priv-app/$name /system/priv-app/$name /product/priv-app/$name /system_ext/app/$name /system/app/$name 2>/dev/null" | head -1 | tr -d '\r')
    [ -n "$dir" ] || { echo "!! $name: not found on the virtual phone"; continue; }
    "$ADB" push "$apk" "$dir/$name.apk" >/dev/null && echo "   $name -> $dir"
  done
  "$ADB" shell stop; "$ADB" shell start; echo "Restarted the Android framework with the new apps."
  ;;
*) sed -n '2,6p' "$0" ;;
esac
