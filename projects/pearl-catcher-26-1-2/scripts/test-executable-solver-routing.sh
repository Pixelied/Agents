#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
grep -F 'GeneralCatchSolver.solveExecutable(new GeneralCatchSolver.Request(' "$EXEC" >/dev/null || { echo 'MISSING executable solver route'; exit 1; }
echo 'Executable solver routing regression: PASS'
