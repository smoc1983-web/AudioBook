#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-evidence
adb logcat -c
result=0
gradle :app:connectedDebugAndroidTest --stacktrace || result=$?
adb logcat -d -v threadtime > startup-evidence/logcat.txt
adb shell run-as com.audiobookdownloader.preview cat files/audiobook-last-crash.txt > startup-evidence/startup-crash.txt || true
adb pull /sdcard/Android/data/com.audiobookdownloader.preview/files/ startup-evidence/app-files || true
adb pull /sdcard/Download/preview-startup-failure.png startup-evidence/ || true
adb pull /sdcard/Download/preview-startup-success.png startup-evidence/ || true
exit "$result"
