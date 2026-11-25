#!/usr/bin/env bash
set -euo pipefail

APK="dist-test/Audiobook-Test.apk"
BASELINE="prior-test/Audiobook-Test.apk"
PACKAGE="com.audiobookdownloader.test"
MAIN="com.audiobookdownloader.preview"

test -s "$APK"
adb wait-for-device
if [[ -f "$BASELINE" ]]; then
    adb install "$BASELINE"
    old_code="$(adb shell dumpsys package "$PACKAGE" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -n1)"
    test -n "$old_code"
    test "$old_code" -lt "$TEST_CODE"
    adb install -r "$APK"
else
    adb install "$APK"
fi

adb shell pm path "$PACKAGE" | grep -q 'package:'
adb shell dumpsys package "$PACKAGE" | grep -F "versionCode=$TEST_CODE"
adb shell am start -W -n "$PACKAGE/com.audiobookdownloader.MainActivity"

# Installing/updating Test must never install or overwrite the production package.
if adb shell pm list packages | grep -Fx "package:$MAIN"; then
    echo "Unexpected main AudioBook package present in isolated emulator" >&2
    exit 1
fi
echo "Verified independent Audiobook Test installation: $PACKAGE, versionCode=$TEST_CODE"
