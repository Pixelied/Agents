# Pearl Catcher 26.1.2 Hardening Design

## Status

Approved direction from the 2026-08-22 source review. This document freezes the implementation boundaries before code changes.

## Source of truth

- Pearl Catcher source: `Pixelied-Studio-Pearl-Catcher-2.5.1-source.zip` provided in the conversation.
- Minecraft reference: supplied decompiled Minecraft Java 26.1.2 client/game source.
- The supplied 26.1.2 source wins over assumptions from older Minecraft versions.
- Preserve the existing single `GeneralCatchSolver` architecture. Do not reintroduce separate predictive/reactive planner families.

## Goal

Make Pearl Catcher reliably conservative: it should perform a catch only when the solver and runtime authority model have enough evidence that the sequence is safe. When timing, ownership, environment, interaction context, or player state is uncertain, the mod must fail closed rather than guess.

At the same time, reduce execution complexity. Reliability must come from explicit state ownership and vanilla-faithful checks, not from accumulating extra modes, heuristics, retries, or user-facing tuning knobs.

## Non-goals

- No new combat features unrelated to pearl catching.
- No alternate planner architecture.
- No giant fluid simulator.
- No packet-desync tricks that rely on server-invalid state.
- No new advanced settings unless a setting is genuinely necessary for correct operation.
- No broad rewrite of the proven projectile math.
- No asynchronous solver architecture unless profiling proves synchronous pruning cannot meet the client-thread budget safely.

## 1. Hard plan-acceptance policy

### Problem

`GeneralCatchSolver` currently ranks robustness and clearance but does not enforce them. The reviewed build can return plans whose sampled robustness is 0% and still execute them.

### Design

Keep robustness calculation inside the existing solver, but separate **candidate ranking** from **execution acceptance**.

A plan is executable only if all of the following hold:

1. The nominal pearl/wind solution collides in the intended catch window.
2. The plan satisfies the existing target-range/error constraints.
3. Nominal geometric clearance is at least **0.03 blocks**.
4. Sampled robustness is at least a conservative minimum.
5. Runtime uncertainty introduced by network age, entity/world obstruction, unsupported environment, or player movement does not consume the available margin.

Hard floors: **0.03 blocks nominal clearance** and **80% robustness over the existing 64 spread samples**. The robustness threshold matches the earlier design intent that predictive/reactive solutions be at least 80% robust. Both remain internal safety invariants rather than user-facing sliders.

A returned candidate that fails the hard policy is treated as no valid plan. The executor may re-solve when new authoritative state becomes available, but it must never execute a knowingly rejected candidate.

### Regression requirements

- A known zero-percent robustness case must produce no executable plan.
- A plan below 80% robustness must produce no executable plan.
- A plan below 0.03 blocks clearance must produce no executable plan.
- A normal robust case must continue to solve.
- Delay-zero plans are subject to the same hard gate; they do not receive a special exemption.

## 2. Bounded Legit input ownership

### Problem

HOTBAR/SWAP input leases can wait forever for an expected state transition. A single rejected or interrupted action can block all future Legit catches.

### Design

Every synthetic Legit input lease becomes a bounded state machine:

`requested -> awaiting confirmation -> confirmed | expired | cancelled`

Each request records:

- owner attempt ID;
- requested action;
- expected confirmation condition;
- creation tick;
- confirmation deadline.

If the expected state is not observed by the deadline, the lease is cleared and the owning attempt either recomputes from current inventory state or aborts safely. No expired or cancelled lease may block a later attempt.

The deadline must be bounded by the owning attempt's existing preparation lifetime. It is an internal protocol constant/derived deadline covered by deterministic tests and is not exposed as normal configuration.

## 3. Exact projectile ownership

### Problem

New projectile attribution currently relies on entity ID freshness, proximity, and attempt age. Minecraft 26.1.2 transmits projectile owner identity in the spawn path, so those heuristics can incorrectly claim another player's projectile.

### Design

For an entity to become the attempt-owned pearl or wind charge:

- it must be the expected vanilla projectile type;
- its `getOwner()` must resolve to the local player;
- it must satisfy the attempt's temporal/type expectations;
- it must not already belong to another local attempt.

Proximity remains useful only as a secondary sanity check, never as proof of ownership.

Foreign projectiles remain visible to environmental/hazard checks where they can physically interfere.

## 4. Vanilla-faithful path obstruction and unsupported environments

### Problem

Current preflight checks blocks only. Vanilla projectile collision authority also includes world-border/block clipping and hittable entities. Pearl drag also changes in water and other environmental effects are not modeled by the solver.

### Design

Create one focused runtime path-safety component used by the coordinator before execution/re-execution.

It must:

- trace the relevant projectile path through the intended catch window;
- use the 26.1.2 vanilla block/world-border collision semantics confirmed from source;
- account for hittable entities using the same effective collision expansion/margin as vanilla projectile collision;
- ignore only the intended local pearl/wind pair where vanilla self/pair rules require it, not arbitrary nearby entities;
- reject a pearl path if an entity can intercept the pearl before the intended catch;
- reject a wind path if an entity can trigger/terminate it before the intended catch;
- detect entry into water, bubble columns, or another environment whose projectile dynamics are not represented by `VanillaProjectilePhysics`;
- fail closed for those unsupported dynamics instead of simulating them approximately.

Do not build a general-purpose world simulator. Unsupported environmental states are rejection conditions until exact modeling is deliberately added later.

## 5. Network/server-age timing compensation

### Problem

When the client first observes a delayed pearl, the server-side pearl is already older because the spawn packet travelled to the client. A later wind-use packet then travels back to the server. Solving from client age zero therefore underestimates pearl age at wind creation.

### Design

Represent delayed execution timing as a conservative **server-age interval**, not a single client-local age.

Use the local player's current connection latency reported by the 26.1.2 client as the RTT estimate. For RTT `p` milliseconds, derive a conservative tick-lead interval:

- lower lead: `max(0, floor(p / 50) - 1)` ticks;
- upper lead: `ceil(p / 50) + 1` ticks.

The one-tick guard on each side covers tick-phase quantization and ordinary measurement variance without pretending the ping sample is exact. The observed pearl state is advanced across that entire lead interval before accepting a delayed wind plan.

A delayed plan is executable only if the intended catch remains valid throughout the accepted lead interval after the existing robustness/clearance gates are applied. If connection latency is unavailable/invalid, or the interval cannot be proven safe, delayed execution fails closed.

Do not hide uncertainty with a single arbitrary magic delay offset.

## 6. Correct silent rotation lifecycle

### Problem

Minecraft 26.1.2's use-item handling consumes packet yaw/pitch and can update server-known player rotation. Pearl Catch restores the local camera immediately, but the server may retain the solver rotation until another rotation update is sent.

Restoring server rotation too early can also corrupt inherited movement/rotation assumptions for a later projectile in the same sequence.

### Design

Treat silent rotation as attempt-owned server state.

- Local camera restoration may happen immediately as today.
- Do **not** send a server restoration between pearl and a delayed wind if the later action depends on the current server-known movement/rotation ordering.
- After the final projectile action for that attempt has been sent, enqueue one owned server-rotation restoration to the player's actual camera orientation.
- Cancel or supersede the restoration safely if the player manually rotates in a way that makes the queued snapshot stale.
- Disconnect, disable, or attempt cancellation must clear pending ownership state; no orphaned restoration may fire later.

Tests must prove both ordering and cleanup.

## 7. Legit right-click interaction safety

### Problem

Vanilla `startUseItem()` can interact with an entity or block before falling through to item use. A synthetic Legit right-click can therefore open/use something instead of throwing the pearl or wind charge.

### Design

Synthetic **Legit** projectile use is armed only when the current vanilla `hitResult` is `MISS`. Any block hit or entity hit is treated as ambiguous and rejected before the synthetic key press.

This is intentionally conservative. It avoids hand-maintaining an interactable-block/entity catalogue and avoids invoking interaction code speculatively just to discover whether it consumes the click. Future relaxation requires a separately proven side-effect-free vanilla-equivalent check.

Fast mode may continue to use the direct item-use path because it does not claim to reproduce the normal right-click interaction chain.

## 8. Manual player choices beat stale automation

### Problem

Delayed restore actions can write an old selected slot/offhand state after the user has intentionally changed it.

### Design

Automatic restoration is ownership-aware.

For every temporary slot/offhand change:

- record the exact pre-action state;
- record the state the mod applied;
- restore only if the current state still equals the state the mod owns;
- if the user changed it meanwhile, relinquish ownership and do not overwrite the user.

Restoration is per automatic action, not based on a stale snapshot captured at the beginning of a long catch attempt.

## 9. Unsupported riding/vehicle movement

### Problem

The current server-known movement estimator models ordinary `LocalPlayer.sendPosition()` behavior, while vanilla uses different movement packet behavior when the player is a passenger.

### Design

Until passenger semantics are modeled and tested from the supplied 26.1.2 source, attempts while riding are rejected with a clear internal reason. Do not feed knowingly wrong inherited movement into the solver.

## 10. Cancellation, disable, disconnect, and supersession

### Problem

Attempt-owned asynchronous state is spread across multiple collections/state machines. Disable/debug cancellation can remove attempts while leaving leases, bridges, or restores alive.

### Design

Every asynchronous operation carries an owner token tied to a catch attempt or an explicit debug/sweep operation.

A single cancellation path must release all resources owned by that token:

- Legit input lease;
- pending slot/offhand restore;
- pending silent-rotation restore;
- observed projectile association;
- delayed wind action;
- debug/sweep state;
- any pending re-solve/confirmation state.

Use the same cleanup path for normal completion, abort, disable, disconnect/world change, supersession, and debug cancellation. Cleanup must be idempotent.

## 11. Focused executor decomposition

### Problem

`CatchExecutor` is approximately 1,689 lines and currently owns unrelated responsibilities, making state ownership bugs hard to reason about.

### Design

Reduce it into three responsibility domains while preserving behavior and avoiding framework-style abstraction.

### `CatchCoordinator`

Owns:

- attempt lifecycle;
- initial solve and observed-pearl re-solve;
- plan acceptance policy;
- timing uncertainty decisions;
- high-level cancellation/completion.

### `VanillaInputExecutor`

Owns:

- Fast and Legit item-use mechanics;
- bounded input leases;
- temporary slot/offhand ownership;
- local/silent rotation application and final server restoration;
- interaction-context validation.

### `ProjectileTracker`

Owns:

- local-owner-filtered projectile acquisition;
- attempt association;
- observed pearl/wind snapshots;
- foreign projectile/environment interference queries needed by preflight.

`GeneralCatchSolver`, `VanillaProjectilePhysics`, rendering/debug visualization, configuration, and solver data records remain separate and are not rewritten simply to fit the split.

The split is successful only if it reduces shared mutable state and clarifies ownership. Do not create pass-through classes with no independent responsibility.

## 12. Solver performance

### Problem

The reviewed initial solve can be expensive on the client thread; observed measurements included tens of milliseconds with large spikes. The current performance acceptance of roughly 300 ms is too permissive for interactive gameplay.

### Design

Profile before optimizing. Preserve mathematical results while reducing avoidable work in the initial search.

Preferred optimization order:

1. remove duplicate candidate evaluation;
2. prune candidates using cheap necessary bounds before expensive robustness sampling;
3. cache pure repeated calculations within one solve;
4. short-circuit robustness evaluation when a candidate can no longer meet the hard 80% acceptance floor;
5. reduce allocations in hot loops where measurement shows meaningful cost.

Do not lower the 64-sample robustness evaluation merely to make a benchmark green unless equivalence is demonstrated.

Performance verification records both wall-clock timing and deterministic work counters such as candidate evaluations/robustness samples. CI uses the deterministic counters as the strict regression gate and reports timing as evidence, avoiding a flaky hardware-dependent millisecond promise.

## 13. Behavioral test strategy

Tests are written red-first for every defect before the corresponding implementation change.

Required regression groups:

1. **Plan acceptance**
   - 0% robustness rejected;
   - below-80% robustness rejected;
   - below-0.03 clearance rejected;
   - robust nominal plan accepted.

2. **Input lease lifecycle**
   - HOTBAR confirmation success;
   - SWAP confirmation success;
   - timeout clears lease;
   - cancelled owner clears lease;
   - later attempt proceeds after timeout.

3. **Projectile attribution**
   - local projectile accepted;
   - foreign nearby projectile rejected;
   - foreign projectile can still be considered as interference.

4. **Path safety**
   - block obstruction rejected;
   - world-border obstruction rejected where applicable;
   - entity interception rejected;
   - water/bubble/unsupported environment rejected.

5. **Latency/timing**
   - zero/low-latency timing remains valid;
   - delayed observed pearl is checked over the conservative RTT-derived lead interval;
   - excessive timing uncertainty rejects execution.

6. **Rotation lifecycle**
   - silent use packet carries solver rotation;
   - no premature restore before delayed wind;
   - final restore happens after final action;
   - stale/manual rotation prevents an obsolete restore;
   - cancellation/disconnect clears it.

7. **Legit interaction safety**
   - `MISS` hit result may arm Legit item use;
   - block hit rejects synthetic Legit use;
   - entity hit rejects synthetic Legit use.

8. **Manual ownership**
   - untouched mod-owned slot restores;
   - manual user slot change is preserved;
   - stale offhand restoration is not applied.

9. **Lifecycle**
   - disable cleans every owner resource;
   - debug/sweep cancellation cleans every owner resource;
   - world/disconnect cleanup is idempotent;
   - superseded attempt cannot fire a later action.

10. **Unsupported movement**
    - passenger/riding state rejects the attempt.

11. **Existing invariants**
    - current pure solver self-test remains green;
    - existing regression scripts remain green or are replaced by stronger behavioral tests without losing coverage;
    - build/ABI checks run against the actual built artifact rather than being invoked without required arguments.

## 14. CI and verification

The project must have a dedicated GitHub Actions workflow under `.github/workflows/pearl-catcher-26-1-2-ci.yml` that executes the project's supported test/build path on the required Java/Minecraft/Fabric toolchain.

Before merge:

- run all deterministic unit/regression tests;
- build the production artifact;
- run ABI/post-build verification scripts against the built output with the required arguments;
- audit that the output contains no accidental source/debug junk;
- run workspace tests and `python agentctl.py validate`;
- inspect the final PR diff for unrelated changes;
- verify every acceptance criterion in `tasks/harden-pearl-catcher-26-1-2/task.json` against evidence.

CI must be green before the implementation PR is merged.

## 15. Implementation order

1. Import the exact reviewed 2.5.1 source into `projects/pearl-catcher-26-1-2` without functional edits.
2. Establish a clean baseline build/test run.
3. Add red behavioral regressions for the P0 plan-acceptance and input-lease bugs.
4. Fix P0 issues and verify green.
5. Add red regressions and fix projectile ownership/path safety.
6. Add red regressions and fix latency/timing and silent-rotation lifecycle.
7. Add red regressions and fix Legit interaction/manual-state ownership.
8. Add red regressions and fix riding plus cancellation/disable/disconnect ownership.
9. Decompose `CatchExecutor` only after behavior is protected, keeping public behavior unchanged.
10. Profile and prune the initial solver hot path without weakening correctness gates.
11. Add/finish dedicated CI and post-build artifact verification.
12. Run the full verification matrix, review the diff, and merge only with fresh green evidence.

## Design invariant

Pearl Catcher should become boringly predictable:

**one solver, vanilla-grounded physics, hard safety gates, explicit ownership, bounded state machines, and no throw when the mod cannot prove the catch is safe enough.**
