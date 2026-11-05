#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
jars="${QUALITY_JUNIT_CLASSPATH:?Set JUnit and Hamcrest classpath}"
cd "$root"
javac -cp "$jars" -d "$classes" app/src/main/java/com/audiobookdownloader/PlaybackOptions.java app/src/test/java/com/audiobookdownloader/PlaybackOptionsTest.java
java -cp "$classes:$jars" org.junit.runner.JUnitCore com.audiobookdownloader.PlaybackOptionsTest
