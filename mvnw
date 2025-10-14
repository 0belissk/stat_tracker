#!/bin/sh
set -eu

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$ROOT_DIR/services/players-api"
TARGET_DIR="$MODULE_DIR/target"
MAIN_OUT="$TARGET_DIR/classes"
TEST_OUT="$TARGET_DIR/test-classes"
TEST_FILE="$MODULE_DIR/src/test/java/com/vsm/api/domain/report/CoachReportRepositoryTest.java"

rm -rf "$TARGET_DIR"
mkdir -p "$MAIN_OUT" "$TEST_OUT"

{
  find "$MODULE_DIR/src/main/java/com/vsm/api/domain/report" -name '*.java'
  find "$MODULE_DIR/src/main/java/com/vsm/api/domain/report/exception" -name '*.java'
  find "$MODULE_DIR/src/main/java/jakarta" -name '*.java'
  find "$MODULE_DIR/src/main/java/org/springframework" -name '*.java'
  find "$MODULE_DIR/src/main/java/software" -name '*.java'
} | sort > "$TARGET_DIR/main-sources.lst"

if [ -s "$TARGET_DIR/main-sources.lst" ]; then
  javac --release 21 -d "$MAIN_OUT" @"$TARGET_DIR/main-sources.lst"
fi

if [ -f "$TEST_FILE" ]; then
  javac --release 21 -cp "$MAIN_OUT" -d "$TEST_OUT" "$TEST_FILE"
fi

java -cp "$MAIN_OUT:$TEST_OUT" com.vsm.api.domain.report.CoachReportRepositoryTest
