#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT="$ROOT/src/client/java/studio/pixelied/pearlcatch"
MODE="$CLIENT/PearlCatchMode.java"
for file in CatchCoordinator.java VanillaInputExecutor.java ProjectileTracker.java RuntimePathSafety.java CatchAttemptTracker.java PearlCatchDebug.java; do
  [ -f "$CLIENT/$file" ] || { echo "MISSING: $file"; exit 1; }
done
lines=$(wc -l < "$MODE")
[ "$lines" -le 900 ] || { echo "FAIL: PearlCatchMode.java still has $lines lines (>900)"; exit 1; }
for bad in ElytraPlanner LegitPlanner FastPlanner OffhandPlanner ReactiveWindSolver HybridCatchPolicy PearlTrajectoryPlanner; do
  if grep -R -F "$bad" "$CLIENT" "$ROOT/src/main/java" >/dev/null; then
    echo "FORBIDDEN planner architecture: $bad"; exit 1
  fi
done
count=$(grep -R -l 'GeneralCatchSolver.solve' "$ROOT/src" --include='*.java' | wc -l | tr -d ' ')
[ "$count" -ge 1 ] || { echo 'FAIL: GeneralCatchSolver solve path missing'; exit 1; }

DEBUG="$CLIENT/PearlCatchDebug.java"
EXEC="$CLIENT/CatchCoordinator.java"
for method in updateClosest solverTrace showOverlay renderVisualization; do
  grep -F "$method" "$DEBUG" >/dev/null || { echo "MISSING debug ownership: $method"; exit 1; }
  if grep -E "(private|public|protected|static).*${method}\(" "$EXEC" >/dev/null; then
    echo "FAIL: $method still owned by CatchCoordinator"; exit 1
  fi
done
echo "Runtime decomposition regression: PASS (PearlCatchMode=${lines} lines)"
