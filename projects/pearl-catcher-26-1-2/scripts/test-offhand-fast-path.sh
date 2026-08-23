#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
need() { grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2"; exit 1; }; }
need "$MODE" 'InteractionHand.OFF_HAND'
need "$MODE" 'getOffhandItem'
need "$MODE" 'resolveItemLocation'
need "$MODE" 'useHandAtRotation'
if grep -F 'NO_ENDER_PEARL_IN_HOTBAR' "$MODE" >/dev/null; then
  echo 'FORBIDDEN: hotbar-only pearl gate'; exit 1
fi
if grep -F 'NO_WIND_CHARGE_IN_HOTBAR' "$MODE" >/dev/null; then
  echo 'FORBIDDEN: hotbar-only wind gate'; exit 1
fi
echo 'Fast offhand regression: PASS'
