#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WIN="$ROOT/src/main/java/studio/pixelied/pearlcatch/core/ServerTimingWindow.java"
EXEC="$ROOT/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java"
[ -f "$WIN" ] || { echo 'MISSING ServerTimingWindow'; exit 1; }
grep -F 'fromRoundTripLatencyMs' "$WIN" >/dev/null || { echo 'MISSING RTT conversion'; exit 1; }
grep -F 'getLatency()' "$EXEC" >/dev/null || { echo 'MISSING live connection latency'; exit 1; }
grep -F 'NETWORK_TIMING_ROTATION_TOLERANCE_DEGREES' "$EXEC" >/dev/null || { echo 'MISSING timing agreement tolerance'; exit 1; }
grep -F 'WAIT_NETWORK_TIMING_UNCERTAIN' "$EXEC" >/dev/null || { echo 'MISSING fail-closed timing state'; exit 1; }
echo 'Server timing window regression: PASS'
