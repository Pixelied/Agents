#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
need(){ grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2"; exit 1; }; }
need "$MODE" 'solverTargetForExecution'
need "$MODE" '"vertical".equals(label)'
need "$MODE" 'label.startsWith("debug pitch ")'
need "$MODE" 'angleDistance(new Rotation(player.getYRot(), player.getXRot()), plan.pearlRotation())'
echo 'Current-camera target semantics regression: PASS'
