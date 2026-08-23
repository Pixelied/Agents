# Pearl Catcher 2.5.0 Execution Hardening Design

**Status:** Approved design, pending written-spec review

**Product:** Pixelied Studio Pearl Catcher

**Target:** Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.153.0+26.1.2, Java 25

## Goal

Make Pearl Catcher behave consistently across stationary movement, ordinary movement, elytra motion, offhand layouts, overlapping attempts, and both fast and human-looking execution without reintroducing multiple planning systems.

The physics architecture remains exactly one `GeneralCatchSolver`. Execution policy, inventory/hand selection, and rotation presentation are separate concerns around that solver.

## Non-goals

- No Elytra-specific planner.
- No offhand-specific planner.
- No Fast planner vs Legit planner.
- No predictive/reactive/best-effort planner stack.
- No arbitrary fixed human-delay presets.
- No claim of 100% physical success when vanilla randomness, cooldowns, packet latency, server TPS, or impossible geometry make a catch unsolvable.

## Core Architecture

The system has three responsibilities:

1. **`GeneralCatchSolver`** — solves projectile physics only. It receives the best current world/player/projectile state and returns a physically valid pearl/wind plan with timing, rotations, collision clearance, and target/crosshair error.
2. **Execution controller** — turns a solver request into actual Minecraft actions while preserving timing. It owns hand resolution, slot preparation, vanilla-use confirmation, and attempt lifecycle.
3. **State estimator** — supplies server-relevant player movement and actual projectile observations. It includes the 2.4.2 packet-space movement estimator and real pearl launch reconstruction.

Execution mode must never change the physics rules. Fast and Legit modes use the same solver and the same notion of pearl age, wind delay, collision authority, and safety.

## User-facing Settings

### Item Switching

`Fast`

- Selects the required hotbar slot through the fastest vanilla client state path already used by Pearl Catcher.
- May directly invoke vanilla item-use with an explicit `InteractionHand`.
- Must still respect vanilla cooldown/use results.
- Must not fabricate projectile-spawn success.

`Legit`

- Uses the player's configured hotbar key mappings for slot changes.
- Waits for Minecraft to actually report the requested selected slot before continuing.
- Uses the player's configured Use key path for the final right-click action.
- Does not manually send an item-use packet as a shortcut.
- Adds no arbitrary fixed delay; progress is driven by observed game state.

### Rotation

`Silent`

- Uses the solver's yaw/pitch without visibly moving the camera.
- Must preserve the 2.4.2 rule that there is no standalone pre-use rotation movement packet capable of wiping server-known movement.
- The vanilla use-item packet carries the solved use rotation.

`Visible`

- Actually changes the player's camera to the solved yaw/pitch before the use action.
- In Legit switching mode this is the fully visible/vanilla-looking combination.
- The execution controller must verify the camera has reached the required state before committing a timing-critical use if implementation uses gradual rotation; if rotation is instantaneous, the action may proceed immediately once the client state reflects it.

`Current`

- Never changes yaw/pitch.
- The solver is constrained to the current camera direction.
- If no safe solution exists under that direction, wait/re-solve rather than silently rotating.

Item Switching and Rotation are independent settings. All six combinations are supported:

- Fast + Silent
- Fast + Visible
- Fast + Current
- Legit + Silent
- Legit + Visible
- Legit + Current

## Hand and Inventory Resolution

Each required projectile use resolves its hand at execution time, not only at attempt creation.

Priority:

1. Required item already in offhand.
2. Required item already selected in main hand.
3. Required item in another hotbar slot.
4. Otherwise the attempt cannot perform that action.

The executor must support at least:

- pearl offhand + wind hotbar
- wind offhand + pearl hotbar
- both in hotbar
- either required item already selected

### Offhand in Fast mode

Fast mode may call vanilla item use with an explicit `MAIN_HAND` or `OFF_HAND`, so offhand does not require a fake slot delay.

### Offhand in Legit mode

Vanilla right-click evaluates hands according to vanilla interaction order. Legit mode must never guess through that ordering and accidentally consume the wrong main-hand action.

To keep this reliable and small rather than simulating every possible item/block/entity interaction:

1. If normal vanilla Use is unambiguous for the required offhand projectile, trigger the configured Use key directly.
2. Otherwise use Minecraft's configured **Swap Item With Offhand** key to move the required projectile into the selected main hand.
3. Confirm that the hand swap actually happened.
4. Trigger the configured Use key.
5. Confirm the expected projectile/use result.
6. Restore the prior hand arrangement with the configured swap-hands key when restoration is enabled and no newer active attempt owns that hand state.

The executor must not emulate arbitrary right-click interaction precedence or maintain a catalogue of "safe" main-hand items. If the required hand arrangement cannot be confirmed in time, wait/re-solve or abort cleanly.

## Attempt State Machine

Keep the executor small. A catch attempt follows observable milestones, not planner variants.

`PREPARE_PEARL -> PEARL_USE_REQUESTED -> PEARL_OBSERVED -> PREPARE_WIND -> WIND_USE_REQUESTED -> WIND_OBSERVED_OR_RESOLVED -> COMPLETE`

An attempt may also enter `WAITING_FOR_SAFE_SOLUTION`, `ABORTED`, or `EXPIRED`.

### Timing authority

Timing is measured from confirmed/observed game events.

- Slot-switch request time is not pearl age.
- Key-press request time is not pearl age.
- A planned use that vanilla rejects is not pearl age.
- Once the real pearl exists, its entity tick/observed launch state is authoritative.
- Wind timing is scheduled against actual pearl progress, not against how long Legit preparation took.

If Legit preparation cannot be completed before the currently planned wind-use moment, do not fire late. Re-run the same solver for the next viable intercept.

## Continuous Re-solving Rules

The same `GeneralCatchSolver` may be rerun whenever relevant state materially changes:

- actual pearl becomes available
- packet-space player movement estimate changes materially
- player position changes materially
- item/hand availability changes
- selected slot changes manually or through Legit execution
- an old live wind charge creates a possible earlier collision
- current camera changes in Current mode
- elapsed pearl age invalidates the old timing window

The controller never executes an old plan solely because it was once valid.

## Movement and Elytra Requirements

Preserve the 2.4.2 movement fix:

- solver pre-throw movement is based on packet-space player position displacement, not local `LocalPlayer.getKnownMovement()`.
- silent use must not emit a standalone pre-use movement/rotation packet that resets server-known movement.
- once a pearl is observed, reconstruct the inherited movement estimate from its actual launch vector and the commanded vanilla throw component.
- actual projectile-derived state wins over speculative pre-throw state when the two disagree beyond vanilla spread tolerance.

This path must remain generic. Elytra, sprinting, falling, knockback, and ordinary motion all use the same state-estimation mechanism.

## Collision Robustness

Preserve the 2.4.0+ collision-clearance logic.

A plan must be physically valid under the exact pearl-segment -> wind-charge AABB collision authority. Candidate ranking must not sacrifice most geometric clearance merely to reduce target-distance error by a tiny amount.

Spread sampling remains a secondary diagnostic/sanity signal, not the sole definition of robustness.

## Overlapping Attempts

G/H may start a new attempt while older projectile pairs are still alive, subject to vanilla use/cooldown constraints.

Each attempt owns its pearl/wind entity IDs once observed. New projectiles are paired using spawn timing/entity identity, not a single global active-shot variable.

Before committing a new solution, the solver/controller must account for older live wind charges that could become the new pearl's first collision. An old charge may only be treated as the intended target if the current plan explicitly accepts that collision.

Inventory restoration must not let one attempt undo the slot preparation of another attempt. Restoration happens only when the executor can prove no newer active attempt currently owns the selected-slot state.

## Manual User Intervention

The mod must tolerate the player changing state during an attempt.

Examples:

- user manually changes hotbar slot
- user moves an item
- user opens a GUI/chat
- user changes camera in Current mode
- player starts/stops elytra
- player receives knockback

The response is re-evaluation, not blind continuation.

Legit mode must not synthesize hotbar/use input while a screen state would prevent ordinary gameplay key handling. It waits until normal gameplay input is valid again or the attempt expires.

## Failure and Wait Behavior

The mod should prefer not throwing over knowingly throwing an invalid/stale shot.

Wait/re-solve when:

- no sufficiently safe physical solution exists now
- required Legit slot selection is not confirmed
- the required item is on cooldown
- the item moved/disappeared
- offhand use would be stolen by main hand and no safe slot is available
- current player/projectile state invalidated the old plan
- preparation would make the planned wind timing late

Abort/expire when:

- player dies/respawns
- world/dimension changes
- target projectile disappears without a catch outcome
- attempt exceeds its lifecycle horizon
- required inventory state cannot be recovered

## Keybind Behavior

- `G`: normal automatic catch using configured target distance and current Item Switching/Rotation settings.
- `H`: vertical automatic catch, targeting as close to pitch `-90` as safely/physically possible through the same `GeneralCatchSolver`.
- `B`: debug sweep.

No key creates a separate planner.

## Debugging Additions

Debug output should retain existing fields and add/confirm enough information to distinguish solver failure from executor/input failure:

- item switching mode
- rotation mode
- requested hand/item for each use
- actual resolved hand
- requested target hotbar slot
- tick slot switch requested
- tick selected slot confirmed
- tick Use key queued (Legit)
- vanilla use result / whether a projectile subsequently appeared
- pearl observed entity ID/tick
- wind observed entity ID/tick
- planned wind-use pearl age
- actual wind-use pearl age
- preparation lateness if any
- packet-space movement estimate
- local movement for comparison only
- projectile-inferred inherited movement
- movement-estimate error
- collision clearance
- old-wind first-collision conflict result
- active attempt count

Debugging must not change execution timing except for unavoidable logging overhead; no debug-only planner path is allowed.

## Scenario Test Matrix

The implementation is not complete until tests/smokes cover the following categories.

### Movement

- stationary
- walking
- sprinting
- jumping
- falling
- elytra level flight
- elytra diving
- elytra climbing
- rapidly changing elytra velocity
- knockback/external velocity change

### Aim

- pitch `-90`
- pitch `+90`
- ordinary upward/downward pitches
- level aim
- Current rotation with no valid solution

### Distance

At minimum regression targets:

- 12
- 16
- 18
- 20
- 24
- longer-distance cases within physical/horizon limits

### Inventory/hand

- both items in hotbar
- pearl in offhand
- wind in offhand
- required item already selected
- main-hand item that would steal offhand use
- no safe main-hand slot for offhand use
- item moved after attempt begins
- item removed after attempt begins

### Input configuration

- default hotbar keys
- remapped hotbar keys
- default Use key
- remapped Use key
- GUI/chat open

### Execution combinations

All six Item Switching/Rotation combinations.

### Runtime/timing

- cooldown active
- delayed projectile observation
- manually changed slot during attempt
- repeated G presses
- overlapping attempts
- old wind near new pearl
- H vertical catch while moving
- world change
- death/respawn

## Success Criteria

A release is acceptable when:

1. One `GeneralCatchSolver` remains the only catch planner in production source.
2. Fast and Legit execution use identical solver/timing semantics.
3. Offhand pearl and offhand wind are both supported.
4. Legit mode uses configured vanilla key mappings for hotbar selection and Use behavior, with observed confirmation before timing-critical progression.
5. No pre-use silent rotation packet wipes server-known movement.
6. Elytra tests show the solver's inherited-movement estimate tracks projectile-inferred inherited movement within expected vanilla spread/observation tolerance in representative level, dive, and climb cases.
7. The executor never knowingly fires a stale/late solution when it can wait/re-solve instead.
8. The scenario matrix passes automated tests where deterministic and targeted in-game/debug validation where server behavior cannot be faithfully reproduced offline.
9. Pixelied Studio identity remains clean throughout filenames, package paths, resources, metadata, and docs.
10. Final packaged jar passes class-version, metadata, namespace/stub-leak, Fabric Event ABI, startup-linkage, and single-solver architecture checks.

## Build/Verification Constraint

If the environment still lacks a genuine Java 25 + Loom build path, release notes must continue to state that clearly. Manual/strict-signature packaging is not allowed to weaken ABI verification; every Fabric/Minecraft type-shape assumption touched by this release must be verified against authoritative 26.1.2 source/API definitions before packaging.
