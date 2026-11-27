#!/usr/bin/env bash
# Только одноразовый GitHub Actions AVD; без credentials, реальных заказов и FCM.
set -euo pipefail
pkg="ru.transferservis.app.staging"
out="build/ux01"
mkdir -p "$out"
# Оба APK из одной сборки: одинаковая debug-подпись, только одноразовый AVD.
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
classes="ru.transferservis.app.ui.active.ApprovedPassengerUiTest,ru.transferservis.app.ui.home.HomeRouteSwapUiTest,ru.transferservis.app.ui.PendingBookingHomeScreenTest"
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
    mkdir -p "$out/$label"
    # Передаём весь список классов напрямую AndroidJUnitRunner одним аргументом.
    # Сырые статусы сохраняются даже при падении: exit code adb сам по себе не доказывает PASS.
    adb shell am instrument -w -r -e class "$classes" \
        "${pkg}.test/androidx.test.runner.AndroidJUnitRunner" \
        | tee "$out/$label/instrumentation.txt"
    adb pull "/sdcard/Android/data/$pkg/files/ux01" "$out/$label/" >/dev/null
    UX01_LABEL="$label" python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as E
expected={'approvedScreens_keepActionsAndOfflineSnapshotAccessible',
          'historyAndPendingRecovery_preserveExistingGuards',
          'swapRouteExchangesAddressesWithoutSubmission',
          'changingTariffShowsMatchingDescription','tariffLabelsAreFullyVisible',
          'pendingReferenceAppearsAfterActivityRelaunch_withoutNetworkRetry',
          'corruptedEncryptedRecordStillWarnsAndBlocksNewBooking',
          'manualRecoveryDialog_canBeCancelledWithoutSendingOrClearingRequest'}
import os,re
root=Path('build/ux01')/os.environ['UX01_LABEL']
raw=(root/'instrumentation.txt').read_text()
records=[];current={}
for line in raw.splitlines():
    match=re.match(r'INSTRUMENTATION_STATUS: (class|test|stack)=(.*)',line)
    if match: current[match[1]]=match[2]
    if line.startswith('INSTRUMENTATION_STATUS_CODE:'):
        code=int(line.split(':',1)[1].strip())
        if code != 1 and 'test' in current:
            records.append((dict(current),code));current={}
passed={case['test'] for case,code in records if code==0}
report=E.Element('testsuite',name='UX01',tests=str(len(records)),
                 failures=str(sum(code!=0 for _,code in records)))
for case,code in records:
    item=E.SubElement(report,'testcase',classname=case.get('class',''),name=case['test'])
    if code!=0:E.SubElement(item,'failure').text=case.get('stack','Instrumentation status '+str(code))
E.ElementTree(report).write(root/'instrumentation-results.xml',encoding='utf-8',xml_declaration=True)
if passed != expected or len(records)!=len(expected):
    raise SystemExit('Не подтверждены все 8 тестов: '+str(expected-passed))
if not re.search(r'OK\s*\(8 tests\)',raw) or 'INSTRUMENTATION_CODE: -1' not in raw:
    raise SystemExit('Нет итогового успешного результата AndroidJUnitRunner')
print('UX01_CASES_PASS='+str(len(passed)))
PY
    test -s "$out/$label/ux01/home.png"
    test -s "$out/$label/ux01/offline.png"
done
adb shell wm size reset
adb shell wm density reset
adb shell settings put system font_scale 1.0
