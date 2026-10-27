#!/bin/bash
set -euo pipefail
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
jars="${QUALITY_JUNIT_CLASSPATH:?Set JUnit 4.13.2 and Hamcrest 1.3 jar classpath}"
java --add-modules jdk.compiler com.sun.tools.javac.Main -cp "$jars" -d "$classes" current/app/src/main/java/com/SearchBookIndex.java app/src/main/java/com/audiobookdownloader/ContinuationRules.java app/src/test/java/com/audiobookdownloader/ContinuationRulesTest.java
java -cp "$classes:$jars" org.junit.runner.JUnitCore com.audiobookdownloader.ContinuationRulesTest
