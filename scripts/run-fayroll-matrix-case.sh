#!/usr/bin/env bash
set -euo pipefail

mode=$1
mapfile -t app_apks < <(find app/build/outputs/apk/debug -maxdepth 1 -name '*.apk')
mapfile -t test_apks < <(find app/build/outputs/apk/androidTest/debug -maxdepth 1 -name '*.apk')
if (( ${#app_apks[@]} != 1 || ${#test_apks[@]} != 1 )); then
    echo "Expected one app APK and one instrumentation APK" >&2
    exit 1
fi
timeout 90s adb install "${app_apks[0]}"
timeout 90s adb install "${test_apks[0]}"
timeout 15s adb logcat -G 16M || true
timeout 100m adb logcat -s FayrollMatrix:I '*:S' > "matrix-$mode.txt" &
logger=$!
trap 'kill "$logger" 2>/dev/null || true' EXIT

result=0
timeout 95m adb shell am instrument -w \
    -e class com.audiobookdownloader.FayrollSourceMatrixTest \
    -e sourceMode "$mode" \
    com.audiobookdownloader.test.test/androidx.test.runner.AndroidJUnitRunner \
    | tee "instrumentation-$mode.txt" || result=$?
timeout 15s adb logcat -d -b crash || true
cat "matrix-$mode.txt"
if (( result != 0 )); then
    exit "$result"
fi
rg -q '^OK \(1 test\)' "instrumentation-$mode.txt"
