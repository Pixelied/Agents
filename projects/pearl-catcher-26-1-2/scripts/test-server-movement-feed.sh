#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
CLIENT="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchClient.java"

grep -F 'ServerKnownMovementEstimator' "$MODE" >/dev/null || { echo 'FAIL: runtime has no server-known movement estimator'; exit 1; }
grep -E 'START_CLIENT_TICK\.register\((mode::beginClientTick|client -> mode\.beginClientTick\(client\))' "$CLIENT" >/dev/null || { echo 'FAIL: client does not sample player position at START_CLIENT_TICK'; exit 1; }
grep -F 'captureEndClientTick(client)' "$CLIENT" >/dev/null || { echo 'FAIL: client does not finalize server movement estimate before key-triggered uses'; exit 1; }
if grep -F 'VanillaProjectilePhysics.inheritedMotion(toCore(player.getKnownMovement())' "$MODE" >/dev/null; then
  echo 'FAIL: solver still feeds LocalPlayer.getKnownMovement directly'
  exit 1
fi
echo 'Server movement feed regression: PASS'
