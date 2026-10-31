#!/usr/bin/env bash
set -euo pipefail
result=0
GITHUB_RUN_NUMBER=71 gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.audiobookdownloader.CompactPlayerTest,com.audiobookdownloader.SearchScrollTest,com.audiobookdownloader.SearchCollectionLayoutTest,com.audiobookdownloader.LibraryExperienceTest,com.audiobookdownloader.CollectionDownloadTest,com.audiobookdownloader.BookDescriptionTest,com.audiobookdownloader.CollectionSeriesTest,com.audiobookdownloader.EditionDownloadTest,com.audiobookdownloader.UpdateScreenTest,com.audiobookdownloader.WidgetPreviewTest --stacktrace || result=$?
mkdir -p compact-player-evidence
for skin in 1 5 10 150; do
  timeout 15s adb pull "/sdcard/Android/data/com.audiobookdownloader.preview/files/compact-player-theme-${skin}.png" compact-player-evidence/ || true
done
timeout 15s adb pull /sdcard/Android/data/com.audiobookdownloader.preview/files/compact-player-failure.xml compact-player-evidence/ || true
timeout 15s adb pull /sdcard/Android/data/com.audiobookdownloader.preview/files/compact-player-failure.png compact-player-evidence/ || true
timeout 15s adb shell run-as com.audiobookdownloader.preview cat files/audiobook-last-crash.txt > compact-player-evidence/saved-crash.txt || true
timeout 15s adb logcat -d -t 500 > compact-player-evidence/logcat.txt || true
exit "$result"
