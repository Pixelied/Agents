#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
INPUT="$ROOT/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java"
[ -f "$INPUT" ] || { echo 'MISSING: VanillaInputExecutor.java'; exit 1; }
grep -F 'private final VanillaInputExecutor vanillaInput' "$EXEC" >/dev/null || { echo 'MISSING executor delegation'; exit 1; }
grep -F 'LEGIT_CONFIRM_TIMEOUT_TICKS' "$INPUT" >/dev/null || { echo 'MISSING bounded timeout'; exit 1; }
grep -F 'deadlineClientTick' "$INPUT" >/dev/null || { echo 'MISSING lease deadline'; exit 1; }
grep -F 'QueueUseResult' "$INPUT" >/dev/null || { echo 'MISSING queued-use result'; exit 1; }
grep -F 'cancelOwner' "$INPUT" >/dev/null || { echo 'MISSING owner cancellation'; exit 1; }
grep -F 'PearlCatchClient.drainSyntheticControlEchoes(key)' "$INPUT" >/dev/null || { echo 'MISSING remapped-key echo drain'; exit 1; }
if grep -F 'record LegitInputLease' "$EXEC" >/dev/null; then echo 'FAIL: input lease still owned by CatchCoordinator'; exit 1; fi
echo 'Input executor decomposition regression: PASS'
