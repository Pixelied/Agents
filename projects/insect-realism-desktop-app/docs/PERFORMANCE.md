# Performance and endurance

## Required target

The design requires at least 1,000 live creatures at 60 FPS on representative
modern hardware, with interactions, trails and normal rendering enabled.
Software Vulkan timings are a developer baseline, not native compositor or
hardware certification. Do not reuse a prior profile's measurements as fresh
results for profile `.2`.

The four implemented scenarios are `realistic`, `heavy`, `stress1000`, and
`extreme`. Stress targets 1,200 so normal exits need not drop the measured live
population below 1,000. Extreme permits normal entry/exit and does not promise
5,000 live ants on every frame. Its FPS is not the stress acceptance threshold.

## Measure, then independently audit

From `app/`, with a working graphics backend and no competing heavy workloads:

```sh
cargo run --release --locked -p desktop-app --features allocation-metrics -- --benchmark-scenario stress1000 --benchmark-frames 600 --benchmark-warmup 60 --json ../verification/stress1000.json
cargo bench --locked -p simulation --bench spatial
cargo bench --locked -p desktop-app --bench simulation
cargo bench --locked -p desktop-app --bench stress
```

Use `scripts/verify_release.py --gpu --benchmark` to collect all four scenarios
and invoke the independent audits. The verifier recomputes p95/p99 from samples,
checks frame identities, population, allocation counts, draw counts and LOD totals.
The required-stress gate rejects dropped ticks, fewer than 1,000 live ants,
nonzero warmed simulation allocations, or completed-frame p99 above 16.67 ms.
Missing GPU timestamps or native overlay overhead remain absent, not zero.

Biology runs at 30 Hz while the benchmark supplies deterministic 60-Hz input.
Filter samples with positive ticks when analyzing biological update cost; the
zero-cost non-tick frames must not masquerade as cheaper actual ticks.
Allocation measurement covers the instrumented simulation thread only.

## Endurance

```sh
cd app
cargo run --release --locked -p desktop-app --features allocation-metrics -- --soak-seconds 7200 --soak-cycle-frames 300 --json ../verification/soak-two-hours.json
```

This uses elapsed wall time, real offscreen graphics, resource recreation,
config roundtrips, synthetic topology changes, preset churn, and hide/show.
The independent audit cannot substitute simulated time for two actual hours.
A shorter run remains a shorter run. Current evidence is recorded in
`CONTINUATION_STATUS.md`; no absent prior-session soak report is assumed to pass.

Actual native input delivery, window handles, mixed-DPI hot-plug, physical scale,
and a representative-hardware native soak require the target systems. Stable
resources in one bounded run do not prove all possible leaks are absent.

## Recorded continuation measurements

Source code: `7074a927bfb6cfb19d27688157e0599d0baa1f74`, profile `.2`.
Linux x86_64; Mesa 25.2.8 llvmpipe / LLVM 20.1.2 software Vulkan; 1920x1080,
110-PPI numerical fixture, seed 169287718; 60 warmup plus 600 measured frames.
Each report passed its independent sample audit. Times are milliseconds of
completed offscreen work; they exclude native compositor/presentation overhead.

| Scenario | Live population | Median ms | p95 ms | p99 ms | Worst ms |
|---|---:|---:|---:|---:|---:|
| realistic | 49-50 | 1.908702 | 2.299680 | 2.810990 | 3.228118 |
| heavy | 494-500 | 4.626897 | 5.740608 | 8.392829 | 11.031270 |
| stress1000 | 1186-1200 | 9.758653 | 12.693730 | 15.306883 | 17.754505 |
| extreme | 3594-4921 | 32.057414 | 45.661763 | 60.371411 | 68.685030 |

The stress run had no dropped ticks and zero measured steady-state simulation
allocations. Its worst observed frame exceeded 16.67 ms even though p99 passed.
This is not a promise that every frame, backend or native machine reaches 60 FPS.
The extreme scenario is a stability workload, not a 60-FPS qualification.

A separate lifecycle smoke completed 60.003783149 real seconds: 12 GPU device
recreations, 24 synthetic topology changes, 97 config roundtrips and zero hidden
submissions or hidden-state changes. File-descriptor count remained four. RSS
rose from 116,408,320 to 164,032,512 bytes.

The final-code continuation then completed a fresh 600.006058677-second offscreen
lifecycle soak on source `f3415f05d3bffcfb74be696938f59b112431f8c8`: 46 GPU
recreations, 93 topology changes, 372 config roundtrips, 47 panic hides, zero
hidden draws/state changes, and a serialized-frame p99 of 7.862720 ms. Linux file
descriptors remained four. RSS increased from 116,666,368 to 179,290,112 bytes,
so neither a memory plateau nor leak-free operation is claimed. The required
two-hour qualification remains open; simulated time is never substituted for
elapsed wall time.
