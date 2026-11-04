#!/bin/bash
# MIRACLO screenshots for GitHub. Run on the laptop with the phone on MIRACLO, unlocked, USB debugging on.
# Clean status bar (demo mode: 10:00, full battery, full signal, no notifications), light and dark.
# Saves to ~/Documents/MIRACLO/shots/<name>.png. Your settings are put back at the end.
A=${ADB:-~/Documents/MIRACLO/platform-tools-linux/adb}
OUT=~/Documents/MIRACLO/shots
mkdir -p "$OUT"
$A root >/dev/null 2>&1; sleep 3; $A wait-for-device
sh() { $A shell "$@"; }
shot() { sleep "${2:-1.6}"; $A exec-out screencap -p > "$OUT/$1.png" && echo "  $1.png"; }
home() { sh input keyevent KEYCODE_HOME; sleep 1; }
page() { sh am start -W -n com.android.settings/.SubSettings --es :settings:show_fragment "$1" >/dev/null 2>&1; }

# remember what to put back
OLD_QS=$(sh settings get secure miraclo_qs_style | tr -d '\r')
OLD_NIGHT=$(sh cmd uimode night | tr -d '\r' | awk '{print $NF}')

# clean status bar
sh settings put global sysui_demo_allowed 1
demo() { sh am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
demo enter; demo clock -e hhmm 1000; demo battery -e level 100 -e plugged false
demo network -e wifi show -e level 4 -e fully true; demo network -e mobile show -e level 4 -e datatype 5g -e fully true
demo notifications -e visible false

for mode in dark light; do
  echo "== $mode"
  [ "$mode" = dark ] && sh cmd uimode night yes >/dev/null || sh cmd uimode night no >/dev/null
  sleep 2
  home; shot ${mode}_home
  sh input swipe 540 2000 540 700 250; shot ${mode}_drawer; home
  # Quick Settings in each style: 1 ColorOS, 2 iOS, 4 One UI, 0 Android
  for s in 1:coloros 2:ios 4:oneui 0:android; do
    sh settings put secure miraclo_qs_style ${s%%:*}; sleep 1
    sh cmd statusbar expand-settings; shot ${mode}_qs_${s##*:} 2.2
    sh cmd statusbar collapse; sleep 1
  done
  sh cmd statusbar expand-notifications; shot ${mode}_notifications 2; sh cmd statusbar collapse
  sh am start -a android.settings.SETTINGS >/dev/null; shot ${mode}_settings 2
  page com.android.settings.miraclo.studio.MiraStudioFragment;        shot ${mode}_studio 2
  page com.android.settings.miraclo.studio.MiraStudioQsFragment;      shot ${mode}_studio_qs 2
  page com.android.settings.miraclo.studio.MiraStudioColorsFragment;  shot ${mode}_studio_colors 2.5
  page com.android.settings.miraclo.studio.MiraStudioMotionFragment;  shot ${mode}_studio_motion 2.5
  page com.android.settings.miraclo.studio.MiraStudioLockClocksFragment; shot ${mode}_studio_clocks 2.5
  page com.android.settings.miraclo.studio.MiraStudioIconsFragment;   shot ${mode}_studio_icons 2
  page com.android.settings.miraclo.studio.MiraStudioLauncherFragment; shot ${mode}_studio_launcher 2
  sh am start -a android.settings.DEVICE_INFO_SETTINGS >/dev/null;    shot ${mode}_about 2
  home
  sh input keyevent KEYCODE_SLEEP; sleep 1.5; sh input keyevent KEYCODE_WAKEUP; shot ${mode}_lockscreen 2
  sh input keyevent KEYCODE_MENU >/dev/null 2>&1; home
done

# put everything back
sh settings put secure miraclo_qs_style "${OLD_QS:-0}"
[ "$OLD_NIGHT" = yes ] && sh cmd uimode night yes >/dev/null || sh cmd uimode night no >/dev/null
demo exit
echo
echo "Done: $(ls "$OUT"/*.png | wc -l) screenshots in $OUT"
echo "Look through them; delete any you don't want (and any showing personal info). The lock screen"
echo "may need you to swipe it away. Then send them to the server:"
echo "  ssh -p 22 marblepsp@waffle.serverhive.in 'mkdir -p ~/miraclo-shots' && scp -P 22 $OUT/*.png marblepsp@waffle.serverhive.in:~/miraclo-shots/"
