#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
ATTEMPT="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchAttemptTracker.java"
for token in 'cancelOwner(' 'cancelAllOwnedState(' 'DISABLED_CLEANUP' 'PASSENGER_MOVEMENT_UNSUPPORTED'; do
  grep -F "$token" "$EXEC" >/dev/null || { echo "MISSING: $token"; exit 1; }
done
grep -F 'final long attemptId' "$ATTEMPT" >/dev/null || { echo 'MISSING tracking owner token'; exit 1; }
echo 'Lifecycle hardening regression: PASS'
