#!/usr/bin/env bash
set -euo pipefail
report_dir=app/build/reports/stage3-process
mkdir -p "$report_dir"
python3 tools/tests/queue-test-server.py --port 18084 > "$report_dir/server.log" 2>&1 &
queue_server_pid=$!
trap 'kill "$queue_server_pid" 2>/dev/null || true' EXIT
adb reverse tcp:18084 tcp:18084
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.audiobookdownloader.QueueProcessTest#prepareActiveQueueBeforeProcessStop --max-workers=2 --console=plain
cp -R app/build/outputs/androidTest-results "$report_dir/before-stop"
adb shell am force-stop com.audiobookdownloader.preview
adb reboot
adb wait-for-device
boot_deadline=$((SECONDS + 180))
until [[ "$(adb shell getprop sys.boot_completed | tr -d '\r')" == 1 ]]; do
    if ((SECONDS > boot_deadline)); then echo 'Emulator reboot timed out' >&2; exit 1; fi
    sleep 1
done
adb reverse tcp:18084 tcp:18084
adb shell input keyevent 82
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.audiobookdownloader.QueueProcessTest#restoredQueueCompletesAfterProcessStopAndReboot --max-workers=2 --console=plain
cp -R app/build/outputs/androidTest-results "$report_dir/after-reboot"
