#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
BRIDGE="$ROOT/src/client/java/studio/pixelied/pearlcatch/LegitSilentUseBridge.java"
grep -F 'restoreServerRotationAfterFinalUse' "$EXEC" >/dev/null || { echo 'MISSING final server rotation restore'; exit 1; }
grep -F 'serverRotationNeedsRestore' "$EXEC" >/dev/null || { echo 'MISSING attempt-owned restore flag'; exit 1; }
grep -F 'restoreServerAfterUse' "$BRIDGE" >/dev/null || { echo 'MISSING Legit final-use restoration ownership'; exit 1; }
grep -F 'ServerboundMovePlayerPacket.Rot' "$BRIDGE" >/dev/null || { echo 'MISSING post-use server rotation packet'; exit 1; }
METHOD=$(awk '/private boolean useHandAtRotation\(/,/^    }/' "$EXEC")
if grep -F 'ServerboundMovePlayerPacket.Rot' <<<"$METHOD" >/dev/null; then echo 'FAIL: low-level silent use restores too early'; exit 1; fi
echo 'Rotation lifecycle hardening regression: PASS'
