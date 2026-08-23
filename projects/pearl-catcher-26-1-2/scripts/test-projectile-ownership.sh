#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TRACKER="$ROOT/src/client/java/studio/pixelied/pearlcatch/ProjectileTracker.java"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
ATTEMPT="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchAttemptTracker.java"
[ -f "$TRACKER" ] || { echo 'MISSING ProjectileTracker'; exit 1; }
grep -F 'projectile.getOwner() == player' "$TRACKER" >/dev/null || { echo 'MISSING exact local owner gate'; exit 1; }
grep -F 'ProjectileTracker.isOwnedByLocal' "$EXEC" >/dev/null || { echo 'MISSING entity-load owner gate'; exit 1; }
grep -F 'ProjectileTracker.findNewOwned' "$EXEC" >/dev/null || { echo 'MISSING owner-filtered acquisition'; exit 1; }
grep -F 'firstExistingWindHazard' "$ATTEMPT" >/dev/null || { echo 'MISSING foreign wind interference visibility'; exit 1; }
echo 'Projectile ownership regression: PASS'
