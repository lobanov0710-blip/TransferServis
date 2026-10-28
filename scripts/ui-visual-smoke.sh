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
adb shell screenrecord --bit-rate 3500000 --time-limit 12 \
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

# The Settings app may cold-start with its own splash; verify the actual
# application detail page is visible before taking an icon screenshot.
ready=0
for attempt in $(seq 1 15); do
    adb shell uiautomator dump /sdcard/ui01-appinfo-window.xml >/dev/null 2>&1 || true
    if adb pull /sdcard/ui01-appinfo-window.xml "$out/appinfo-window.xml" >/dev/null 2>&1 \
        && grep -Fq "Transfer Servis" "$out/appinfo-window.xml"; then
        ready=1
        break
    fi
    sleep 2
done
if [ "$ready" -ne 1 ]; then
    echo "ERROR: application settings details never showed Transfer Servis" >&2
    exit 1
fi
adb shell screencap -p /sdcard/ui01-appinfo.png
adb pull /sdcard/ui01-appinfo.png "$out/app-info-icon.png" >/dev/null

wait "$rec_pid" || true
adb pull /sdcard/ui01-launch.mp4 "$out/launch.mp4" >/dev/null

test -s "$out/home.png"
test -s "$out/app-info-icon.png"
test -s "$out/launch.mp4"
echo "UI01_VISUAL_SMOKE_ARTIFACTS_READY=true"
echo "No booking or production network calls were made by this script."

# UI-02: screenshot APK and Gradle-built test APK can have different debug
# signing keys on ephemeral runners. Uninstall *only on this disposable AVD*
# before Gradle installs its matching app + test pair.
adb shell am force-stop "$pkg" >/dev/null 2>&1 || true
adb uninstall "${pkg}.test" >/dev/null 2>&1 || true
adb uninstall "$pkg" >/dev/null 2>&1 || true

./gradlew :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=ru.transferservis.app.ui.home.HomeRouteSwapUiTest \
    --stacktrace

# Do not trust Gradle exit status alone: an AndroidTestRunner install error
# can otherwise be reported as BUILD SUCCESSFUL with ZERO executed tests.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

root = Path("app/build/outputs/androidTest-results/connected")
expected = {
    "swapRouteExchangesAddressesWithoutSubmission",
    "changingTariffShowsMatchingDescription",
}
passed = set()
reports = list(root.rglob("*.xml")) if root.exists() else []
for report in reports:
    try:
        xml = ET.parse(report).getroot()
    except ET.ParseError:
        continue
    for case in xml.iter("testcase"):
        if case.attrib.get("name") not in expected:
            continue
        if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
            continue
        passed.add(case.attrib["name"])

print(f"UI02_INSTRUMENTED_TESTS_PASSED={len(passed)}/2")
print(f"UI02_INSTRUMENTED_XML_REPORTS_FOUND={len(reports)}")
if passed != expected:
    raise SystemExit("ERROR: both view-only Espresso tests must actually execute and pass")
PY
mkdir -p "$out/ui02-test-results"
cp -a app/build/outputs/androidTest-results/connected/. "$out/ui02-test-results/" 2>/dev/null || true
echo "UI02_VIEW_ONLY_TESTS_COMPLETED=true"
