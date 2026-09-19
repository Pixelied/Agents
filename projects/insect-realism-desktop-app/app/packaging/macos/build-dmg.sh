#!/bin/bash
# Native Apple Silicon/Intel package build. No signing secrets are printed or bundled.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
APP="$ROOT/app"
[[ "$(uname -s)" == "Darwin" ]] || { echo 'A macOS host with Xcode Command Line Tools is required.' >&2; exit 2; }
TARGET="${TARGET:-aarch64-apple-darwin}"
case "$TARGET" in aarch64-apple-darwin|x86_64-apple-darwin) ;; *) echo 'Unsupported macOS target' >&2; exit 2;; esac
export MACOSX_DEPLOYMENT_TARGET="${MACOSX_DEPLOYMENT_TARGET:-13.0}"
VERSION="$(python3 -c 'import tomllib,sys; print(tomllib.load(open(sys.argv[1],"rb"))["workspace"]["package"]["version"])' "$APP/Cargo.toml")"
DEST="${OUTPUT_DIR:-$ROOT/dist/macos-$TARGET-$VERSION}"
mkdir -p "$DEST"
DEST="$(cd "$DEST" && pwd)"
STAGE="$(mktemp -d "$DEST/.stage.XXXXXX")"
trap 'rm -rf "$STAGE"' EXIT
cd "$APP"
cargo build --locked --release -p desktop-app --target "$TARGET"
CARGO_OUT="$(python3 -c 'import json,subprocess; print(json.loads(subprocess.check_output(["cargo","metadata","--no-deps","--format-version=1"]))["target_directory"])')"
python3 "$ROOT/scripts/collect_licenses.py" --manifest "$APP/Cargo.toml" --target "$TARGET" --output "$STAGE/notices"
mkdir "$STAGE/image"
BUNDLE="$STAGE/image/Insect Realism.app"
python3 "$ROOT/scripts/stage_package.py" --platform macos --binary "$CARGO_OUT/$TARGET/release/desktop-app" --output "$BUNDLE" --licenses "$STAGE/notices"
IDENTITY="${MACOS_SIGN_IDENTITY:--}"
if [[ "$IDENTITY" == '-' ]]; then
    codesign --force --sign - "$BUNDLE"
else
    # No special entitlements are required; no screen recording/accessibility entitlement is requested.
    codesign --force --options runtime --timestamp --sign "$IDENTITY" "$BUNDLE"
fi
codesign --verify --strict --verbose=2 "$BUNDLE"
python3 "$ROOT/scripts/verify_package.py" "$BUNDLE" --platform macos --json "$DEST/bundle-inspection.json"
ln -s /Applications "$STAGE/image/Applications"
cp "$ROOT/docs/INSTALL.md" "$STAGE/image/INSTALL.txt"
python3 "$ROOT/scripts/verify_package.py" "$STAGE/image" --write-manifest --verify-manifest
DMG="$DEST/InsectRealism-$VERSION-$TARGET.dmg"
hdiutil create -volname 'Insect Realism' -srcfolder "$STAGE/image" -ov -format UDZO "$DMG"
hdiutil verify "$DMG"
if [[ -n "${NOTARY_KEYCHAIN_PROFILE:-}" ]]; then
    [[ "$IDENTITY" != '-' ]] || { echo 'Notarization requires a Developer ID identity, not ad-hoc signing.' >&2; exit 2; }
    xcrun notarytool submit "$DMG" --keychain-profile "$NOTARY_KEYCHAIN_PROFILE" --wait
    xcrun stapler staple "$DMG"
    xcrun stapler validate "$DMG"
fi
# Export a real .app archive as well as the DMG; do not mislabel it as notarized when credentials were absent.
ditto -c -k --sequesterRsrc --keepParent "$BUNDLE" "$DEST/InsectRealism-$VERSION-$TARGET.app.zip"
(cd "$DEST"; shasum -a 256 ./*.dmg ./*.app.zip > SHA256SUMS.txt)
printf 'Built %s\nSigning identity mode: %s\nNative interactive acceptance is still separate.\n' "$DMG" "$([[ "$IDENTITY" == '-' ]] && echo ad-hoc || echo Developer-ID)"
