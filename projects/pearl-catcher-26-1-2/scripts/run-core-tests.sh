#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/core-self-test"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT"/src/main/java/studio/pixelied/pearlcatch/core/*.java \
  "$ROOT"/src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java
java -cp "$OUT" studio.pixelied.pearlcatch.core.GeneralCatchSolverSelfTest
