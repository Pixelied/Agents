#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CLIENT="$ROOT/src/client/java/studio/pixelied/pearlcatch/PearlCatchClient.java"
INPUT="$ROOT/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java"
need(){ grep -F "$2" "$1" >/dev/null || { echo "MISSING: $2 in ${1#$ROOT/}"; exit 1; }; }
need "$CLIENT" 'drainSyntheticControlEchoes'
need "$INPUT" 'KeyMappingHelper.getBoundKeyOf'
need "$INPUT" 'PearlCatchClient.drainSyntheticControlEchoes(key)'
echo 'Legit remapped-key conflict regression: PASS'
