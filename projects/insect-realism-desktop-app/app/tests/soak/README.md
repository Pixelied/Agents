# Reliability soak

Run from `app/` on a supported graphics environment:

```sh
cargo run --locked --release -p desktop-app --features allocation-metrics -- \
  --soak-seconds 7200 --soak-cycle-frames 300 --json target/soak-two-hours.json
```

The duration is **elapsed wall time**, stored independently from simulated time. The command runs the real controller, biology and offscreen renderer, alternates heavy/extreme populations, hides/shows, writes/reloads configuration, rebuilds synthetic topology and destroys/recreates GPU devices. It records hidden-draw/frozen-state violations, operation counts, current resource registries, OS resources where supported, memory and timing summaries. Reports are written atomically so an interrupted run leaves explicit partial evidence.

`complete: false` is an interrupted/in-progress report, not a two-hour pass. A final `complete: true` still does not qualify native window handles, real hot-plug, OS input, driver VRAM usage or infinite-duration reliability. Linux process metrics are RSS and file descriptor counts; unavailable native metrics are `null`, not zero. GPU counts are wgpu registry inventory, not physical VRAM bytes.

Record the exact executable hash and source generation. A long-running copied binary is not silently relabelled as a later source revision. Record concurrent compilation/other workloads; they can affect performance but do not justify inventing a native frame rate. Review memory over time rather than subtracting only the first/last sample. Stable observed resources support a bounded-run statement, never a proof that no possible leak exists.

For native release qualification, also exercise actual window recreation, multi-monitor hot-plug, DPI changes, sleep/wake and input safety under load. No synthetic topology event proves physical OS behavior.
