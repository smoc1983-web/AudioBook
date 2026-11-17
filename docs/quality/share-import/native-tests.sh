#!/usr/bin/env bash
set -euo pipefail
api=${1:?Pass emulator API level}
case "$api" in 23|36) ;; *) echo 'Unsupported test API' >&2; exit 2;; esac
# emulator-runner invokes separate script lines in separate shells. Keep the
# class selection and Gradle invocation in one process, never an empty filter.
tests=com.audiobookdownloader.SharedAudioImportTest,com.audiobookdownloader.SharedAudioPanelTest,com.audiobookdownloader.SourceHealthAndroidTest,com.audiobookdownloader.SourceHealthPanelTest,com.audiobookdownloader.SeekHistoryAndroidTest,com.audiobookdownloader.SeekHistoryPanelTest,com.audiobookdownloader.Stage8ReliabilityTest,com.audiobookdownloader.Stage8ProgressFolderTest,com.audiobookdownloader.Stage8FolderPanelStateTest,com.audiobookdownloader.Stage8ExportStateTest,com.audiobookdownloader.LibraryIdentityAndroidTest
if [[ "$api" -ge 29 ]]; then tests="$tests,com.audiobookdownloader.IdlePlaybackTest"; fi
[[ -n "$tests" ]]
echo "Selected native test classes: $tests"
gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$tests" --max-workers=2 --console=plain
mkdir -p app/build/reports/share-import-native
cp -R app/build/outputs/androidTest-results app/build/reports/share-import-native/normal
