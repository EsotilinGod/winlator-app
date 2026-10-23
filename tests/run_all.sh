#!/usr/bin/env bash
# Runs the host-side regression checks with the same arguments as the CI workflow.
# Requires python3, a JDK and a C compiler (cc). Does not exercise Android or a real GPU.
set -euo pipefail
cd "$(dirname "$0")/.."

JAVA_SRC=app/src/main/java/com/winlator
VORTEK=app/src/main/cpp/vortekrenderer
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

python3 tests/mali-14t/java/run.py "$JAVA_SRC"
python3 tests/sync-fence/run.py
python3 tests/document-import/run.py

javac -d "$TMP/dinput8" "$JAVA_SRC/core/DllOverrides.java" tests/dinput8/DllOverridesTest.java
java -cp "$TMP/dinput8" DllOverridesTest

javac -d "$TMP/hud" "$JAVA_SRC/core/FrameTiming.java" tests/hud/FrameTimingTest.java
java -cp "$TMP/hud" FrameTimingTest

python3 tests/mali-14t/native/run.py "$VORTEK"
python3 tests/vortek-wait/run.py
python3 tests/mali-memory/run.py "$VORTEK"
python3 tests/vortek-bc/run.py "$VORTEK"
echo "All host-side checks passed."
