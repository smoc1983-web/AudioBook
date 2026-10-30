#!/usr/bin/env bash
set -euo pipefail
classes=$(mktemp -d)
trap 'rm -rf "$classes"' EXIT
jars="${QUALITY_JUNIT_CLASSPATH:?Set JUnit/Hamcrest/Jsoup/JSON jar classpath}"
java --add-modules jdk.compiler com.sun.tools.javac.Main -g -cp "$jars" -d "$classes" current/app/src/main/java/com/SearchBookIndex.java app/src/main/java/com/audiobookdownloader/SeriesAuthorityRules.java app/src/main/java/com/audiobookdownloader/BookDescriptionParser.java app/src/test/java/com/audiobookdownloader/BookDescriptionParserTest.java
java ${BOOK_DESCRIPTION_JAVA_AGENT:-} -cp "$classes:$jars" org.junit.runner.JUnitCore com.audiobookdownloader.BookDescriptionParserTest

if [[ -n "${BOOK_DESCRIPTION_COVERAGE_DIR:-}" ]]; then
  java -jar "${BOOK_DESCRIPTION_JACOCO_CLI:?Set JaCoCo CLI path}" report "$BOOK_DESCRIPTION_COVERAGE_DIR/jacoco.exec" --classfiles "$classes/com/audiobookdownloader/BookDescriptionParser.class" --sourcefiles app/src/main/java --xml "$BOOK_DESCRIPTION_COVERAGE_DIR/coverage.xml" --html "$BOOK_DESCRIPTION_COVERAGE_DIR/html"
fi
