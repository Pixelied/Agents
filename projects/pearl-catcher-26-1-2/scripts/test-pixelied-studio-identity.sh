#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# Split literals so this verifier does not itself contain the forbidden legacy identity strings.
TOKENS=("ad""rien" "mou""zon" "ad""rienmou""zon" "chat""gpt" "chat"" gpt" "co""dex" "open""ai")
FAILED=0
for token in "${TOKENS[@]}"; do
  if grep -RniF --exclude='test-pixelied-studio-identity.sh' --exclude-dir=.git "$token" "$ROOT" >/tmp/pearlcatch_identity_hits.$$ 2>/dev/null; then
    echo "FAIL: legacy identity text remains"
    cat /tmp/pearlcatch_identity_hits.$$
    FAILED=1
  fi
  if find "$ROOT" -depth -iname "*$token*" -print | grep . >/tmp/pearlcatch_identity_paths.$$ 2>/dev/null; then
    echo "FAIL: legacy identity path remains"
    cat /tmp/pearlcatch_identity_paths.$$
    FAILED=1
  fi
done
rm -f /tmp/pearlcatch_identity_hits.$$ /tmp/pearlcatch_identity_paths.$$
[ "$FAILED" -eq 0 ] || exit 1

grep -F 'Pixelied Studio' "$ROOT/src/main/resources/fabric.mod.json" >/dev/null || {
  echo 'FAIL: visible author identity is not Pixelied Studio'; exit 1;
}
grep -R '^package studio\.pixelied\.pearlcatch' "$ROOT/src/main/java" "$ROOT/src/client/java" "$ROOT/src/test/java" >/dev/null || {
  echo 'FAIL: Pixelied Studio Java package namespace missing'; exit 1;
}
echo 'Pixelied Studio identity regression: PASS'
