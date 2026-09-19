# Test strategy and acceptance

## Automated verification

```sh
python scripts/verify_release.py --output verification/new-attempt --gpu --benchmark
```

The directory must be new and empty. The driver records exact commands, exit
codes, elapsed durations and logs; compares source fingerprints before/after;
checks compiled profile bytes; and audits benchmark/soak samples separately.
Omitting `--gpu` selects the explicitly reduced core/controller suite.
Native `--package` constructs and inspects the package on that OS.

Set `INSECT_MEGA_PACK` to the original Mega Pack for all Python evidence tests.
Set `MEGA_PACK_ROOT` for original-pack Rust compiler tests. A missing raw pack
does not prevent normalized-input tests but must not become an evidence-audit pass.

The complete Rust command is `cargo test --workspace --release --all-features
--locked -- --test-threads=1` from `app/`. Run the `input_probe` example tests
separately. The portable compiler regressions additionally pin exact acceleration,
turn and full-profile bytes on each host; numerical tolerance cannot bypass them.

## Layers are not interchangeable

1. Source/static/unit/property tests establish checked contracts and regressions.
2. Offscreen graphics tests and recorded workloads establish behavior on that
   exact backend; generated PNGs are actual renderer readbacks.
3. Native package inspection establishes format/resources/checksums, not actual
   click-through, physical calibration, installer removal or runtime stability.

The input probe's synthetic unit tests are not real input acceptance. Use
`app/tests/input-safety/README.md` on each native target and record executable
hash, OS/GPU/driver, monitor modes, focus owner, and observed results.

## Required manual gates

Clicks, double-clicks, dragging, wheel/trackpad, mouse motion and keyboard input
must reach an underlying application while overlays are active. Panic hide must
work on key press while another app is focused. Repeat after overlay recreation.
Check task switching, menu/tray survival, settings separation, real hot-plug,
rotation/mixed DPI, sleep/wake, fullscreen exclusions and failure recovery.

Review 2/3/4 mm fixtures at calibrated 1:1 physical size, normal viewing distance,
bright/dark/high-contrast content, all headings/gait phases and LOD transitions.
Magnified diagnostics alone do not establish physical realism.

Install/uninstall actual packages including startup cleanup, and run the native
heavy-load endurance matrix. Record unsupported protected/fullscreen cases
honestly; bypassing OS protections is not part of testing.

## Evidence

`CONTINUATION_STATUS.md` indexes current runs and explicit remaining gates.
A source ZIP, a policy test, or an older archived log is never silently treated
as a fresh successful native run. An unavailable two-hour result stays unverified.
