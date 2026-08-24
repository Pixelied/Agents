#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SAFE="$ROOT/src/client/java/studio/pixelied/pearlcatch/RuntimePathSafety.java"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
[ -f "$SAFE" ] || { echo 'MISSING RuntimePathSafety'; exit 1; }
for token in 'clipIncludingBorder' 'ProjectileUtil.getEntityHitResult' 'FluidTags.WATER' 'Blocks.BUBBLE_COLUMN' 'collisionMargin(startingTickAge + segment - 1)'; do
  grep -F "$token" "$SAFE" >/dev/null || { echo "MISSING: $token"; exit 1; }
done
grep -F 'RuntimePathSafety.checkPearl' "$EXEC" >/dev/null || { echo 'MISSING pearl runtime safety gate'; exit 1; }
grep -F 'RuntimePathSafety.checkWind' "$EXEC" >/dev/null || { echo 'MISSING wind runtime safety gate'; exit 1; }
grep -F 'completedTicks' "$EXEC" | grep -F 'RuntimePathSafety' >/dev/null || { echo 'MISSING observed pearl age passed to path safety'; exit 1; }
echo 'Runtime path safety regression: PASS'
