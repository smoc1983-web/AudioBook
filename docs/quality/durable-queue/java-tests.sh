#!/usr/bin/env bash
set -euo pipefail
: "${QUALITY_JUNIT_CLASSPATH:?Set JUnit 4 and Hamcrest classpath}"
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -source 17 -target 17 -cp "$QUALITY_JUNIT_CLASSPATH" -d "$classes" \
 app/src/main/java/com/audiobookdownloader/ChapterIdentity.java \
 app/src/main/java/com/audiobookdownloader/QueueSnapshotRules.java \
 app/src/main/java/com/audiobookdownloader/QueueRecoveryRules.java \
 app/src/test/java/com/audiobookdownloader/QueueSnapshotRulesTest.java \
 app/src/test/java/com/audiobookdownloader/QueueRecoveryRulesTest.java
java_args=()
if [[ -n "${QUALITY_JACOCO_AGENT:-}" ]]; then
 mkdir -p target/site/jacoco
 java_args+=("-javaagent:$QUALITY_JACOCO_AGENT=destfile=target/site/jacoco/queue.exec,append=false")
fi
java "${java_args[@]}" -cp "$classes:$QUALITY_JUNIT_CLASSPATH" org.junit.runner.JUnitCore com.audiobookdownloader.QueueSnapshotRulesTest com.audiobookdownloader.QueueRecoveryRulesTest
if [[ -n "${QUALITY_JACOCO_CLI:-}" ]]; then
 java -jar "$QUALITY_JACOCO_CLI" report target/site/jacoco/queue.exec \
  --classfiles "$classes/com/audiobookdownloader/QueueSnapshotRules.class" \
  --classfiles "$classes/com/audiobookdownloader/QueueRecoveryRules.class" \
  --sourcefiles app/src/main/java --xml target/site/jacoco/jacoco.xml
fi
