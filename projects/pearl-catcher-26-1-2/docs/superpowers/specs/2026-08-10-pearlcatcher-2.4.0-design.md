# Pearl Catcher 2.4.0 Collision-Clearance Design

## Goal

Make the single `GeneralCatchSolver` favor catches that remain valid under real vanilla projectile spread instead of nominal face/edge/corner grazes, while preserving target-distance behavior and allowing overlapping manual catches.

## Architecture

`GeneralCatchSolver` remains the only solver. Every candidate already has an exact first pearl-segment -> wind-charge AABB collision. 2.4.0 adds an analytic geometric clearance metric: the maximum uniform inward shrink of the effective wind-charge AABB that the planned pearl segment still intersects. This is a guaranteed L-infinity relative-translation tolerance for that nominal segment and is independent of finite random sampling.

Candidate ranking becomes clearance-aware before sampled spread reliability. Distance remains a strong objective, but a nominally exact target with near-zero clearance must lose to a nearby-distance candidate with materially safer interior penetration. `sampledRobustHitFraction` remains diagnostic/sanity information, not the sole definition of robustness.

## Collision clearance

For a segment `P(t) = A + t(B-A)` and AABB, define six affine face-clearance functions (`x-minX`, `maxX-x`, etc.). The interior clearance at `t` is the minimum of those six values. The segment clearance is the maximum interior clearance over `t in [0,1]`. Because the minimum of affine functions is concave and piecewise linear, the exact maximum occurs at an endpoint or an intersection of two face-clearance functions. Evaluate those finite candidates exactly; no spatial sampling is needed.

A grazing face/edge/corner collision therefore has clearance near 0. A deep center crossing approaches the AABB half-extent. The metric is recorded in `Plan` and debug exports.

## Solver objective

1. Reject candidates whose intended collision is not the first exact vanilla AABB entry.
2. Prefer materially larger geometric collision clearance.
3. Within similarly safe candidates, minimize target-distance error.
4. Minimize crosshair error.
5. Use wind delay/catch time/rotation only as light tie-breakers.
6. Run deterministic vanilla-spread samples on finalists as a sanity/diagnostic signal and a secondary penalty, not as an authority that can override near-zero geometric clearance.

The solver must continue to use one code path for unknown pearl launch and real observed pearl launch.

## Controls

- `G`: start a normal catch using the current camera target. Starting another G catch must not be blocked merely because older pearl/wind pairs still exist.
- `H`: start a normal single-solver catch with target pitch `-90 degrees` and the player's current yaw. This is the vertical auto-catch action, not a debug action.
- `B`: toggle the automatic debug pitch sweep formerly bound to H.

## Overlapping catches

Runtime state changes from one `activeShot`/one `pendingCatch` to collections. Each launched attempt owns its own pearl/wind entity ids, plan, trace, and pending replan state. Entity-load matching must use each attempt's pre-launch entity-id snapshot and choose the newest compatible pending attempt. Finishing one attempt must not clear or block unrelated attempts.

Older wind charges are real collision hazards for newer pearls. The solver/runtime should not pretend ownership exists server-side. 2.4.0 records all active wind ids and, when predicting a new catch, rejects a plan if the nominal new pearl path intersects the observed current AABB/straight-line continuation of an already-active wind charge before the intended catch. This is a safety constraint, not a second planner.

## Debugging

Add to plan/export:
- `collisionClearance`
- player `getKnownMovement`/solver inherited motion (existing player movement remains visible)
- current active-attempt count

Keep client interpolation overlaps labeled as hints only.

## Tests

Core regressions must prove:
- exact AABB segment clearance is zero for a corner graze and large for a center crossing;
- a safer near-target candidate beats a fragile exact-target candidate;
- 12-block distance behavior remains materially accurate;
- straight-up/down and extreme inherited-motion cases still solve;
- known-pearl replanning preserves clearance-aware ranking;
- performance remains interactive at the default 12-block target.

Runtime architecture tests must prove:
- G/H/B key mappings are correct;
- no "current shot already tracked" gate remains for manual G/H;
- active/pending state is collection-based rather than singular;
- legacy planner classes remain absent.
