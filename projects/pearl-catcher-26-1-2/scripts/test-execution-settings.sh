#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CFG="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java"
UI="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java"
need() { grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2 in ${1#$ROOT/}"; exit 1; }; }
need "$CFG" 'enum ItemSwitchMode'
need "$CFG" 'FAST'
need "$CFG" 'LEGIT'
need "$CFG" 'itemSwitchMode'
need "$UI" 'Item switching:'
need "$UI" 'Rotation:'
if grep -R -E 'class (ElytraPlanner|LegitPlanner|FastPlanner|OffhandPlanner)|/(ElytraPlanner|LegitPlanner|FastPlanner|OffhandPlanner)\.java' "$ROOT/src" >/dev/null; then
  echo 'FORBIDDEN: extra planner class'; exit 1
fi
echo 'Execution settings regression: PASS'

INPUT="$ROOT/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java"
if ! grep -F 'new CameraRestoreRequest(clientTick + 1' "$INPUT" >/dev/null; then
  echo 'FAIL: Legit Visible use does not restore the pre-use camera after vanilla consumes the queued Use key.'
  exit 1
fi
