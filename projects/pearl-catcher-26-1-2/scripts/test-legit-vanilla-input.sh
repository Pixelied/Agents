#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
INPUT="$ROOT/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java"
BRIDGE="$ROOT/src/client/java/studio/pixelied/pearlcatch/LegitSilentUseBridge.java"
MIXIN="$ROOT/src/client/java/studio/pixelied/pearlcatch/mixin/MinecraftUseMixin.java"
MIXJSON="$ROOT/src/main/resources/pearlcatch.mixins.json"
MODJSON="$ROOT/src/main/resources/fabric.mod.json"
need() { grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2 in ${1#$ROOT/}"; exit 1; }; }
need "$INPUT" 'KeyMapping.click'
need "$INPUT" 'KeyMappingHelper.getBoundKeyOf'
need "$INPUT" 'keyHotbarSlots'
need "$INPUT" 'keyUse'
need "$INPUT" 'keySwapOffhand'
need "$INPUT" 'HitResult.Type.MISS'
[ -f "$BRIDGE" ] || { echo 'MISSING bridge'; exit 1; }
[ -f "$MIXIN" ] || { echo 'MISSING mixin'; exit 1; }
[ -f "$MIXJSON" ] || { echo 'MISSING mixin json'; exit 1; }
need "$MODJSON" 'pearlcatch.mixins.json'
need "$MIXIN" 'LegitSilentUseBridge'
need "$MIXIN" 'startUseItem'
need "$MIXIN" 'cancellable = true'
need "$MIXIN" 'ci.cancel()'
need "$BRIDGE" 'boolean beforeVanillaUse'
LEGIT_BLOCK=$(awk '/private boolean startLegitPearlCatch/{p=1} p{print} /\/\* END LEGIT EXECUTION \*\//{p=0}' "$EXEC")
REPLAN_BLOCK=$(awk '/private boolean replanPendingCatch/{p=1} p{print} /private void completeLegitWindObservation/{p=0}' "$EXEC")
if printf '%s\n%s\n' "$LEGIT_BLOCK" "$REPLAN_BLOCK" | grep -F 'gameMode.useItem' >/dev/null; then
  echo 'FORBIDDEN: Legit path directly invokes gameMode.useItem'; exit 1
fi
need "$EXEC" 'queueLegitUse(mc, player, pending.attemptId'
need "$EXEC" 'WAIT_PLAYER_USING_ITEM_BEFORE_PEARL'
need "$EXEC" 'WAIT_PLAYER_USING_ITEM_BEFORE_WIND'
need "$EXEC" 'WAIT_SCREEN_CANCELLED_QUEUED_WIND_USE'
need "$EXEC" 'WAIT_SCREEN_CANCELLED_QUEUED_PEARL_USE'
need "$INPUT" 'discardQueuedInputForScreen'
echo 'Legit vanilla input regression: PASS'
