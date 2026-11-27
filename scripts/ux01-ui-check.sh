#!/usr/bin/env bash
# Только одноразовый GitHub Actions AVD; без credentials, реальных заказов и FCM.
set -euo pipefail
pkg="ru.transferservis.app.staging"
out="build/ux01"
mkdir -p "$out"
for config in "360:1.0" "360:2.0" "411:1.3"; do
    width="${config%%:*}"
    scale="${config##*:}"
    label="${width}dp-font${scale}"
    if [ "$width" = "360" ]; then
        adb shell wm size 900x2000
        adb shell wm density 400
    else
        adb shell wm size 1080x2400
        adb shell wm density 420
    fi
    adb shell settings put system font_scale "$scale"
    adb shell wm size > "$out/${label}-size.txt"
    adb shell wm density > "$out/${label}-density.txt"
    adb shell settings get system font_scale > "$out/${label}-font.txt"
    # Очищаются только старые отчёты текущего CI runner, чтобы не принять прошлый PASS.
    rm -rf app/build/outputs/androidTest-results/connected
    ./gradlew :app:connectedDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=ru.transferservis.app.ui.active.ApprovedPassengerUiTest,ru.transferservis.app.ui.home.HomeRouteSwapUiTest,ru.transferservis.app.ui.PendingBookingHomeScreenTest \
        --stacktrace
    python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as E
expected={'approvedScreens_keepActionsAndOfflineSnapshotAccessible',
          'historyAndPendingRecovery_preserveExistingGuards',
          'swapRouteExchangesAddressesWithoutSubmission',
          'changingTariffShowsMatchingDescription','tariffLabelsAreFullyVisible',
          'pendingReferenceAppearsAfterActivityRelaunch_withoutNetworkRetry',
          'corruptedEncryptedRecordStillWarnsAndBlocksNewBooking',
          'manualRecoveryDialog_canBeCancelledWithoutSendingOrClearingRequest'}
passed=set()
for path in Path('app/build/outputs/androidTest-results/connected').rglob('*.xml'):
    for case in E.parse(path).iter('testcase'):
        name=case.get('name')
        if name in expected and not any(case.find(x) is not None for x in ['failure','error','skipped']):
            passed.add(name)
if passed != expected: raise SystemExit('Отсутствуют пройденные тесты: '+str(expected-passed))
print('UX01_CASES_PASS='+str(len(passed)))
PY
    mkdir -p "$out/$label"
    cp -a app/build/outputs/androidTest-results/connected "$out/$label/tests"
    adb pull "/sdcard/Android/data/$pkg/files/ux01" "$out/$label/" >/dev/null
    test -s "$out/$label/ux01/home.png"
    test -s "$out/$label/ux01/offline.png"
done
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
