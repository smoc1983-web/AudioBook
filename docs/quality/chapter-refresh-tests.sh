#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../.." && pwd)
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
jars="${QUALITY_JUNIT_CLASSPATH:?Set JUnit, Hamcrest, JSON and Jsoup classpath}"
cd "$root"
javac -cp "$jars" -d "$classes" current/app/src/main/java/com/SearchBookIndex.java app/src/main/java/com/audiobookdownloader/ChapterRefresh*.java app/src/test/java/com/audiobookdownloader/ChapterRefresh*Test.java
java -cp "$classes:$jars" org.junit.runner.JUnitCore com.audiobookdownloader.ChapterRefreshRulesTest com.audiobookdownloader.ChapterRefreshParserTest
