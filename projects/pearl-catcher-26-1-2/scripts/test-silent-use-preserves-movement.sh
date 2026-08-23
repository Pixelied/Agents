#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
METHOD=$(awk '/private boolean useHandAtRotation\(/,/^    }/' "$FILE")
if grep -F 'new ServerboundMovePlayerPacket.Rot' <<<"$METHOD" >/dev/null; then
  echo 'FAIL: useHandAtRotation sends a standalone Rot packet before use-item; this zeroes ServerPlayer known movement.'
  exit 1
fi
grep -F 'restoreServerRotationAfterFinalUse' "$FILE" >/dev/null || { echo 'FAIL: final post-sequence server rotation restore missing'; exit 1; }
if grep -R -F 'sendSilentReturnRotation' "$ROOT/src" >/dev/null; then
  echo 'FAIL: dead sendSilentReturnRotation setting survived the movement-preservation fix.'
  exit 1
fi
echo 'Silent-use movement preservation regression: PASS'
