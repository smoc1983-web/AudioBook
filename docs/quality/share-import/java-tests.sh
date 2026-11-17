#!/usr/bin/env bash
set -euo pipefail
: "${QUALITY_JUNIT_CLASSPATH:?Set JUnit 4 and Hamcrest classpath}"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$QUALITY_JUNIT_CLASSPATH" -d "$classes" \
 app/src/main/java/com/audiobookdownloader/ChapterIdentity.java app/src/main/java/com/audiobookdownloader/SharedAudioRules.java app/src/test/java/com/audiobookdownloader/SharedAudioRulesTest.java
args=()
if [[ -n "${QUALITY_JACOCO_AGENT:-}" ]]; then
 mkdir -p target/site/jacoco
 args+=("-javaagent:$QUALITY_JACOCO_AGENT=destfile=target/site/jacoco/share-import.exec,append=false")
fi
java "${args[@]}" -cp "$classes:$QUALITY_JUNIT_CLASSPATH" org.junit.runner.JUnitCore com.audiobookdownloader.SharedAudioRulesTest
if [[ -n "${QUALITY_JACOCO_CLI:-}" ]]; then
 java -jar "$QUALITY_JACOCO_CLI" report target/site/jacoco/share-import.exec --classfiles "$classes/com/audiobookdownloader/SharedAudioRules.class" --sourcefiles app/src/main/java --xml target/site/jacoco/jacoco.xml
fi
