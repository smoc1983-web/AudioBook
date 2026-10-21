#!/usr/bin/env bash
set -euo pipefail
mkdir -p startup-evidence
adb install -r "$1"
adb shell pm grant com.audiobookdownloader.preview android.permission.READ_MEDIA_AUDIO
adb shell pm grant com.audiobookdownloader.preview android.permission.POST_NOTIFICATIONS
adb logcat -c
adb shell am start -W -n com.audiobookdownloader.preview/com.audiobookdownloader.MainActivity
sleep 12
adb shell uiautomator dump /sdcard/startup.xml
adb pull /sdcard/startup.xml startup-evidence/startup.xml
adb exec-out screencap -p > startup-evidence/startup.png
adb shell run-as com.audiobookdownloader.preview cat files/audiobook-last-crash.txt > startup-evidence/startup-crash.txt || true
adb logcat -d -v threadtime > startup-evidence/logcat.txt
cat startup-evidence/startup-crash.txt
python - <<'PY'
from pathlib import Path
text=Path('startup-evidence/startup.xml').read_text()
assert 'Не удалось открыть главный экран' not in text, 'Released APK entered startup recovery; see saved stack'
assert 'Жанры' in text or 'Источники' in text, 'Main screen did not become visible'
PY
