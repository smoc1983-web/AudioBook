#!/usr/bin/env bash
set -euo pipefail
report_dir=app/build/reports/stage3-process
mkdir -p "$report_dir"
python3 tools/tests/queue-test-server.py --port 18084 > "$report_dir/server.log" 2>&1 &
queue_server_pid=$!
trap 'kill "$queue_server_pid" 2>/dev/null || true' EXIT
# Run instrumentation directly: Gradle may uninstall the package after connected tests.
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb reverse tcp:18084 tcp:18084
timeout 150 adb shell am instrument -w -r -e class com.audiobookdownloader.QueueProcessTest#prepareActiveQueueBeforeProcessStop com.audiobookdownloader.preview.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/before-stop.log"
rg -q 'OK \(1 test\)' "$report_dir/before-stop.log"
adb shell am force-stop com.audiobookdownloader.preview
adb reboot
timeout 180 adb wait-for-disconnect
timeout 180 adb wait-for-device
boot_deadline=$((SECONDS + 180))
until [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; do
    if ((SECONDS > boot_deadline)); then echo 'Emulator reboot timed out' >&2; exit 1; fi
    sleep 1
done
adb reverse tcp:18084 tcp:18084
adb shell input keyevent 82
timeout 300 adb shell am instrument -w -r -e class com.audiobookdownloader.QueueProcessTest#restoredQueueCompletesAfterProcessStopAndReboot com.audiobookdownloader.preview.test/androidx.test.runner.AndroidJUnitRunner | tee "$report_dir/after-reboot.log"
rg -q 'OK \(1 test\)' "$report_dir/after-reboot.log"
