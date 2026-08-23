#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
SCREEN="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java"
CLIENT="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchClient.java"
need() { grep -F "$1" "$2" >/dev/null || { echo "MISSING: $1 in $2"; exit 1; }; }
forbid() { if grep -F "$1" "$2" >/dev/null; then echo "FORBIDDEN: $1 in $2"; exit 1; fi; }
need 'GeneralCatchSolver' "$MODE"
for x in HybridCatchPolicy PearlTrajectoryPlanner ReactiveWindSolver 'PREDICTIVE_RELIABLE' 'PREDICTIVE_BEST_EFFORT' 'REACTIVE_REAL_VELOCITY'; do
  forbid "$x" "$MODE"
done
forbid 'Wind timing:' "$SCREEN"
forbid 'Max prediction horizon' "$SCREEN"

CORE="$ROOT/src/main/java/studio/pixelied/pearlcatch/core"
for legacy in HybridCatchPolicy.java JointInterceptSolver.java PearlTrajectoryPlanner.java ReactiveWindSolver.java; do
  if [ -e "$CORE/$legacy" ]; then echo "FORBIDDEN LEGACY SOLVER FILE: $legacy"; exit 1; fi
done

# 2.4 controls and overlapping-attempt architecture.
need 'verticalCatchKey' "$CLIENT"
need 'GLFW.GLFW_KEY_H' "$CLIENT"
need 'GLFW.GLFW_KEY_B' "$CLIENT"
need 'triggerVerticalPearlCatch' "$CLIENT"
need 'G = pearl catch • H = vertical catch • B = debug sweep' "$SCREEN"
need 'collisionClearance' "$MODE"
need 'List<TrackingShot>' "$MODE"
need 'List<PendingCatch>' "$MODE"
forbid 'private TrackingShot activeShot;' "$MODE"
forbid 'private PendingCatch pendingCatch;' "$MODE"
forbid 'a catch/debug shot is already being tracked' "$MODE"

echo 'Single-solver architecture test: PASS'
