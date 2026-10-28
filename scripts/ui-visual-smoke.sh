#!/usr/bin/env bash
# UI-01 visual smoke on a disposable GitHub Actions AVD.
# This must not submit orders or contact the production service.
set -euo pipefail

pkg="ru.transferservis.app.staging"
activity="ru.transferservis.app.MainActivity"
out="build/ui-visual-smoke"
mkdir -p "$out"

apk="app/build/outputs/apk/debug/app-debug.apk"
test -s "$apk"
adb install -r "$apk"
adb shell pm path "$pkg" | grep -Fq "package:"

# A fresh Debug APK has an invalid staging URL by default; open screens only.
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
adb shell am force-stop "$pkg" || true
adb shell input keyevent KEYCODE_HOME
sleep 2

# Capture the system splash transition and final home screen.
adb shell screenrecord --size 720x1280 --bit-rate 3500000 --time-limit 12 \
    /sdcard/ui01-launch.mp4 > "$out/screenrecord.log" 2>&1 &
rec_pid=$!
sleep 2
adb shell am start -W -n "${pkg}/${activity}" | tee "$out/am-start.txt"
sleep 5
adb shell screencap -p /sdcard/ui01-home.png
adb pull /sdcard/ui01-home.png "$out/home.png" >/dev/null

# The OS settings page shows the app name and actual launcher icon.
adb shell am start -a android.settings.APPLICATION_DETAILS_SETTINGS \
    -d "package:${pkg}" | tee "$out/appinfo-start.txt"
sleep 2
adb shell screencap -p /sdcard/ui01-appinfo.png
adb pull /sdcard/ui01-appinfo.png "$out/app-info-icon.png" >/dev/null

wait "$rec_pid" || true
adb pull /sdcard/ui01-launch.mp4 "$out/launch.mp4" >/dev/null

test -s "$out/home.png"
test -s "$out/app-info-icon.png"
test -s "$out/launch.mp4"
echo "UI01_VISUAL_SMOKE_ARTIFACTS_READY=true"
echo "No booking or production network calls were made by this script."
