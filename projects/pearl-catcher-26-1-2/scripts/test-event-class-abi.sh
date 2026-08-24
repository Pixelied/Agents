#!/usr/bin/env bash
set -euo pipefail
TARGET="${1:?jar or classes path}"
if [[ "$TARGET" != /* ]]; then TARGET="$(pwd)/$TARGET"; fi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
if [ -f "$TARGET" ]; then
  (cd "$TMP" && unzip -q "$TARGET")
  CP="$TMP"
else
  CP="$TARGET"
fi
CLIENT=$(find "$CP" -name 'PearlCatchClient.class' -print -quit)
[ -n "$CLIENT" ] || { echo 'PearlCatchClient.class not found'; exit 1; }
REL=${CLIENT#"$CP"/}; CLS=${REL%.class}; CLS=${CLS//\//.}
javap -classpath "$CP" -v "$CLS" > "$TMP/dump.txt"
if grep -E 'InterfaceMethodref.*net/fabricmc/fabric/api/event/Event\.register:' "$TMP/dump.txt" >/dev/null; then
  echo 'FAIL: Event.register encoded as InterfaceMethodref'
  exit 1
fi
grep -E 'Methodref.*net/fabricmc/fabric/api/event/Event\.register:' "$TMP/dump.txt" >/dev/null || {
  echo 'FAIL: Event.register class Methodref missing'; exit 1;
}
echo 'Fabric Event class ABI regression: PASS'
