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
timeout 100m adb logcat -b crash > "crash-$mode.txt" &
crash_logger=$!
trap 'kill "$logger" "$crash_logger" 2>/dev/null || true' EXIT

for attempt in 1 2; do
    result=0
    timeout 70m adb shell am instrument -w \
        -e class com.audiobookdownloader.FayrollSourceMatrixTest \
        -e sourceMode "$mode" \
        com.audiobookdownloader.test.test/androidx.test.runner.AndroidJUnitRunner \
        | tee "instrumentation-$mode-$attempt.txt" || result=$?

    if (( result == 0 )) && grep -Eq '^OK \(1 test\)' "instrumentation-$mode-$attempt.txt"; then
        cat "matrix-$mode.txt"
        exit 0
    fi
    timeout 15s adb logcat -d -b crash || true
    if ! grep -Fq 'shortMsg=Process crashed' "instrumentation-$mode-$attempt.txt"; then
        break
    fi
    if [[ $attempt == 1 ]]; then
        # Retry a process crash once, on the same clean app state.
        timeout 15s adb shell pm clear com.audiobookdownloader.test
    fi
done
cat "matrix-$mode.txt"
exit 1
