#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-evidence
# Same logical 411dp-wide display, fewer pixels for software rendering on CI.
timeout 15s adb shell wm size 360x800
timeout 15s adb shell wm density 140
# This app has no Play Services dependency; keep first-boot background setup from restarting.
timeout 15s adb shell pm disable-user --user 0 com.google.android.gms || true
timeout 15s adb shell pm disable-user --user 0 com.google.android.apps.messaging || true
timeout 15s adb shell pm disable-user --user 0 com.google.android.dialer || true
timeout 15s adb shell setprop debug.hwui.renderer skiagl
timeout 15s adb shell input keyevent 82
timeout 15s adb logcat -c
timeout 15s adb shell rm -f /sdcard/Download/preview-sleep-shake-ready
timeout 15s adb emu sensor status > startup-evidence/sensor-status.txt || true
(
  for preview_attempt in $(seq 1 600); do
    if timeout 5s adb shell test -f /sdcard/Download/preview-sleep-shake-ready; then
      for preview_pulse in $(seq 1 6); do
        timeout 5s adb emu sensor set acceleration 14:0:9.80665
        sleep 0.10
        timeout 5s adb emu sensor set acceleration 0:0:9.80665
        sleep 0.12
        timeout 5s adb emu sensor set acceleration -14:0:9.80665
        sleep 0.10
        timeout 5s adb emu sensor set acceleration 0:0:9.80665
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
timeout 10m gradle -Dorg.gradle.jvmargs="-Xmx1536m -Dfile.encoding=UTF-8" :app:connectedDebugAndroidTest --stacktrace || result=$?
kill "$preview_sensor_pid" 2>/dev/null || true
timeout 15s adb logcat -d -v threadtime > startup-evidence/logcat.txt || true
timeout 15s adb shell run-as com.audiobookdownloader.preview cat files/audiobook-last-crash.txt > startup-evidence/startup-crash.txt || true
timeout 15s adb pull /sdcard/Android/data/com.audiobookdownloader.preview/files/ startup-evidence/app-files || true
timeout 15s adb pull /sdcard/Download/preview-startup-failure.png startup-evidence/ || true
timeout 15s adb pull /sdcard/Download/preview-startup-success.png startup-evidence/ || true
timeout 15s adb pull /sdcard/Download/preview-page-curl.png startup-evidence/ || true
timeout 15s adb pull /sdcard/Download/preview-sleep-shake-success.png startup-evidence/ || true
timeout 15s adb pull /sdcard/Download/preview-page-curl-software.png startup-evidence/ || true
timeout 15s adb pull /sdcard/Download/preview-player-software.png startup-evidence/ || true
for preview_direction in left right; do
  for preview_frame in 0 1 2 3; do
    timeout 15s adb pull "/sdcard/Download/curl-cylinder-${preview_direction}-${preview_frame}.png" startup-evidence/ || true
  done
done
for widget_kind in 0 1 2; do
  timeout 15s adb pull "/sdcard/Download/preview-widget-${widget_kind}.png" startup-evidence/ || true
done
free -m > startup-evidence/host-memory.txt
sudo dmesg -T | tail -100 > startup-evidence/host-kernel.txt || true
exit "$result"
