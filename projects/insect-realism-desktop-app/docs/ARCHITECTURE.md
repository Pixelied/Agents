# Architecture

## Dependency boundaries

`creature-profile` validates versioned evidence and runtime profiles.
`display-model` owns millimetres, calibrated pixels and physical adjacency.
`settings` owns presets, migration and atomic per-user configuration.
`simulation` uses those domain types but never imports rendering or native OS
code. Its fixed biological clock feeds compact visual state.
`rendering` consumes that visual state and `platform-api` raw surface contracts.
`platform-api` defines fail-closed overlay state and utility events without
owning graphics. Native adapters own OS windows, menu/tray and notifications.
`desktop-app` is the composition root. `profile-compiler` is build-time tooling.

## Simulation and graphics

The simulation uses preallocated state, a spatial index and coarse trail fields.
Physical positions and speeds are millimetres and millimetres/second; logical
UI scaling is not an insect-size control. Render interpolation is separate from
the fixed biological clock. Stable individual traits and measured trajectory
samples drive locomotion; donor-species transfers and engineering assumptions
remain explicit in profile provenance.

GPU-instanced procedural ant geometry uses three body masses, six articulated
legs and paired antennae. The instance builder performs per-display physical
conversion and LOD selection. Normal rendering is not one draw per creature.

## Safety and recovery

Native windows must prove transparent/non-activating/click-through state before
visibility. Native owners outlive their GPU surfaces. Panic hiding freezes
biology and removes submissions; persistent Pause remains a separate setting.
Compatibility policy uses process/window metadata, never pixel understanding.

Device initialization does not replenish the retry budget. The budget is
replenished only by 120 successful presentations spread across at least five
seconds, or an explicit user retry. This is an engineering recovery policy,
not a biological measurement. Repeated successful initialization followed by
first-frame failure therefore cannot retry forever. Settings-only painting
does not count as evidence that an overlay has recovered.

## Diagnostics

Benchmark instrumentation is a developer command, not normal runtime work.
The executable's allocator forwards all calls to System, and enables per-thread
counting only around benchmark simulation advance. Tests verify the counter
can detect real allocations. GPU benchmarks wait for completion rather than
mistaking cheap queue submission for a completed frame.
