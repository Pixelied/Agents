# Pearl Catcher 2.1.0 Solver Reliability Design

## Goal
Make Pearl Catcher choose an intentional catch near a user-selected target distance, never accept a plan that collides earlier than its intended intercept, model vanilla launch-time player motion at arbitrary finite XYZ speeds, and make wind timing an actual executable timing choice rather than a solver-only assumption.

## Solver behavior
- Every candidate is a real pearl movement-segment entry into the wind-charge AABB using the 26.1.2 vanilla collision-margin age.
- A candidate is invalid if the same pearl/wind trajectory has any AABB-entry collision on an earlier pearl segment. Early collision checking begins at pearl segment 1 and uses `max(0, pearlTick - windLeadTicks)` completed wind ticks.
- `targetCatchDistance` is a preferred forward range along the target ray, not a maximum. Ranking minimizes absolute range error after exact/no-early validity. Crosshair error and robustness remain important tie-breakers.
- A separate internal search cap prevents unbounded work; user-facing target distance is clamped to a practical range.
- Player motion inheritance uses exactly the vanilla `Projectile#shootFromRotation` source: `source.getKnownMovement()`, with Y suppressed only when `source.onGround()` is true.
- Momentum regression tests cover large positive/negative X/Y/Z velocities, combined diagonal motion, ascent, and high-speed falling. The solver may report no plan when the requested ray/distance is physically unreachable; it must never fabricate a collision.

## Wind timing
- `AUTO` uses the source-backed vanilla back-to-back timing model (lead 1): pearl collision step runs before the wind charge movement step in the same entity tick.
- Manual lead 1/2/3 remains available only as a debug-model override for sweeps/diagnostics.
- Client execution stays back-to-back for normal Auto/Lead 1; it does not invent delayed wind spawns whose origin/momentum would diverge from the solved launch state.

## Settings
Normal settings expose:
- Enabled
- Rotation mode
- Restore slot
- Target catch distance
- Max prediction horizon
- Crosshair radius
- Wind timing: Auto / Lead 1 / Lead 2 / Lead 3
- Reset to defaults

Debug settings remain available. `Prediction ticks` is renamed `Max prediction horizon`; it is a search horizon, not a quality level. Default target distance is 12 blocks and default horizon is 40 ticks.

## Debug sweep
- Sweep waits for a usable pearl before launching the next pitch rather than recording avoidable `PEARL_USE_FAILED` gaps caused by cooldown/use state.
- Exports include target catch distance, timing mode, chosen lead, target-distance error, planned catch tick/range, and observed first clip tick/point.

## Performance
- Preserve the 2.0.3 segment-to-ray pruning.
- Add early-collision rejection before expensive robustness scoring.
- Auto timing may run up to three lead solves, so candidate work must be pruned by target-distance geometry and benchmarked after warmup.

## Compatibility
- Minecraft 26.1.2, Fabric Loader >=0.19.3, Fabric API >=0.153.0+26.1.2, Java 25.
- Client-side only; vanilla movement/use packets; no custom server payloads or fake velocity/entity mutation.
