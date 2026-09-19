# Continuation status

## Exact source and native CI

- Branch: `feat/insect-continuation-03-q7b4`
- Application/package revision: `f3415f05d3bffcfb74be696938f59b112431f8c8`
- Source tree: `7894261d7924b8258dcc7ba7dfacac072c280e5f`
- Final native packaging run: GitHub Actions `35419485077`
- macOS 14 arm64 job: passed all requested automated gates and native packaging
- Windows Server 2022 x64 job: passed all requested automated gates and native packaging
- Runtime profile: `2026.09.17-donor-transfer.2`
- Runtime profile SHA-256: `ce558669b3b17d5e85282c1bc94ea8c0e34b6d86311ded3a2acc2d31552c502b`

The jobs verified formatting, strict Clippy, packaging/tooling tests, input-probe
unit tests, release tests, byte-exact profile recompilation, and native package
construction without source mutation. These are automated build/package results,
not interactive desktop acceptance.

## Native package hashes

| Platform | Artifact | SHA-256 |
|---|---|---|
| macOS arm64 | `InsectRealism-0.1.0-aarch64-apple-darwin.app.zip` | `6e6992ed3d9a345fe9ae6e2b4843e8e7d9e52deb21fbc6bb359cb674d42ed76e` |
| macOS arm64 | `InsectRealism-0.1.0-aarch64-apple-darwin.dmg` | `5b2e8a0144ebab0e3e2f470909d8fbae5e5f417a80514ffe06d698e82444f6c2` |
| Windows x64 | `InsectRealism-0.1.0-x64-portable.zip` | `a5b8b8f3f93ff8b9fc5abbdb257799109c11ee0c242248a2e33a94e3a0f1bf74` |
| Windows x64 | `InsectRealism-0.1.0-x64.msi` | `feabd39df94745c225449c75305996175d489ca9a74f4417fb5414c345de6f80` |

The Windows executable's normal and delay-load import tables contain no external
Visual C++ runtime DLL. The macOS package is a development package; native
codesign verification passed in CI, but Developer-ID signing/notarization was not
performed. Neither platform package is represented as interactively accepted.

## Fresh local verification

The final-code Linux/software-Vulkan suite recorded:

- 152 Rust tests passed; 0 failed; 0 ignored.
- 94 Python/tooling tests passed; 0 skipped.
- Formatting and strict Clippy passed.
- Byte-exact profile recompilation passed.
- Renderer and settings visual validation passed on Mesa llvmpipe Vulkan.
- Source fingerprint remained unchanged during verification.

The portable offscreen benchmark audits recorded:

| Scenario | Minimum live population | p99 completed-frame time | 16.67 ms p99 gate |
|---|---:|---:|---|
| realistic | 49 | 2.810990 ms | pass |
| heavy | 494 | 8.392829 ms | pass |
| stress1000 | 1186 | 15.306883 ms | pass |
| extreme | 3594 | 60.371411 ms | not a 60-FPS acceptance workload |

`stress1000` had zero warmed simulation allocations and no dropped ticks. These
figures are offscreen software-Vulkan evidence, not native compositor/FPS claims.

## Endurance evidence

The final-code offscreen lifecycle run completed 600.006058677 real seconds and
passed its independent audit. It exercised 46 GPU recreations, 93 topology
changes, 372 config roundtrips, 47 panic hides, and zero hidden draws/state
changes. Serialized-frame p99 was 7.862720 ms. Linux file descriptors remained
four. RSS increased from 116,666,368 to 179,290,112 bytes.

This is a ten-minute bounded run. It does **not** satisfy the required two-hour
wall-clock endurance gate and does not establish a memory plateau or prove the
absence of leaks.

## Gates still open

The remaining work requires real target-machine interaction rather than another
source-only build:

- Verify click/double-click/drag/scroll/mouse/keyboard passthrough with overlays visible.
- Verify panic hide while another application owns focus and after overlay recreation.
- Verify no unwanted activation/task-switcher behavior and normal tray/menu-bar behavior.
- Exercise mixed-DPI/rotation/hot-plug and sleep/wake on physical displays.
- Calibrate and inspect 2/3/4 mm rendering at 1:1 physical size.
- Install/uninstall the actual MSI and DMG/app and verify startup cleanup.
- Measure representative native hardware performance with 1,000+ live creatures.
- Complete the required two-hour wall-clock endurance run and review resource trends.
- For public macOS distribution, use the intended Developer-ID/notarization process.

Until those gates are recorded, these artifacts remain development candidates,
not fully release-qualified builds.
