#!/bin/bash
set -euo pipefail
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
jars="${QUALITY_JUNIT_CLASSPATH:?Set JUnit and Hamcrest classpath}"
javac -cp "$jars" -d "$classes" current/app/src/main/java/com/SearchBookIndex.java app/src/main/java/com/audiobookdownloader/SearchEditionIdentity.java app/src/test/java/com/audiobookdownloader/SearchEditionIdentityTest.java
java -cp "$classes:$jars" org.junit.runner.JUnitCore com.audiobookdownloader.SearchEditionIdentityTest
