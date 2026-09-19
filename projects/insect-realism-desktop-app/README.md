# Insect Realism Desktop Utility

A Rust menu-bar/tray utility that renders physically scaled procedural ants on
transparent monitor overlays. macOS and Windows are the native targets; Linux
is currently an offscreen developer harness, not a desktop overlay release.

This is the continuing project on `feat/insect-continuation-03-q7b4`, not a restart.
See `APP_EXECUTION.md` and `docs/CONTINUATION_STATUS.md` for exact source and test
results. Development packages are not a claim of native input or physical-size
qualification.

## Build and test

Use the Rust version pinned in `app/rust-toolchain.toml` (1.98.1), Python 3.11+
for release tools, and the appropriate native SDK for macOS or Windows.

```sh
cd app
cargo build --release --locked -p desktop-app
cargo clippy --workspace --all-targets --all-features --locked -- -D warnings
cargo test --workspace --release --all-features --locked -- --test-threads=1
cargo test -p desktop-app --all-features --example input_probe --release --locked
```

The full test command requires a working Vulkan, Metal or D3D12 backend.
Unavailable graphics is an error, not a skipped success. No arguments launches
the native utility. Native packages rename `desktop-app` to `InsectRealism`.

## Everyday use

Open Settings from the menu/tray, calibrate each untrusted display with a ruler
or an 85.60 mm card-width reference, then enable the Realistic preset. Do not
expose payment-card details. Cursor reactions are off in that preset.

Ctrl+Alt+Shift+H is the default emergency hide shortcut (Alt is Option on a Mac).
Hide All is temporary and freezes biology; Pause is persisted; Quit exits.
Settings is a separate interactive window. Failed overlay safety or calibration
keeps the insects hidden. See `docs/INSTALL.md` for installation and removal.

## Research and runtime

Runtime works offline and embeds a validated profile; it neither captures the
screen nor parses papers. `app/assets/creature-profiles/build-input.json` is the
normalized, provenance-preserving build input. The original Mega Pack remains
separate. The selected donor motion model and engineering transfers are explicit
in `docs/MODEL_ASSUMPTIONS.md`; only ants currently pass the shipping gate.

```sh
cd app
cargo run --release --locked -p profile-compiler -- compile --input assets/creature-profiles/build-input.json --output target/rebuilt.bin --report target/rebuilt.json
cargo run --release --locked -p profile-compiler -- verify --bundle target/rebuilt.bin
```

Profile `.2` uses portable software math and is checked byte-for-byte on each
native build. See `docs/BIOLOGY_PROFILE_FORMAT.md` for the versioned change.
Set `MEGA_PACK_ROOT` for Rust original-pack tests and `INSECT_MEGA_PACK` for the
Python secondary-creature evidence tests. Without the original pack, the latter
explicitly skip; tests of the normalized snapshot remain available.

## Verification and packages

From this project root, using a new empty output directory:

```sh
python scripts/verify_release.py --output verification/new-attempt --gpu --benchmark
python scripts/make_checkpoint.py /path/outside/project/source-checkpoint.zip
```

Native macOS: `bash app/packaging/macos/build-dmg.sh`.
Native Windows: `pwsh -File app/packaging/windows/build-msi.ps1`.
The repository-level `insect-packages-m5r2.yml` workflow runs the native build and
package checks. The repository-level `insect-release.yml` handles version tags and manual runs,
with draft-only release creation; its project-local copy remains a reusable template.

See `docs/TESTING.md`, `docs/PERFORMANCE.md`, `docs/RELEASE.md`, and
`docs/ACCEPTANCE_MATRIX.md` for measurement commands and remaining release gates.
