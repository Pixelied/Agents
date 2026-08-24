# SpeedBridge Assist Breezily and Silent Aim Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor SpeedBridge Assist 1.1.0 into a registered technique framework, repair the confirmed staircase crash and backward-movement regression, add authentic straight-level Breezily bridging, and provide Visible and Silent Packet aim for every technique.

**Architecture:** Keep path, support, block-selection, placement-validation, and cleanup services shared. Each `BridgeTechnique` consumes an immutable `BridgeContext` and returns a data-only `TechniqueTick`. Separate raw physical intent, world-space movement execution, visual camera rotation, logical/server rotation, and placement ownership.

**Tech Stack:** Java 25, Minecraft Java 26.2, Fabric Loader 0.19.3, Fabric API 0.156.0+26.2, Fabric Loom 1.17.17, Gradle 9.6.1, JUnit 5, Sponge Mixin, GitHub Actions/Xvfb.

## Global constraints

- Never construct custom position, velocity, teleport, or block-placement packets.
- Silent aim uses the normal `LocalPlayer#sendPosition` path without packet spam.
- At most one assisted placement attempt per client tick.
- Physical state remains observable; no synthetic key may remain stuck.
- Forced Breezily never falls back to Standard or another technique.
- Breezily v1 is straight, level, backward, and has no routine Sneak.
- Standard retains straight, diagonal, upward, and diagonal-upward bridging.
- Every asynchronous interaction carries session, technique, and operation ownership.
- Automated tests and headless startup never substitute for gameplay verification.

---

### Task 1: Reproduce both 1.1.0 regressions

**Files:**
- Create `src/test/java/dev/adrien/speedbridge/stair/StaircaseOwnershipRegressionTest.java`
- Create `src/test/java/dev/adrien/speedbridge/input/BackwardMovementRegressionTest.java`
- Create `src/test/java/dev/adrien/speedbridge/controller/ControllerLifecycleRegressionTest.java`

- [ ] Write a failing test for `start -> reset -> lowerFailed`, asserting `!active || step != null`.
- [ ] Add the symmetric stale-upper case.
- [ ] Model pending stair placement -> grounded abort/reset -> timeout -> next tick and assert no exception or reactivation.
- [ ] Add the passing characterization that a 180-degree placement-yaw turn makes raw camera-relative S point opposite the committed backward world axis.
- [ ] Run:

```bash
./gradlew test --tests '*StaircaseOwnershipRegressionTest' \
               --tests '*BackwardMovementRegressionTest' \
               --tests '*ControllerLifecycleRegressionTest' --no-daemon
```

- [ ] Commit: `test: reproduce staircase crash and backward movement regression`.

### Task 2: Fix stale staircase placement ownership

**Files:**
- Create `placement/PlacementOwner.java`
- Modify `placement/PendingPlacement.java`, `placement/PlacementFailure.java`
- Modify `stair/StaircaseCycle.java`
- Modify `controller/SpeedBridgeController.java`

**Interfaces:**

```java
public record PlacementOwner(long sessionGeneration, String techniqueId, long operationGeneration) {}
```

- [ ] Extend every pending placement with an owner.
- [ ] Increment a staircase generation on every valid `start`.
- [ ] Change `lowerFailed` and `upperFailed` to accept the expected generation and ignore idle, null-step, or mismatched callbacks.
- [ ] Invalidate owned pending stair work before any staircase reset, stop, disable, disconnect, or grounded abort.
- [ ] Ignore stale timeout and confirmation results before they reach a state machine.
- [ ] Add a fail-closed controller guard for an impossible active/null-step state and `PlacementFailure.INTERNAL_STATE`.
- [ ] Run all staircase, controller lifecycle, standalone, and Gradle tests.
- [ ] Commit: `fix: reject stale staircase placement results`.

### Task 3: Add a world-space movement frame and repair physical S

**Files:**
- Create `input/RawInputIntent.java`, `MovementFrame.java`, `MovementCommand.java`, `MovementFrameAdapter.java`, `MovementOverrideState.java`
- Create `mixin/KeyboardInputMixin.java`
- Modify `input/InputController.java`, `controller/SpeedBridgeController.java`, `speedbridge.mixins.json`
- Create `input/MovementFrameAdapterTest.java`

**Interfaces:**

```java
public record MovementFrame(Vec2d forwardAxis, Vec2d rightAxis) {}
public record MovementCommand(double forward, double strafe) {
    public static final MovementCommand NONE = new MovementCommand(0, 0);
}
```

- [ ] Write failing all-cardinal tests proving physical S must stay aligned with the committed backward axis after logical/visible yaw changes.
- [ ] Implement frame-to-world and world-to-current-yaw conversion using dot products.
- [ ] Verify the exact Minecraft 26.2 keyboard-input class, update method, and mutable movement fields with Loom sources and `javap`; record them in `verification/26.2-input-hook.txt`.
- [ ] Apply converted movement impulses after vanilla keyboard input is read but before `LocalPlayer` consumes it.
- [ ] Keep raw hardware state separate for override detection.
- [ ] Accept matching S as backward intent; abort on conflicting W or unexpected lateral input.
- [ ] Confirm activation release, exception, screen, correction, and disconnect clear the movement override.
- [ ] Commit: `fix: preserve backward movement through placement rotation`.

### Task 4: Introduce session, context, and technique contracts

**Files:**
- Create `controller/BridgeSession.java`, `BridgeContext.java`, `BridgeContextBuilder.java`
- Create `technique/TechniqueId.java`, `TechniqueCapabilities.java`, `TechniqueEvaluation.java`, `TechniqueTick.java`, `TechniqueAbortReason.java`, `BridgeTechnique.java`

```java
public interface BridgeTechnique {
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

- [ ] Make `BridgeContext` immutable and include player/AABB/velocity, raw input, movement frame, support, path, block selection, tick, connection/screen/correction state, aim mode, and placement result.
- [ ] Ensure techniques do not poll `Minecraft.getInstance()`.
- [ ] Add contract tests for stable IDs, immutable context, capability validation, and zero commands.
- [ ] Commit: `refactor: add bridge technique contracts`.

### Task 5: Extract the correlated placement pipeline

**Files:**
- Create `placement/PlacementRequest.java`, `PlacementResult.java`, `PlacementPipeline.java`
- Modify `PlacementOwner.java`, `PendingPlacement.java`, `SpeedBridgeController.java`

- [ ] Define requests with exact target, kind, path step, logical rotation, owner, earliest tick, and latest safe tick.
- [ ] Move cadence, validation, Use Item pulse, pending confirmation, timeout, and stale-result handling into `PlacementPipeline`.
- [ ] Make the pipeline the only production owner of placement attempts.
- [ ] Test rotation-not-ready, valid placement, confirmation, timeout, session mismatch, technique switch, operation reset, and two requests in one tick.
- [ ] Delete controller-owned `processPendingPlacement` and `requestPlacement`.
- [ ] Commit: `refactor: centralize correlated placement lifecycle`.

### Task 6: Extract `StandardBridgeTechnique`

**Files:**
- Create `technique/standard/StandardBridgeTechnique.java`, `StandardState.java`
- Shrink `controller/SpeedBridgeController.java`
- Preserve existing path, vertical-intent, movement-planner, and staircase domain classes.

- [ ] Record golden contexts for straight, straight/diagonal transitions, all diagonal quadrants, level/stair transitions, all straight and diagonal stair headings, lower/upper failures, and physical S through rotation.
- [ ] Move Standard-specific state and decisions out of the controller.
- [ ] Return only `TechniqueTick`; do not press keys, rotate, select slots, or interact from the technique.
- [ ] Keep the committed movement frame independent of placement rotation.
- [ ] Run the complete 1.1.0 regression suite.
- [ ] Commit: `refactor: extract standard bridge technique`.

### Task 7: Replace ad-hoc key mutation with `InputArbiter`

**Files:**
- Create `input/InputArbiter.java`, `InputConflict.java`, `TickPulse.java`
- Modify `InputController.java`, `CleanupPlan.java`, `SpeedBridgeController.java`

- [ ] Track ownership for movement, Sneak, Jump, and Use Item.
- [ ] Make Jump and Use pulses tick-scoped and exception-safe.
- [ ] Never release physical Sneak.
- [ ] Let matching physical S merge as intent; abort conflicting input before another command is accepted.
- [ ] Make cleanup idempotent and test every stop reason plus a thrown technique tick.
- [ ] Commit: `refactor: arbitrate physical and synthetic input per control`.

### Task 8: Add `RotationBroker` and migrate Visible aim

**Files:**
- Create `aim/AimMode.java`, `LogicalRotationRequest.java`, `RotationReadiness.java`, `RotationBroker.java`
- Replace `placement/RotationController.java`
- Modify `SpeedBridgeController.java`

- [ ] Preserve existing visible smoothing with golden tests.
- [ ] Associate rotation requests with placement ownership.
- [ ] Make the broker the only class that mutates local yaw, pitch, or head rotation.
- [ ] Restore current visual rotation and clear readiness on every exit.
- [ ] Commit: `refactor: centralize visible rotation delivery`.

### Task 9: Implement Silent Packet aim

**Files:**
- Create `aim/SilentCameraState.java`, `LogicalRaycast.java`
- Create `mixin/MouseHandlerMixin.java`, `CameraMixin.java`, `LocalPlayerMixin.java`, `MovementPacketObserver.java`
- Modify `RotationBroker.java`, `PlacementValidator.java`, `speedbridge.mixins.json`, `tools/check-26.2-api.sh`

- [ ] Record exact 26.2 mouse, camera, and `LocalPlayer#sendPosition` descriptors in `verification/26.2-silent-aim-hooks.txt`.
- [ ] During Silent mode, route mouse deltas to a visual yaw/pitch snapshot instead of player logical rotation.
- [ ] Render first-person camera from the visual snapshot.
- [ ] Keep logical yaw/pitch on `LocalPlayer` for physics, logical raycasts, and vanilla networking.
- [ ] Observe rotation-bearing packets only inside the normal send-position path; never construct or send a packet.
- [ ] Mark rotation ready only after a matching logical rotation is observed in vanilla sending.
- [ ] Revalidate with a logical eye ray before ordinary Use Item.
- [ ] Test free look, movement-frame stability, restoration, server correction, and zero extra packet sends.
- [ ] Commit: `feat: add shared silent packet aim`.

### Task 10: Add registry, forced modes, and Automatic selection

**Files:**
- Create `technique/TechniqueRegistry.java`, `AutomaticTechniqueSelector.java`, `TechniqueSelection.java`
- Create `config/TechniqueMode.java`
- Modify `SpeedBridgeController.java`

- [ ] Register fresh Standard and Breezily factories by stable string ID.
- [ ] Forced Standard and Breezily never consult fallback selection.
- [ ] Score Automatic candidates by compatibility, safety, speed bias, failure penalty, and transition cost.
- [ ] Require stable eligibility and safe entry/exit boundaries; hard safety failures bypass hysteresis.
- [ ] Test noise, one-tick diagonal intent, stable straight runs, upward intent, obstacles, recent failure, and anti-flicker behavior.
- [ ] Commit: `feat: add registered bridge technique selection`.

### Task 11: Build pure Breezily geometry and corridor control

**Files:**
- Create `technique/breezily/BreezilyFrame.java`, `BreezilyReferencePath.java`, `BreezilyCorridor.java`, `BreezilyCorridorSolver.java`, `BreezilyControl.java`
- Add matching tests.

```java
public double targetCrossTrack(long blockIndex, double phase, double amplitude) {
    double side = (blockIndex & 1L) == 0 ? 1.0 : -1.0;
    double triangle = phase <= 0.5 ? phase * 2.0 : (1.0 - phase) * 2.0;
    return side * amplitude * triangle;
}
```

- [ ] Derive phase from real along-track position, not ticks.
- [ ] Solve a safe full-block corridor from player width, support bounds, margin, and predicted drift.
- [ ] Use position error, look-ahead error, and lateral-velocity damping to choose LEFT/RIGHT/NEUTRAL.
- [ ] Prove center at boundaries, alternating midpoint extremes, continuity, midpoint reversal, and low-FPS sample independence.
- [ ] Commit: `feat: add breezily reference path and corridor control`.

### Task 12: Implement Breezily cycle and safety deadlines

**Files:**
- Create `BreezilyPhase.java`, `BreezilyObservation.java`, `BreezilyCommand.java`, `BreezilyCycle.java`, `BreezilySafetyPredictor.java`

- [ ] Implement `IDLE -> VALIDATING_START -> CAPTURING_FRAME -> RUNNING` and `RUNNING -> EMERGENCY_BRAKE -> ABORTED_LATCHED`.
- [ ] Calculate aim and support deadlines from actual position and velocity.
- [ ] Retry only safe/retryable failures before the deadline.
- [ ] At no-return prediction, cancel movement/Use, apply emergency Sneak only when useful, and latch abort.
- [ ] Prove forced Breezily never invokes Standard under obstacle, diagonal, upward, missed-placement, collision, or physical-Sneak failures.
- [ ] Replay 20/10/5 update-per-second sequences and delayed confirmations.
- [ ] Commit: `feat: add breezily cycle and safety predictor`.

### Task 13: Integrate `BreezilyBridgeTechnique`

**Files:**
- Create `technique/breezily/BreezilyBridgeTechnique.java`
- Modify registry, context builder, and controller.

- [ ] Declare strict straight/level/full-block/Visible/Silent capabilities.
- [ ] Capture a stable cardinal bridge frame from raw backward intent and recent movement.
- [ ] Return `MovementCommand(-1, strafe)`, no routine Sneak, no Jump, one logical rotation, and at most one placement request.
- [ ] Test four cardinal directions, both aim modes, at least 32 simulated blocks, camera free look, physical S, midpoint alternation, and confirmation.
- [ ] Test safe Standard/Breezily Automatic transitions and hard-failure aborts.
- [ ] Commit: `feat: integrate breezily bridge technique`.

### Task 14: Add config, UI, HUD, and migration

**Files:**
- Modify `SpeedBridgeConfig.java`, `ConfigManager.java`, `SpeedBridgeConfigScreen.java`, HUD files, and `en_us.json`.
- Create `ConfigMigrationTest.java`.

- [ ] Persist Technique and Aim Mode by stable string ID.
- [ ] Add per-technique Automatic enable and safer/faster bias.
- [ ] Add bounded Breezily corridor, support-margin, phase-error, boundary-error, emergency-Sneak, and debug settings; no fixed A/D tick interval.
- [ ] Migrate missing 1.1.0 fields to Automatic + Visible safely.
- [ ] Show active technique, aim mode, phase, along/cross track, lateral velocity, deadline, owner, and abort reason in debug HUD.
- [ ] Commit: `feat: expose technique and aim settings`.

### Task 15: Full integration, CI, manual matrix, and 1.2.0 packaging

**Files:**
- Modify `.github/workflows/build.yml`, tools, README, and `gradle.properties`.
- Create integration/cleanup/recorded-sequence tests and `verification/MANUAL_GAMEPLAY_MATRIX.md`.

- [ ] Test cleanup for activation, disable, screen, focus, death, unavailable player, dimension, disconnect, correction, no blocks, placement exception, technique abort, selector failure, and Mixin failure.
- [ ] Replay low FPS, latency, camera turns, physical S, direction change during stair jump, stale stair timeout, and Breezily deadline failure.
- [ ] Run 100,000 pure technique ticks and check for unbounded state growth.
- [ ] CI must run clean Gradle tests/build, standalone tests, source guards, Mixin audit, and confirmed Xvfb client startup; upload JAR and evidence.
- [ ] Manually test Standard backward S in four directions, all Standard patterns, forced Breezily in four directions, both aim modes, 32-block Silent free look, Automatic transitions, varied FPS/latency, every abort/override path, full blocks, and non-full-block rejection.
- [ ] Record falls, misses, speed, brake success, stop reasons, and corrections honestly.
- [ ] Bump to `1.2.0` only after every automated gate and required manual row passes.
- [ ] Commit: `release: verify SpeedBridge Assist 1.2.0`.

## Parallel boundaries

After Tasks 1-5 freeze contracts, Input (Task 7), Aim (Tasks 8-9), Selector (Task 10), and pure Breezily domain work (Tasks 11-12) may run under separate leases. `SpeedBridgeController.java`, `speedbridge.mixins.json`, and `PlacementPipeline.java` remain single-owner integration choke points. Standard extraction must finish before Breezily integration changes orchestration.

## Self-review

- Every approved design requirement maps to a task.
- The reported crash and backward failure are mandatory Tasks 1-3.
- Types are introduced before later tasks consume them.
- No task allows duplicate placement or direct technique packet construction.
- Forced Breezily no-fallback behavior is tested.
- Silent aim covers free look, normal-send observation, logical raycast, and restoration.
- No placeholder work items remain.