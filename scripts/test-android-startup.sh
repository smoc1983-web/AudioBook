#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-evidence
# Same logical 411dp-wide display, fewer pixels for software rendering on CI.
adb shell wm size 360x800
adb shell wm density 140
# This app has no Play Services dependency; keep first-boot background setup from restarting.
adb shell pm disable-user --user 0 com.google.android.gms || true
adb shell input keyevent 82
adb logcat -c
adb shell rm -f /sdcard/Download/preview-sleep-shake-ready
adb emu sensor status > startup-evidence/sensor-status.txt
(
  for preview_attempt in $(seq 1 600); do
    if adb shell test -f /sdcard/Download/preview-sleep-shake-ready; then
      for preview_pulse in $(seq 1 6); do
        adb emu sensor set acceleration 14:0:9.80665
        sleep 0.10
        adb emu sensor set acceleration 0:0:9.80665
        sleep 0.12
        adb emu sensor set acceleration -14:0:9.80665
        sleep 0.10
        adb emu sensor set acceleration 0:0:9.80665
        sleep 0.12
      done
      break
    fi
    sleep 1
  done
) > startup-evidence/sensor-injection.txt 2>&1 &
preview_sensor_pid=$!
trap 'kill "$preview_sensor_pid" 2>/dev/null || true' EXIT
result=0
gradle :app:connectedDebugAndroidTest --stacktrace || result=$?
kill "$preview_sensor_pid" 2>/dev/null || true
adb logcat -d -v threadtime > startup-evidence/logcat.txt
adb shell run-as com.audiobookdownloader.preview cat files/audiobook-last-crash.txt > startup-evidence/startup-crash.txt || true
adb pull /sdcard/Android/data/com.audiobookdownloader.preview/files/ startup-evidence/app-files || true
adb pull /sdcard/Download/preview-startup-failure.png startup-evidence/ || true
adb pull /sdcard/Download/preview-startup-success.png startup-evidence/ || true
adb pull /sdcard/Download/preview-page-curl.png startup-evidence/ || true
adb pull /sdcard/Download/preview-sleep-shake-success.png startup-evidence/ || true
exit "$result"
