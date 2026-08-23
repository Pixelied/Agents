#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
INPUT="$ROOT/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
[ -f "$INPUT" ] || { echo 'MISSING VanillaInputExecutor'; exit 1; }
grep -F 'clientTick > current.deadlineClientTick()' "$INPUT" >/dev/null || { echo 'MISSING timeout release'; exit 1; }
grep -F 'LegitSilentUseBridge.cancel()' "$INPUT" >/dev/null || { echo 'MISSING silent-use cleanup'; exit 1; }
grep -F 'int windActionPreviousSlot = player.getInventory().getSelectedSlot()' "$EXEC" >/dev/null || { echo 'MISSING per-action Fast restore snapshot'; exit 1; }
echo 'Input ownership hardening regression: PASS'
