#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MODE="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchDebug.java"
for f in pearlItemPrepRequestedClientTick pearlItemPrepConfirmedClientTick windItemPrepRequestedClientTick windItemPrepConfirmedClientTick; do
  grep -F "$f" "$MODE" >/dev/null || { echo "MISSING TRACE FIELD: $f"; exit 1; }
done
grep -F 'execution=itemSwitchMode=' "$MODE" >/dev/null || { echo 'MISSING text execution trace'; exit 1; }
echo 'Execution trace regression: PASS'
