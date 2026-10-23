#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:?Set ANDROID_HOME}"
# DESTRUCTIVE TO DEVICEPULSE TEST HISTORY. Use only a disposable emulator/device.
adb_bin="$ANDROID_HOME/platform-tools/adb"
"$adb_bin" install -r app/build/outputs/apk/debug/app-debug.apk
"$adb_bin" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
mkdir -p app/build/verification
"$adb_bin" shell am instrument -w -r dev.devicepulse.test/dev.devicepulse.PulseTests | tee app/build/verification/instrumentation.txt
# Custom Instrumentation reports RESULT_OK (-1), passed count and no FAIL.
python3 - <<'PY'
from pathlib import Path
s=Path('app/build/verification/instrumentation.txt').read_text()
import re
assert 'INSTRUMENTATION_CODE: -1' in s and '27 tests passed' in s and 'FAIL' not in s,s
assert len(re.findall(r'^INSTRUMENTATION_STATUS: stream=PASS ',s,re.M))==27,s
PY
