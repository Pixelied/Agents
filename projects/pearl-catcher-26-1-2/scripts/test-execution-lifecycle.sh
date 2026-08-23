#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
need() { grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2"; exit 1; }; }
need "$MODE" 'mc.screen != null'
need "$MODE" 'mc.getOverlay() != null'
need "$MODE" 'isOnCooldown'
need "$MODE" 'resetExecutionState'
need "$MODE" 'movementEstimatorPlayerId'
need "$MODE" 'getSelectedSlot() != pending.pearlSwapSlot'
need "$MODE" 'firstExistingWindHazard'
need "$MODE" 'nextAttemptId'
need "$MODE" 'VanillaInputExecutor vanillaInput'
need "$MODE" 'legitPearlLaunches.add'
need "$MODE" 'pendingCatches.add'
# No old single-shot busy gate may reject G/H just because projectiles are alive.
if grep -E 'if \(!activeShots\.isEmpty\(\)\).*return|if \(!pendingCatches\.isEmpty\(\)\).*return' "$MODE" >/dev/null; then
  echo 'FORBIDDEN: global active-shot busy gate'; exit 1
fi
echo 'Execution lifecycle regression: PASS'
