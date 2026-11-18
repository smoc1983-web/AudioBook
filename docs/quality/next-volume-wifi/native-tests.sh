#!/usr/bin/env bash
set -euo pipefail
api=${1:?Pass emulator API level}
case "$api" in 23|36) ;; *) echo 'Unsupported test API' >&2; exit 2;; esac
# emulator-runner invokes separate script lines in separate shells. Keep the
# class selection and Gradle invocation in one process, never an empty filter.
tests=com.audiobookdownloader.NextVolumeWifiAndroidTest,com.audiobookdownloader.NextVolumeWifiPanelTest,com.audiobookdownloader.BookMetadataPlaybackTest,com.audiobookdownloader.BookMetadataAndroidTest,com.audiobookdownloader.BookMetadataPanelTest,com.audiobookdownloader.LibraryRootsTest,com.audiobookdownloader.LibraryRootsPanelTest,com.audiobookdownloader.LocalContinuationPlaybackTest,com.audiobookdownloader.ShortAudioValidationTest,com.audiobookdownloader.AudioValidationTest#contentDeterminesFormatForAllExistingAudioContainers,com.audiobookdownloader.AudioValidationTest#errorPagesPlaylistsTruncationAndWrongSizeAreRejected,com.audiobookdownloader.SharedAudioImportTest,com.audiobookdownloader.SharedAudioPanelTest,com.audiobookdownloader.SourceHealthAndroidTest,com.audiobookdownloader.SourceHealthPanelTest,com.audiobookdownloader.SeekHistoryAndroidTest,com.audiobookdownloader.SeekHistoryPanelTest,com.audiobookdownloader.Stage8ReliabilityTest,com.audiobookdownloader.Stage8ProgressFolderTest,com.audiobookdownloader.Stage8FolderPanelStateTest,com.audiobookdownloader.Stage8ExportStateTest,com.audiobookdownloader.LibraryIdentityAndroidTest
if [[ "$api" -ge 29 ]]; then tests="$tests,com.audiobookdownloader.IdlePlaybackTest"; fi
[[ -n "$tests" ]]
echo "Selected native test classes: $tests"
report=app/build/reports/next-volume-wifi-native
mkdir -p "$report"
collect() {
  timeout 20s adb logcat -d -v threadtime > "$report/logcat.txt" 2>&1 || true
  timeout 20s adb shell dumpsys activity top > "$report/activity.txt" 2>&1 || true
  timeout 20s adb shell cat /data/anr/traces.txt > "$report/anr.txt" 2>&1 || true
  if [[ -d app/build/outputs/androidTest-results ]]; then cp -R app/build/outputs/androidTest-results "$report/normal"; fi
}
trap collect EXIT
# Bound a stuck test without treating it as passed. Preserve all class/method filters.
timeout --signal=TERM --kill-after=15s 8m gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$tests" -Pandroid.testInstrumentationRunnerArguments.timeout_msec=120000 --max-workers=2 --console=plain
