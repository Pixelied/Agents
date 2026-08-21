# SpeedBridge Assist: Technique Framework, Breezily Bridging, and Silent Aim Design

**Date:** 2026-08-01  
**Status:** Approved design, awaiting written-spec review  
**Target baseline:** SpeedBridge Assist 1.1.0, Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Loom 1.17.17, Java 25  
**Scope:** Architecture and behavior specification only. No implementation changes are authorized by this document.

## 1. Objective

Extend SpeedBridge Assist with a reusable bridging-technique architecture that supports:

1. A user-selectable **Automatic** technique mode.
2. A user-selectable **Standard** technique mode containing the existing straight, diagonal, upward-staircase, and diagonal-upward behavior.
3. A user-selectable **Breezily** technique mode implementing authentic straight, level, backward, no-sneak Breezily movement.
4. A reusable registration model for future techniques such as Witchly, Moonwalk, Godbridge, or Telly without expanding one monolithic controller.
5. A global **Aim Mode** setting with:
   - **Visible:** automation visibly turns the camera.
   - **Silent Packet:** the server and player simulation use the technique's logical rotation while the first-person camera remains under the user's control.

The design must preserve real block faces, normal reach, exact ray tracing, ordinary block interaction, normal client movement physics, physical-input override, one placement maximum per client tick, and complete synthetic-input cleanup.

## 2. Approved product decisions

These decisions are fixed unless the user explicitly changes them:

- Breezily is a separate selectable technique, not a hidden setting inside Standard.
- Automatic may choose Breezily or another registered technique based on capability, safety, speed preference, and current world conditions.
- Forced Breezily uses only Breezily. It never falls back to Standard, Ninja-style crouching, staircase bridging, Witchly, or another technique.
- When forced Breezily becomes invalid or unsafe, it stops progress, attempts emergency Sneak only when that can still help, releases all synthetic state, enters a latched-aborted state, and requires activation release before restarting.
- Authentic Breezily version one is straight, level, backward, no-sneak bridging. Diagonal Witchly and upward Breezily-like techniques are future independent techniques.
- Silent Packet aim is shared infrastructure available to every technique, including future techniques.
- Silent Packet aim must not fabricate position, velocity, teleport, block-placement, or duplicate interaction packets.
- Automatic and forced-technique selection are independent from aim delivery. Any technique can run with Visible or Silent Packet aim if its capabilities permit it.

## 3. Research conclusions

### 3.1 Authentic Breezily gait

The defining Breezily motion is:

- continuous backward movement;
- alternating left/right strafe input;
- no routine crouching;
- a lateral direction reversal near the middle of each block;
- crossing each block boundary while already returning toward the bridge centerline.

The official Minecraft speed-bridging article explicitly warns that changing strafe direction at a block edge causes falls and illustrates the correct change near the middle of each block. It distinguishes Witchly as a diagonal technique rather than ordinary Breezily.

The desired bridge-local path is therefore a triangular zigzag, not a fixed-tick A/D spam loop and not a literal sine wave:

```text
phase through block:  0.00        0.50        1.00
cross-track target:   center  ->  extreme  -> center
next block:           center  -> opposite extreme -> center
```

The controller must track actual along-track position, cross-track position, horizontal velocity, support overlap, and placement deadlines. Tick count is an observation, not the primary timing source.

### 3.2 Movement-vector interpretation

The technique does not work merely because total player speed is reduced by a fixed formula. Minecraft maps movement input through player yaw, acceleration, friction, and retained velocity. The relevant measured values are:

- the component of actual velocity along the committed bridge axis;
- the component of actual velocity across the bridge axis;
- predicted cross-track position near the next midpoint and block boundary;
- predicted support overlap before placement confirmation.

No fixed `4.317 m/s`, fixed 45-degree decomposition, or universal tick interval will be treated as authoritative control data.

### 3.3 Placement timing

High human CPS creates repeated opportunities to hit a brief valid placement window. Automation does not need to imitate human CPS. It must issue one ordinary Use Item pulse on the earliest safe client tick where all shared placement validation succeeds.

### 3.4 Silent rotation transport

Minecraft's normal movement protocol supports rotation-bearing movement packets. The client also has a normal movement-send path in `LocalPlayer#sendPosition`. The design will preserve that normal packet cadence rather than sending extra rotation packets each tick. This avoids unnecessary packet volume and avoids coupling every technique to packet construction.

## 4. Current 1.1.0 codebase findings

The existing source is usable but too centralized for additional techniques:

- `SpeedBridgeController` currently owns activation, safety, block selection, path planning, level movement, staircase state, rotation, placement, HUD output, and cleanup orchestration.
- `InputController` already reads physical state and merges synthetic state per `KeyMapping`, but higher-level movement code currently treats physical movement as an all-or-nothing override in places.
- `RotationController` directly mutates `LocalPlayer` yaw and pitch, so it can only provide visible rotation.
- `BridgePathPlanner` already produces committed, face-connected straight and alternating-axis diagonal paths.
- `PlacementTargetFinder`, `PlacementValidator`, `PlacementCadence`, `PendingPlacement`, and `BlockSelector` should remain shared infrastructure.
- `StaircaseCycle` is already a distinct pure state machine and should be owned by the extracted Standard technique.
- `CleanupPlan` and stop conditions are useful foundations but must gain technique and silent-rotation ownership.

The feature should therefore extract responsibilities from `SpeedBridgeController` instead of layering Breezily branches into it.

## 5. User-facing configuration

### 5.1 Technique setting

```text
Technique
- Automatic
- Standard
- Breezily
- future registered techniques
```

`TechniqueMode` is persisted by stable identifier, not enum ordinal.

#### Automatic

- Evaluates registered techniques against current context.
- Selects only techniques enabled for Automatic.
- Changes techniques only at declared safe transition points.
- Uses hysteresis and minimum dwell rules to prevent flicker.
- Initially chooses between Standard and Breezily.

#### Standard

- Uses the existing straight, diagonal, upward-staircase, and diagonal-upward behavior.
- Never switches to Breezily.

#### Breezily

- Uses only straight, level Breezily.
- Refuses diagonal, rising, falling, irregular-support, and obstacle conditions.
- Never falls back to another technique.

### 5.2 Aim Mode setting

```text
Aim Mode
- Visible
- Silent Packet
```

#### Visible

The active technique's logical rotation is applied directly to the player and camera using configurable speed and smoothing.

#### Silent Packet

- The first-person camera remains controlled by the user's mouse.
- The player simulation and vanilla movement-send path use the technique's logical yaw and pitch.
- No custom position or placement packets are constructed.
- Physical mouse movement alone does not abort automation.
- Conflicting physical movement, attack, or use input can abort or override according to the shared input policy.

### 5.3 Automatic technique preferences

Every registered technique exposes:

- `Use in Automatic`: on/off.
- `Priority bias`: safer to faster.

Forced technique modes ignore Automatic preferences.

### 5.4 Breezily advanced settings

The initial implementation should expose only bounded expert controls whose effects can be validated:

- Maximum lateral corridor fraction.
- Minimum predicted support-overlap margin.
- Maximum permitted midpoint phase error.
- Maximum permitted boundary cross-track error.
- Emergency Sneak enabled.
- Debug overlay enabled.

There will be no user-facing fixed A/D tick interval. Reversal timing remains position- and velocity-based.

## 6. Architecture

### 6.1 Top-level flow

```text
Activation + physical input + world state
                    |
                    v
            BridgeContextBuilder
                    |
        +-----------+-----------+
        |                       |
        v                       v
 TechniqueSelection       Shared services
 Automatic/forced         block/path/support
        |                 placement/input/aim
        v                       |
 Active BridgeTechnique <-------+
        |
        v
 TechniqueCommand / TechniqueStatus
        |
        v
 InputArbiter + RotationBroker + PlacementPipeline
        |
        v
                  Minecraft
```

`SpeedBridgeController` becomes a thin orchestrator. It owns the current session and shared lifecycle, but no longer contains technique-specific movement or staircase logic.

### 6.2 Core types

#### `TechniqueId`

Stable string-backed identifiers such as `standard` and `breezily`.

#### `TechniqueCapabilities`

Declares:

- level, ascending, and descending support;
- straight and diagonal path support;
- compatible support-shape classes;
- Visible and Silent Packet aim support;
- routine Sneak or Jump requirements;
- safe-entry and safe-exit requirements;
- risk score, expected speed score, and Automatic eligibility.

#### `BridgeContext`

Immutable per-tick observation containing:

- player position, bounding box, yaw, pitch, velocity, grounded state, and movement state;
- physical input snapshot;
- current logical bridge frame and path state;
- support collision snapshot;
- current and proposed placement targets;
- selected block and remaining count;
- current tick and recent timing history;
- server-correction, screen, focus, dimension, and connection state;
- active aim mode and configuration snapshot.

Techniques may not directly poll unrelated Minecraft globals once the context is constructed. This keeps state machines deterministic and testable.

#### `BridgeTechnique`

```java
interface BridgeTechnique {
    TechniqueId id();
    TechniqueCapabilities capabilities();
    TechniqueEvaluation evaluate(BridgeContext context);
    void begin(BridgeContext context);
    TechniqueTick tick(BridgeContext context);
    boolean canTransitionSafely(BridgeContext context);
    void abort(TechniqueAbortReason reason);
    void reset();
}
```

#### `TechniqueEvaluation`

Returns:

- compatible/incompatible;
- hard incompatibility reason;
- readiness score;
- safety confidence;
- expected speed score;
- earliest safe entry condition.

#### `TechniqueTick`

Returns data only:

- `SyntheticInputRequest`;
- `LogicalRotationRequest`;
- optional `PlacementRequest`;
- state transition or abort request;
- debug snapshot.

A technique never directly presses keys, mutates rotation, switches slots, or calls `useItemOn`.

### 6.3 Registry and selector

`TechniqueRegistry` owns factories and stable metadata. The initial registry contains Standard and Breezily.

`AutomaticTechniqueSelector`:

1. Filters disabled or incompatible techniques.
2. Scores remaining techniques using user priority, safety confidence, expected speed, recent failures, and transition cost.
3. Keeps the active technique while it remains safe and sufficiently competitive.
4. Requires a candidate to remain eligible for multiple observations before switching.
5. Switches only when both active and candidate techniques report a safe boundary.
6. Immediately aborts on a hard safety failure, bypassing hysteresis.

The selector does not implement movement or placement.

## 7. Shared input architecture

### 7.1 Per-key arbitration

Replace all-or-nothing movement ownership with explicit per-key arbitration:

```text
final key state = physical state OR permitted synthetic state
```

`InputArbiter` tracks separate ownership for:

- forward;
- backward;
- left;
- right;
- Sneak;
- Jump;
- Use Item.

It never releases a physically held key. Releasing synthetic Sneak while the user holds Sneak leaves Sneak down.

### 7.2 Manual override policy

- Activation release always aborts immediately.
- Physical Attack always cancels pending automated placement and aborts the active technique.
- Physical Use Item suppresses automated Use Item for that tick; repeated conflict aborts.
- Physical movement matching the active technique may merge with automation.
- Physical movement conflicting with the committed technique frame aborts before another synthetic movement request is accepted.
- Physical Sneak during forced Breezily triggers orderly abort because continued movement would no longer be authentic Breezily; the physical key remains held.
- Mouse movement in Silent Packet mode updates only visual camera state and does not abort.
- Mouse movement in Visible mode remains a manual camera override and aborts or yields according to the existing visible-rotation policy.

### 7.3 Pulse guarantees

Jump and Use Item pulses are represented by tick-scoped tokens. They expire automatically even if the active technique throws, aborts, disconnects, or changes.

## 8. Shared aim and rotation architecture

### 8.1 `RotationBroker`

The shared broker owns:

- the user's visual camera rotation;
- the active technique's logical player/server rotation;
- rotation smoothing in Visible mode;
- silent-camera overrides in Silent Packet mode;
- last logical rotation emitted through vanilla movement sending;
- restoration state during cleanup.

### 8.2 Visible mode

The broker applies the logical rotation to player yaw, pitch, and head rotation using the existing bounded-step algorithm. Placement is allowed once the visible rotation is within tolerance.

### 8.3 Silent Packet mode

Silent Packet mode separates visual camera state from logical player state:

- Mouse deltas update an independent visual yaw/pitch snapshot.
- Camera-view queries used for first-person rendering return the visual snapshot.
- The `LocalPlayer` yaw and pitch used by movement physics, logical ray calculation, and vanilla networking remain the technique's logical rotation.
- Vanilla `LocalPlayer#sendPosition` therefore emits the logical rotation at normal packet cadence and chooses normal movement packet variants.
- The mod does not emit extra position, velocity, teleport, or placement packets.
- The mod does not spam standalone rotation packets.

The mixin boundary is limited to:

1. Capturing mouse-turn intent into visual rotation while Silent Packet mode is active.
2. Returning visual rotation for camera rendering.
3. Recording when vanilla `LocalPlayer#sendPosition` has emitted the requested logical rotation.

Third-person player-model rotation is not guaranteed to remain visually aligned with the free-look camera. The requirement is that the user's camera does not move.

### 8.4 Logical raycasts

Shared placement validation must not use the visible camera ray in Silent Packet mode. `LogicalRaycast` computes the ray from:

- actual eye position;
- logical yaw and pitch;
- normal reach;
- the same block/entity clipping rules used by placement validation.

The resulting `BlockHitResult` must still identify a real adjacent face and cursor coordinate.

### 8.5 Placement ordering in Silent Packet mode

A silent placement is permitted only after the broker has observed vanilla networking emit a matching logical rotation. The usual sequence is:

1. Technique requests logical aim.
2. Broker applies logical rotation without moving the camera.
3. Vanilla `sendPosition` emits that rotation.
4. Broker records the sent rotation and sequence/tick.
5. On a later controller opportunity, the placement target is revalidated with a logical ray.
6. The ordinary placement pipeline pulses Use Item once.

This pre-aim gate avoids sending a block interaction before the server-facing rotation is established.

### 8.6 Restoration

Every exit path:

1. Cancels pending automated interaction.
2. Restores player yaw, pitch, and head rotation to the current visual camera rotation.
3. Clears camera overrides and logical ownership.
4. Allows the next vanilla movement send to restore the server-facing rotation naturally.
5. Releases all synthetic keys.

A server correction aborts silent aim instead of trying to fight the correction.

## 9. Shared placement pipeline

The existing block selection and placement validation remain common to all techniques.

A `PlacementRequest` contains:

- exact connected target position;
- placement kind;
- desired adjacent support block and face;
- desired hit point or valid hit-point search region;
- logical rotation request;
- earliest and latest safe placement conditions;
- confirmation deadline;
- technique-owned correlation ID.

The pipeline performs:

1. visible slot selection and optional restoration tracking;
2. reach validation;
3. exact block and face ray validation;
4. replaceability and placement-state validation;
5. player and entity collision validation;
6. logical-rotation readiness validation;
7. global placement-cadence validation;
8. one ordinary `MultiPlayerGameMode#useItemOn` attempt;
9. pending world confirmation;
10. success/failure delivery back to the active technique.

No two distinct placements may occur during one client tick.

## 10. Standard technique extraction

`StandardBridgeTechnique` owns the existing behavior without changing its user-visible semantics:

- straight and face-connected diagonal level bridging;
- upward staircase cycles;
- diagonal-upward staircase cycles;
- routine edge Sneak;
- Jump and two-tick staircase placement control;
- lower-block recovery;
- path switching based on stabilized movement intent.

It reuses:

- `BridgeHeadingTracker`;
- `BridgePathPlanner`;
- `MovementPlanner`;
- `EdgeDetector`;
- `VerticalIntentTracker`;
- `StaircaseCycle` and `LandingPredictor`.

The extraction should occur before Breezily behavior is introduced, so regression tests can prove that Standard remains equivalent to 1.1.0.

## 11. Breezily technique

### 11.1 Supported conditions

Breezily version one requires:

- level travel;
- a straight cardinal committed bridge axis;
- backward locomotion;
- a stable direction source: current physical movement intent when present, otherwise the nearest cardinal backward axis derived from the activation-time reference yaw within a bounded entry tolerance;
- ordinary full-width support collision at the player's feet;
- no obstacle in the movement corridor;
- no Jump, flight, glide, swim, climb, vehicle, or vertical displacement;
- a valid face-connected next target;
- a permitted hotbar block;
- sufficient initial centerline and support margin;
- no recent teleport, correction, or unresolved placement failure.

Slabs, stairs, paths, snow layers, narrow collision, uneven support, diagonal paths, and elevation changes are incompatible with forced Breezily and lower its Automatic evaluation to incompatible.

### 11.2 Bridge-local frame

`BreezilyFrame` stores:

- normalized along-track axis;
- normalized lateral axis;
- centerline origin;
- current support-block index;
- block-boundary origin;
- current block phase;
- active lateral side;
- current target amplitude.

Coordinates are projected every tick:

```text
along = dot(playerCenter - origin, alongAxis)
cross = dot(playerCenter - centerline, lateralAxis)
alongVelocity = dot(horizontalVelocity, alongAxis)
crossVelocity = dot(horizontalVelocity, lateralAxis)
phase = fractional progress within the current block
```

Small visual-camera movements never alter the committed frame. After frame capture, the technique owns synthetic backward movement and the alternating lateral keys; the user supplies activation and may supply compatible general direction input, but does not need to hold S or click. A deliberate conflicting direction change aborts forced Breezily.

### 11.3 Reference path

For each block:

- phase `0.00`: cross-track target is centerline;
- phase `0.50`: target is the current side's safe lateral amplitude;
- phase `1.00`: target is centerline;
- the following block uses the opposite side.

The controller chooses `LEFT`, `RIGHT`, or `NEUTRAL` using:

- current position error;
- cross-track velocity damping;
- predicted cross-track position at midpoint/boundary;
- remaining along-track distance;
- corridor margin;
- support prediction.

The actual key reversal may be commanded slightly before the mathematical midpoint so that physical lateral velocity reverses near the midpoint.

### 11.4 Corridor solver

`BreezilyCorridorSolver` calculates the permitted player-center corridor from real collision geometry and the player's bounding box. It returns:

- maximum safe cross-track displacement;
- target amplitude;
- warning and hard-abort boundaries;
- minimum support overlap;
- predicted recoverability at the next midpoint and boundary.

The target amplitude is clamped by configuration but may be reduced automatically. The controller never increases amplitude beyond the geometrically safe corridor merely to imitate a human tutorial path.

### 11.5 State machine

```text
INACTIVE
  -> VALIDATING_START
  -> CAPTURING_FRAME
  -> RUNNING
       -> EMERGENCY_BRAKE on hard failure
       -> COMPLETE_STOP on activation release
  -> ABORTED_LATCHED
  -> INACTIVE after activation release
```

#### `VALIDATING_START`

Checks all supported conditions and verifies that the first target and first complete lateral cycle are predicted to remain recoverable.

#### `CAPTURING_FRAME`

Captures the nearest valid cardinal bridge frame from stable intent or activation-time reference yaw, then selects the initial side using existing cross-track velocity and available margin. It does not choose randomly and does not accept a diagonal frame.

#### `RUNNING`

Runs three coordinated pure controllers:

- `BreezilyLocomotionController` for backward and lateral input;
- `BreezilyPlacementController` for target and deadline requests;
- `BreezilySafetyPredictor` for support and recovery forecasting.

#### `EMERGENCY_BRAKE`

- Stops synthetic backward and lateral movement.
- Cancels automated Use Item.
- Applies synthetic Sneak only when current support geometry predicts that Sneak can still prevent leaving support.
- Never releases physical Sneak.
- Performs no further placement.
- Restores rotation and releases synthetic state.
- Enters `ABORTED_LATCHED`.

#### `ABORTED_LATCHED`

No automated movement, aiming, or placement occurs until activation is released. This prevents repeated restart/fall loops while the activation key remains held.

### 11.6 Placement deadlines

The Breezily placement controller computes position-based deadlines:

- **pre-aim deadline:** latest point where logical rotation can be established before the valid interaction window;
- **attempt deadline:** latest recoverable point for the one normal placement attempt;
- **confirmation deadline:** latest point where the placed block must be observed before predicted support is lost.

Failure to meet a deadline is a hard abort. Low FPS and latency affect observations but do not convert deadlines into fixed delays.

### 11.7 Forced Breezily behavior

Forced Breezily never invokes the technique selector after activation. Any incompatible path, direction change, obstacle, vertical intent, missed placement, unresolved confirmation, invalid ray, block exhaustion, server correction, or conflicting physical input causes emergency abort rather than fallback.

## 12. Automatic mode

### 12.1 Initial selection rules

Breezily may be selected when:

- it is enabled for Automatic;
- the intended path is stable, backward, straight, and level;
- the player is grounded and within the entry corridor;
- upcoming support and target geometry are compatible;
- no staircase or recovery cycle is pending;
- recent corrections and failures are below cooldown thresholds;
- the selector predicts at least one complete Breezily block cycle safely.

Standard remains eligible for straight, diagonal, irregular, and rising paths.

### 12.2 Safe transitions

Automatic may transition Standard -> Breezily only on a confirmed, grounded, fully supported block boundary with no pending placement.

Automatic may schedule Breezily -> Standard when it predicts a turn, diagonal input, elevation change, incompatible support, obstacle, or degraded corridor. It transitions at the next safe supported boundary when possible.

A hard failure does not transition directly into active Standard movement. It emergency-brakes first. Standard may start only after the selector observes a fresh safe entry state.

### 12.3 Anti-flicker

- Candidate validity requires multiple observations.
- Active techniques have a minimum safe dwell period.
- Small camera movement never changes the committed path.
- Brief input noise does not immediately change technique.
- Recent hard failure adds a Breezily re-entry cooldown.
- Safety failure bypasses every dwell and hysteresis rule.

## 13. HUD and diagnostics

Compact HUD adds:

- selected mode;
- active technique;
- aim mode;
- active/transitioning/aborted state;
- block count;
- latest stop reason.

Breezily debug HUD adds:

- block index and phase;
- along/cross position;
- along/cross velocity;
- selected lateral side and commanded key;
- desired cross-track target;
- corridor warning/hard limits;
- predicted midpoint and boundary errors;
- placement deadline state;
- last placement and confirmation result;
- silent logical and visual rotations.

Debug output must be read-only and must not alter timing.

## 14. Codebase migration plan boundaries

The implementation plan should use these concrete boundaries:

### Retain and adapt

- `selection/BlockSelector.java`
- `placement/PlacementTargetFinder.java`
- `placement/PlacementValidator.java`
- `placement/PlacementCadence.java`
- `placement/PendingPlacement.java`
- `path/BridgePathPlanner.java`
- `support/CollisionSupportScanner.java`
- `state/CleanupPlan.java`

### Extract from `SpeedBridgeController`

- session lifecycle and context construction remain in the controller;
- Standard level/stair behavior moves to `technique/standard/StandardBridgeTechnique`;
- Automatic selection moves to `technique/AutomaticTechniqueSelector`;
- input application moves behind `input/InputArbiter`;
- aim delivery moves behind `rotation/RotationBroker`;
- placement execution moves behind `placement/PlacementPipeline`.

### New technique package

```text
technique/
  BridgeTechnique.java
  BridgeContext.java
  TechniqueCapabilities.java
  TechniqueEvaluation.java
  TechniqueTick.java
  TechniqueRegistry.java
  AutomaticTechniqueSelector.java
  standard/StandardBridgeTechnique.java
  breezily/BreezilyBridgeTechnique.java
  breezily/BreezilyFrame.java
  breezily/BreezilyCorridorSolver.java
  breezily/BreezilyLocomotionController.java
  breezily/BreezilyPlacementController.java
  breezily/BreezilySafetyPredictor.java
  breezily/BreezilyState.java
```

### New rotation package

```text
rotation/
  AimMode.java
  RotationBroker.java
  LogicalRotationRequest.java
  LogicalRaycast.java
  RotationSnapshot.java
  SilentRotationState.java
```

### Mixins

- one camera/mouse interception boundary for silent free look;
- one observation hook around vanilla `LocalPlayer#sendPosition` to record logical rotation emission;
- existing server-correction mixin remains and aborts the active technique.

No technique may contain direct packet-construction code.

## 15. Verification strategy

### 15.1 Pure unit tests

Technique framework:

- forced mode never calls selector;
- Automatic filters capabilities correctly;
- selection hysteresis and dwell rules;
- safe-transition requirements;
- failure cooldown and re-entry.

Breezily geometry and control:

- all four cardinal bridge axes;
- midpoint reversal around phase 0.5;
- no reversal at block boundaries;
- alternating left/right amplitude by block;
- continuity across boundaries;
- inertia-aware early braking;
- starting with existing lateral velocity;
- corridor reduction near safety limits;
- hard abort outside recoverable corridor;
- target and confirmation deadlines;
- low-FPS tick gaps using position-based phase;
- moderate delayed confirmation;
- full-block collision requirements.

Input:

- per-key merge;
- physical Sneak remains held;
- physical S can merge with synthetic A/D;
- conflicting movement aborts;
- attack/use conflict handling;
- every exit releases synthetic state.

Rotation:

- Visible mode moves camera and logical rotation together;
- Silent Packet mode keeps visual camera unchanged;
- mouse movement changes visual state only while silent;
- logical yaw governs movement frame;
- logical ray differs correctly from visual ray;
- vanilla send observation gates placement;
- cleanup restores visual rotation;
- no stale silent state after every exit.

### 15.2 Deterministic simulation tests

Recorded sequences vary:

- 20, 10, and 5 observed updates per second;
- horizontal speed and friction variation;
- speed/slowness effects within supported limits;
- delayed placement confirmation;
- small start-position offsets;
- lateral drift and sudden collision;
- camera movement during Silent Packet mode;
- direction changes near midpoint and boundary;
- block exhaustion and server correction.

Tests assert support overlap and command decisions, not only state names.

### 15.3 Fabric integration tests and guards

- Minecraft 26.2 compilation with Java 25.
- Mixin application and startup under `runClient`.
- Source guard forbidding direct position, velocity, teleport, duplicate interaction, and technique-owned packet construction.
- One-placement-per-tick invariant.
- Standard behavior regression suite.
- Config serialization/migration from 1.1.0.

### 15.4 Development-client smoke test

GitHub Actions should start the client under Xvfb, confirm SpeedBridge entrypoint and mixins load, and retain logs. This proves startup only, not gameplay correctness.

### 15.5 Required manual gameplay matrix

A human must test in a disposable world before claiming gameplay verification:

- forced Breezily in all four cardinal directions;
- Visible and Silent Packet aim;
- camera free-look through at least 32 blocks in Silent Packet mode;
- Automatic Standard -> Breezily -> Standard transitions;
- failed placement, block exhaustion, obstacle, screen, focus loss, teleport, and correction aborts;
- physical Sneak, movement, mouse, Use Item, and Attack override;
- 30, 60, 120+, and unstable FPS;
- single-player, local server, and a permitted multiplayer test server with measured latency;
- full blocks with different textures and non-full-block rejection;
- no stuck keys after every exit.

Results must report falls, missed placements, emergency-brake success, average blocks per second, and exact stop reasons.

## 16. Failure handling and cleanup invariants

The orchestrator must guarantee cleanup for:

- activation release;
- disable;
- technique abort;
- selector transition failure;
- screen opening;
- focus loss;
- death or unavailable player;
- dimension change;
- disconnect;
- teleport/server correction;
- no blocks;
- placement exception;
- mixin/rotation readiness failure.

After cleanup:

- no synthetic movement key is owned;
- Jump, Sneak, and Use Item pulses are absent;
- physical keys retain their actual state;
- no pending placement remains;
- no technique state remains active;
- silent camera interception is disabled;
- player/server rotation is restoring or restored to visual rotation;
- optional previous-slot restoration occurs exactly once.

## 17. Compatibility and risk boundaries

- Silent Packet aim changes the relationship between camera and server-facing rotation. Servers and anti-cheats may reject or flag it even though movement and placement use normal client paths.
- The mod must not claim universal multiplayer or anti-cheat compatibility.
- Extra rotation packet spam is explicitly avoided; vanilla movement sending remains authoritative.
- Technique timing must not depend on render FPS.
- The first implementation supports Minecraft 26.2 only. Version ports require fresh mapping and behavior verification.
- The existing GPL-3.0-or-later license and third-party notice remain applicable.

## 18. Non-goals

This feature does not include:

- Witchly or diagonal Breezily;
- upward Breezily staircase;
- Moonwalk, Godbridge, Telly, or speed-telly logic;
- position spoofing, velocity modification, teleporting, packet blinking, or placement packet duplication;
- bypass-specific anti-cheat behavior;
- server-side components;
- automatic sprint unless separately designed and approved;
- claiming gameplay correctness from unit tests or a headless startup test alone.

## 19. Completion criteria for implementation

Implementation is complete only when:

1. Standard behavior remains regression-tested.
2. Forced Breezily performs only the defined straight, level technique.
3. Automatic selection and safe transitions pass deterministic tests.
4. Visible and Silent Packet aim work through the same technique contract.
5. Silent mode preserves free-look camera movement without changing the committed technique frame.
6. Logical rotation is emitted through normal vanilla movement sending before placement.
7. All placement and reach invariants remain enforced.
8. Every cleanup path releases all synthetic state.
9. Java 25 Gradle tests and production build pass.
10. Development client starts without SpeedBridge mixin or entrypoint errors.
11. Manual gameplay results are reported honestly and distinguish verified behavior from remaining limitations.

## 20. Source references

- Mojang/Minecraft, “How to Speed Bridge!” — defines Breezily midpoint direction changes and distinguishes Witchly diagonal movement: `https://www.minecraft.net/pt-br/article/how-speed-bridge-`
- Minecraft 1.21.11 mapped `Entity` API — `moveRelative` and input-vector behavior: `https://mappings.dev/1.21.11/net/minecraft/world/entity/Entity.html`
- Minecraft 1.21.11 mapped `LocalPlayer` API — normal `sendPosition`/movement-packet path and last-sent rotation fields: `https://mappings.dev/1.21.11/net/minecraft/client/player/LocalPlayer.html`
- Minecraft 1.21.11 mapped `ServerboundMovePlayerPacket.Rot` API — normal rotation-bearing movement packet: `https://mappings.dev/1.21.11/net/minecraft/network/protocol/game/ServerboundMovePlayerPacket%24Rot.html`
- Minecraft 1.21.11 mapped `MultiPlayerGameMode` API — ordinary `useItemOn` interaction path: `https://mappings.dev/1.21.11/net/minecraft/client/multiplayer/MultiPlayerGameMode.html`
- Mojang bug MC-258862 — additional rotation packets contribute to movement-packet frequency accounting, supporting the decision to preserve vanilla movement-send cadence: `https://bugs.mojang.com/browse/MC-258862`
- Community historical references used only to understand human execution and terminology, not as authoritative engine specifications: Hypixel “Ultimate Bridging Index” and “Advanced FastBridging with Videos for Visual Learners.”
