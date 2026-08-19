# Crystal Anchor Combat Optimizer V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the planner-centric V1 runtime with an event-driven V2 Crystal/Anchor combat engine that prioritizes absolute lethal speed, honest damage prediction, typed server timing, same-base crystal recycling, and optional Mod Menu configuration while preserving legitimate vanilla 26.1.2 mechanics.

**Architecture:** Keep the verified simulation/legality core, but move hot combat decisions into a small reactive lane fed by immutable approvals in `CombatBlackboard`. A cheaper strategic scanner continuously refreshes target-local damage opportunities; `ReactiveCombatEngine` materializes already-approved actions on crystal spawn/removal/totem events, `ActionArbiter` performs only cheap current-state validation, and the existing vanilla dispatcher performs real client interactions. V1 remains available until V2 passes damage differential, latency, recycle, legality, and full GameTest gates.

**Tech Stack:** Java 25, Minecraft Java 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2, Fabric Loom 1.17-SNAPSHOT, Gradle 9.5.1, JUnit 5, Fabric GameTest, optional Mod Menu 18.0.0-beta.1.

**Spec:** `projects/crystal-anchor-combat-optimizer-26-1-2/docs/superpowers/specs/2026-08-19-crystal-optimizer-v2-design.md`

## Global Constraints

- Target Minecraft Java 26.1.2 and Java 25 only.
- Remain a client-side Fabric mod.
- Use only legitimate vanilla/client-observable state: no fabricated entity IDs, fake packets, hidden opponent inventory, impossible movement/state, silent/server-only rotations, fake crits, or fake server RNG knowledge.
- Never attack a newly placed crystal until its real server-observed entity ID exists.
- Same-base throughput is `break -> place -> wait for real spawn -> break -> place`, never simultaneous crystals in the same occupied placement volume.
- `LETHAL_SPEED` prioritizes immediate useful lethal damage and has no artificial stopwatch CPS cap in the reactive lane.
- Target damage uncertainty must be represented explicitly; self-damage uncertainty must be evaluated pessimistically.
- Do not introduce global damage multipliers, fudge factors, or hidden correction constants.
- Server explosion terrain destruction remains uncertain until server block updates make it observable.
- Future Client is behavioral/reference material only; do not copy its decompiled source unless a compatible license is independently established.
- Mod Menu is optional at runtime; the mod must load, toggle with O, and render its HUD without Mod Menu installed.
- Keep the existing `work/crystal-anchor-combat-optimizer-26-1-2` branch; this plan never deletes it.
- Target release version is `0.2.0`.
- Run commands below from the repository root unless a step explicitly changes directory.

---

## File Structure

### New main-source units

- `src/main/java/dev/adrien/crystaloptimizer/config/OptimizerStrategy.java` — semantic strategy preset enum.
- `src/main/java/dev/adrien/crystaloptimizer/config/OptimizerConfig.java` — validated immutable user config snapshot.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/ApprovalSlot.java` — named blackboard approval slots.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/ReactiveActionSpec.java` — sealed approved-action template contract.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/FixedActionSequence.java` — pre-materialized legal action sequence.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/SpawnCrystalCycle.java` — materializes a real spawned entity ID into break/recycle actions.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java` — short-lived revisioned approval with damage/timing evidence.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardSnapshot.java` — immutable target/revision/approval snapshot.
- `src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboard.java` — atomic publication/invalidation of snapshots.
- `src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageUncertainty.java` — explicit uncertainty provenance.
- `src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageScenario.java` — weighted exact simulator input scenario.
- `src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEstimate.java` — lower/expected/upper estimate.
- `src/main/java/dev/adrien/crystaloptimizer/v2/damage/LiveDamageTrace.java` — diagnostic wrapper around simulator trace and estimate.
- `src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngine.java` — aggregates exact scenario simulation into honest intervals.
- `src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingTransition.java` — typed observable timing classes.
- `src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingCorrelation.java` — collision-safe action/event correlation key.
- `src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingDistribution.java` — p50/p90/dispersion/confidence snapshot.
- `src/main/java/dev/adrien/crystaloptimizer/v2/timing/SequenceTiming.java` — actual candidate-sequence completion estimate.
- `src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngine.java` — rolling typed transition distributions.
- `src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java` — reserves predicted/in-flight item consumption.
- `src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java` — cheap current-state interface for arbitration.
- `src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java` — accepted/rejected approval result.
- `src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java` — final cheap legality/revision/resource gate.
- `src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEvent.java` — sealed event contract.
- `src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBasePhase.java` — recycle lifecycle enum.
- `src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTracker.java` — per-base lifecycle and duplicate suppression.
- `src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveDecision.java` — materialized action burst plus source approval.
- `src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngine.java` — event-driven approval selection/materialization.
- `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java` — target/self/timing evidence for one immediate opportunity.
- `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMap.java` — bounded target-local opportunities with revision keys.
- `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java` — Future-style max-useful-damage/lethal-time selector.
- `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtThresholdEstimate.java` — known/derived/unknown progressive-damage threshold envelope.
- `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTracker.java` — attributed threshold history for staircase decisions.
- `src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java` — event/decision/dispatch/result timestamps.

### New client-source units

- `src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigService.java` — load/save/atomically publish config.
- `src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java` — compact Mod Menu/main config screen.
- `src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java` — read-only advanced diagnostics.
- `src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java` — optional `ModMenuApi` entrypoint.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java` — direct packet/world event fan-out to V2 coordinator.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java` — typed timing correlation adapter.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java` — lightweight current legality/resource/revision view.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageScenarioFactory.java` — visible-state/prediction scenarios for `DamageEngine`.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java` — incremental target-local damage map/cache.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java` — bounded sticky target selection without beam search.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java` — refreshes blackboard approvals outside the hot event path.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java` — cached V2 diagnostics snapshot.
- `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java` — enable state, scan tick, event handling, dispatch, reconciliation.

### Existing units deliberately reused or adapted

- `sim/damage/*`, `sim/model/*`, `world/*` — low-level verified mechanics.
- `action/*` — legal action representation and simulation.
- `prediction/*` — target position hypotheses.
- `execution/InventoryCoordinator.java` — cross-module/offhand-hotbar reservation ownership.
- `client/execution/RotationController.java` and `VanillaInteractionDispatcher.java` — real rotations/interactions.
- `client/intel/ClientObservationBus.java` — opponent evidence; extended to emit reactive events.
- `client/mixin/ClientPacketListenerMixin.java` and `ClientCommonPacketListenerImplMixin.java` — packet observation hooks.
- `OptimizerHud.java` — rendering shell; switched to cached V2 diagnostics.
- `CrystalOptimizerClient.java` — reduced to bootstrap/toggle wiring after V2 acceptance.

---

### Task 1: Establish V2 config contracts and 0.2.0 build metadata

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerStrategy.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerConfig.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/gradle.properties`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/build.gradle`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/config/OptimizerConfigTest.java`

**Interfaces:**
- Produces: `OptimizerConfig.defaults()`, `OptimizerConfig.validated()`, and `OptimizerStrategy.LETHAL_SPEED|AGGRESSIVE|SAFE`.
- Reuses: existing `dev.adrien.crystaloptimizer.execution.RotationMode`.

- [ ] **Step 1: Write the failing config validation test**

```java
package dev.adrien.crystaloptimizer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.adrien.crystaloptimizer.execution.RotationMode;
import org.junit.jupiter.api.Test;

final class OptimizerConfigTest {
    @Test
    void defaultsExpressLethalSpeedPolicy() {
        OptimizerConfig config = OptimizerConfig.defaults();
        assertEquals(OptimizerStrategy.LETHAL_SPEED, config.strategy());
        assertEquals(RotationMode.ADAPTIVE, config.rotationMode());
        assertEquals(true, config.crystals());
        assertEquals(true, config.anchors());
        assertEquals(true, config.autoRestock());
    }

    @Test
    void rejectsOutOfRangeCombatValues() {
        OptimizerConfig invalid = new OptimizerConfig(
            true, OptimizerStrategy.LETHAL_SPEED, 0.5, 4.0f, 12.0f, 8.0f,
            true, true, true, RotationMode.ADAPTIVE, true
        );
        assertThrows(IllegalArgumentException.class, invalid::validated);
    }
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:
```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.config.OptimizerConfigTest
```
Expected: compilation failure because the V2 config types do not exist.

- [ ] **Step 3: Add immutable config types and build metadata**

```java
package dev.adrien.crystaloptimizer.config;

public enum OptimizerStrategy {
    LETHAL_SPEED,
    AGGRESSIVE,
    SAFE
}
```

```java
package dev.adrien.crystaloptimizer.config;

import dev.adrien.crystaloptimizer.execution.RotationMode;
import java.util.Objects;

public record OptimizerConfig(
    boolean enabled,
    OptimizerStrategy strategy,
    double targetRange,
    float minDamage,
    float maxSelfDamage,
    float facePlaceHealth,
    boolean crystals,
    boolean anchors,
    boolean autoRestock,
    RotationMode rotationMode,
    boolean hud
) {
    public OptimizerConfig {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(rotationMode, "rotationMode");
    }

    public static OptimizerConfig defaults() {
        return new OptimizerConfig(
            false, OptimizerStrategy.LETHAL_SPEED, 12.0, 4.0f, 12.0f, 8.0f,
            true, true, true, RotationMode.ADAPTIVE, true
        );
    }

    public OptimizerConfig validated() {
        requireRange("targetRange", targetRange, 1.0, 16.0);
        requireRange("minDamage", minDamage, 0.0, 40.0);
        requireRange("maxSelfDamage", maxSelfDamage, 0.0, 40.0);
        requireRange("facePlaceHealth", facePlaceHealth, 0.0, 40.0);
        return this;
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
        }
    }
}
```

Update `gradle.properties` to `mod_version=0.2.0` and add `modmenu_version=18.0.0-beta.1`. Add the Terraformers Maven repository and `implementation "com.terraformersmc:modmenu:${project.modmenu_version}"` to `build.gradle`; do not add a hard Fabric metadata dependency.

- [ ] **Step 4: Run focused and baseline unit tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.config.OptimizerConfigTest
gradle --no-daemon test
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2
git commit -m "feat: define optimizer v2 config"
```

---

### Task 2: Build the immutable CombatBlackboard and approval templates

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ApprovalSlot.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ReactiveActionSpec.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/FixedActionSequence.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/SpawnCrystalCycle.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardSnapshot.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboard.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardTest.java`

**Interfaces:**
- Produces: atomic `CombatBlackboard.snapshot()` / `publish(...)`; `ActionApproval.isCurrent(...)`; reactive templates that materialize only from compatible events.
- Consumes later: `DamageEstimate` and `SequenceTiming`; create temporary minimal records in this task only if compiler ordering requires it, then replace them in Tasks 3 and 5 in the same branch before merging.

- [ ] **Step 1: Write the failing blackboard immutability/invalidation test**

```java
@Test
void publishReplacesSnapshotAtomicallyAndRejectsExpiredApproval() {
    CombatBlackboard board = new CombatBlackboard();
    UUID target = UUID.randomUUID();
    ActionApproval approval = TestApprovals.fixed(target, 4L, 8L, 12L, 16L, 1_000L);
    board.publish(new CombatBlackboardSnapshot(
        target, 8L, 4L, 12L, 16L,
        Map.of(ApprovalSlot.BREAK, approval)
    ));

    CombatBlackboardSnapshot snapshot = board.snapshot();
    assertEquals(target, snapshot.targetId());
    assertTrue(snapshot.approval(ApprovalSlot.BREAK).orElseThrow()
        .isCurrent(4L, 8L, 12L, 16L, 999L));
    assertFalse(snapshot.approval(ApprovalSlot.BREAK).orElseThrow()
        .isCurrent(4L, 8L, 12L, 16L, 1_001L));
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.state.CombatBlackboardTest
```
Expected: compilation failure for missing blackboard types.

- [ ] **Step 3: Implement approval/template contracts**

```java
public enum ApprovalSlot {
    LETHAL,
    FINISHER,
    STAIRCASE,
    RECYCLE,
    BREAK,
    PLACE,
    PRESSURE,
    PREPARE
}
```

```java
public sealed interface ReactiveActionSpec permits FixedActionSequence, SpawnCrystalCycle {
    List<CombatAction> materialize(CombatEvent event);
}
```

```java
public record FixedActionSequence(List<CombatAction> actions) implements ReactiveActionSpec {
    public FixedActionSequence {
        actions = List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("actions must not be empty");
    }

    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        return actions;
    }
}
```

```java
public record SpawnCrystalCycle(BlockPos basePos, boolean replaceAfterBreak) implements ReactiveActionSpec {
    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        if (!(event instanceof CombatEvent.CrystalSpawned spawned) || !spawned.basePos().equals(basePos)) {
            return List.of();
        }
        CombatAction attack = new AttackKnownCrystal(spawned.entityId());
        return replaceAfterBreak
            ? List.of(attack, new PlaceCrystal(basePos))
            : List.of(attack);
    }
}
```

`ActionApproval` must carry `approvalId`, `targetId`, `ApprovalSlot`, `ReactiveActionSpec`, target/self damage evidence, timing evidence, world/target/inventory/config revisions, and `expiresAtNanos`. `CombatBlackboardSnapshot` must defensively copy its approval map, and `CombatBlackboard` must store the current snapshot in `AtomicReference`.

- [ ] **Step 4: Run blackboard tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.state.*'
```
Expected: PASS with immutable maps/lists and revision/expiry checks covered.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/state
git commit -m "feat: add v2 combat blackboard"
```

---

### Task 3: Add DamageEstimate intervals and scenario aggregation

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageUncertainty.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageScenario.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEstimate.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/LiveDamageTrace.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngineTest.java`
- Reuse: existing `ExplosionDamageCalculator26`, `VanillaDamageSimulator`, `DamageTrace`, `CombatState`, `ExplosionContext`.

**Interfaces:**
- `DamageScenario(SimCombatant victim, Vec3 position, AABB box, double weight, Set<DamageUncertainty> uncertainties)`.
- `DamageEngine.estimate(ExplosionContext, CombatState, UUID, List<DamageScenario>, long geometryRevision, long combatRevision)` returns `DamageEstimate`.
- `DamageEstimate` exposes `lowerBound()`, `expected()`, `upperBound()`, `confidence()`, `uncertainties()`, revisions, and `exact()`.

- [ ] **Step 1: Write failing exact-vs-uncertain aggregation tests**

```java
@Test
void exactSingleScenarioCollapsesInterval() {
    DamageEstimate estimate = engine.estimate(
        explosion,
        state,
        targetId,
        List.of(new DamageScenario(target, targetPos, targetBox, 1.0, Set.of())),
        7L,
        11L
    );
    assertTrue(estimate.exact());
    assertEquals(estimate.lowerBound(), estimate.expected(), 1.0e-5f);
    assertEquals(estimate.expected(), estimate.upperBound(), 1.0e-5f);
}

@Test
void uncertainScenariosContainEveryExactOutcome() {
    DamageEstimate estimate = engine.estimate(
        explosion,
        state,
        targetId,
        List.of(
            new DamageScenario(targetA, posA, boxA, 0.7, Set.of(DamageUncertainty.PREDICTED_POSITION)),
            new DamageScenario(targetB, posB, boxB, 0.3, Set.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN))
        ),
        8L,
        12L
    );
    assertTrue(estimate.lowerBound() <= estimate.expected());
    assertTrue(estimate.expected() <= estimate.upperBound());
    assertFalse(estimate.exact());
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageEngineTest
```

- [ ] **Step 3: Implement scenario aggregation without fudge factors**

```java
public DamageEstimate estimate(
    ExplosionContext explosion,
    CombatState state,
    UUID victimId,
    List<DamageScenario> scenarios,
    long geometryRevision,
    long combatRevision
) {
    if (scenarios.isEmpty()) throw new IllegalArgumentException("scenarios must not be empty");
    float lower = Float.POSITIVE_INFINITY;
    float upper = Float.NEGATIVE_INFINITY;
    double weighted = 0.0;
    double weightTotal = 0.0;
    Set<DamageUncertainty> reasons = EnumSet.noneOf(DamageUncertainty.class);

    for (DamageScenario scenario : scenarios) {
        float incoming = ExplosionDamageCalculator26.incoming(
            explosion, scenario.box(), scenario.position(), state.geometry()
        );
        DamageResult result = VanillaDamageSimulator.apply(
            scenario.victim(),
            DamageRequest.explosion(incoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );
        float healthDamage = result.trace().healthDamage();
        lower = Math.min(lower, healthDamage);
        upper = Math.max(upper, healthDamage);
        weighted += healthDamage * scenario.weight();
        weightTotal += scenario.weight();
        reasons.addAll(scenario.uncertainties());
    }

    float expected = (float)(weighted / weightTotal);
    double confidence = reasons.isEmpty() ? 1.0 : Math.max(0.0, Math.min(1.0, weightTotal));
    return new DamageEstimate(lower, expected, upper, confidence, reasons, geometryRevision, combatRevision);
}
```

`DamageUncertainty` must include `PREDICTED_POSITION`, `HURT_THRESHOLD_UNKNOWN`, `ABSORPTION_UNKNOWN`, `TERRAIN_UNOBSERVED`, `ARMOR_STATE_STALE`, `EFFECT_STATE_STALE`, and `PENDING_SERVER_ACCEPTANCE`.

- [ ] **Step 4: Run damage unit tests including existing simulator tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.damage.*' --tests 'dev.adrien.crystaloptimizer.sim.damage.*'
```
Expected: PASS; no existing exact simulator behavior changes.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/damage
git commit -m "feat: add v2 damage intervals"
```

---

### Task 4: Add vanilla differential damage/exposure GameTests

**Files:**
- Create: `.../src/gametest/java/dev/adrien/crystaloptimizer/gametest/ExplosionDifferentialGameTests.java`
- Modify: `.../src/gametest/java/dev/adrien/crystaloptimizer/gametest/CrystalOptimizerGameTests.java` only if shared test registration/helpers are needed.
- Test: GameTest runtime.

**Interfaces:**
- Produces reusable `assertExplosionEstimateMatchesVanilla(...)` fixture that executes a real server explosion and compares attributable health loss to the simulator/estimate.
- Keeps existing pure simulator tests as fast unit coverage.

- [ ] **Step 1: Add a failing exposed-target crystal differential GameTest**

```java
@GameTest
public void exposedCrystalDamageMatchesVanilla(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    ServerPlayer target = helper.makeMockServerPlayerInLevel();
    Vec3 explosionCenter = target.position().add(2.5, 0.0, 0.0);
    float before = target.getHealth();

    float predictedRaw = ExplosionDamageCalculator26.incoming(
        ExplosionContext.crystal(explosionCenter),
        target.getBoundingBox(),
        target.position(),
        blockView(level)
    );
    DamageResult predicted = VanillaDamageSimulator.apply(
        observed(target),
        DamageRequest.explosion(predictedRaw)
            .withDifficulty(level.getDifficulty())
            .withSourcePosition(explosionCenter)
    );

    level.explode(null, explosionCenter.x, explosionCenter.y, explosionCenter.z, 6.0f, Level.ExplosionInteraction.NONE);
    float observedLoss = before - target.getHealth();
    helper.assertTrue(
        Math.abs(observedLoss - predicted.trace().healthDamage()) <= 1.0e-4f,
        "vanilla and simulator crystal damage diverged"
    );
    helper.succeed();
}
```

Use the exact 26.1.2 `ServerLevel` explosion overload and mock-player helper available in the checked-in/decompiled mappings when implementing; the assertion and compared quantities above are fixed requirements.

- [ ] **Step 2: Run GameTests and confirm the new differential test exposes any mapping/behavior gaps**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon runGameTest --stacktrace
```
Expected before fixture completion: compile/runtime failure isolated to the new differential fixture, not existing unit tests.

- [ ] **Step 3: Complete the fixture and add the required matrix**

Add named GameTests for:

```text
exposedCrystalDamageMatchesVanilla
partialCoverCrystalDamageMatchesVanilla
slabExposureMatchesVanilla
stairExposureMatchesVanilla
hardDifficultyScalingMatchesVanilla
resistanceMatchesVanilla
blastProtectionMatchesVanilla
armorBreakOrderingMatchesVanilla
anchorDamageMatchesVanilla
strongerProtectedFollowupMatchesVanilla
totemThenFollowupMatchesVanilla
```

Each test must compare real server-applied health/totem outcome with the project calculation; no hardcoded expected damage constants are allowed where vanilla itself can provide the oracle.

- [ ] **Step 4: Run the full GameTest matrix twice**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon runGameTest --stacktrace
gradle --no-daemon runGameTest --stacktrace
```
Expected: PASS twice to catch state leakage/flaky fixture setup.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/gametest
git commit -m "test: differential-check explosion damage"
```

---

### Task 5: Replace generic RTT heuristics with typed TimingEngine V2

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingTransition.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingCorrelation.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingDistribution.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/SequenceTiming.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngineTest.java`
- Keep for migration comparison: existing `timing/ServerTimingModel.java` until Task 16.

**Interfaces:**
- `TimingEngine.recordStart(TimingCorrelation,long)` / `recordEnd(TimingCorrelation,long)`.
- `TimingEngine.distribution(TimingTransition,long)`.
- `TimingEngine.estimateSequence(List<TimingTransition>,long)`.

- [ ] **Step 1: Write failing percentile/freshness/sequence tests**

```java
@Test
void estimatesTypedPlaceToSpawnDistributionAndSequence() {
    TimingEngine engine = new TimingEngine(64, 5_000_000_000L);
    long base = 1_000_000_000L;
    for (int i = 0; i < 10; i++) {
        TimingCorrelation key = TimingCorrelation.block(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            new BlockPos(i, 64, 0)
        );
        engine.recordStart(key, base + i * 100_000_000L);
        engine.recordEnd(key, base + i * 100_000_000L + (20L + i) * 1_000_000L);
    }

    TimingDistribution distribution = engine.distribution(
        TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
        base + 1_100_000_000L
    );
    assertEquals(10, distribution.sampleCount());
    assertTrue(distribution.p90Millis() >= distribution.p50Millis());

    SequenceTiming sequence = engine.estimateSequence(
        List.of(TimingTransition.IMMEDIATE, TimingTransition.CRYSTAL_PLACE_TO_SPAWN),
        base + 1_100_000_000L
    );
    assertEquals(1, sequence.hardFeedbackBoundaries());
}
```

- [ ] **Step 2: Run focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.timing.TimingEngineTest
```

- [ ] **Step 3: Implement typed rolling distributions**

```java
public enum TimingTransition {
    IMMEDIATE(false),
    BLOCK_INTERACTION_TO_ACK(true),
    CRYSTAL_PLACE_TO_SPAWN(true),
    CRYSTAL_ATTACK_TO_REMOVAL(true),
    TOTEM_POP_TO_VISIBLE_REFILL(true),
    SERVER_UPDATE_CADENCE(false);

    private final boolean hardFeedback;
    TimingTransition(boolean hardFeedback) { this.hardFeedback = hardFeedback; }
    public boolean hardFeedback() { return hardFeedback; }
}
```

`TimingEngine` must keep separate bounded sample deques per transition, calculate p50/p90 from sorted completed durations, use median absolute deviation for dispersion, decay confidence with sample age, and sum only actual hard-feedback transition distributions when estimating candidate completion time. Unknown distributions return confidence `0.0` and conservative sequence timing rather than a made-up one-tick probability.

- [ ] **Step 4: Run typed timing and existing timing tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.timing.*' --tests 'dev.adrien.crystaloptimizer.timing.*'
```
Expected: PASS; V1 timing remains intact for comparison.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/timing
git commit -m "feat: add typed server timing engine"
```

---

### Task 6: Wire packet/world observations into typed combat events and TIME_TO_DAMAGE tracing

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEvent.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/intel/ClientObservationBus.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEventTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2PacketObservationArchitectureTest.java`

**Interfaces:**
- `ClientCombatEventBus.subscribe(Consumer<CombatEvent>)` and synchronous `publish(CombatEvent)`.
- `ClientTimingObserver` owns one `TimingEngine` and translates packet/action correlations.
- Events include crystal spawn/removal, block ack/change, totem pop, equipment change, target movement invalidation, inventory change, and config change.

- [ ] **Step 1: Write failing event immutability and architecture tests**

```java
@Test
void crystalSpawnCarriesRealEntityAndBaseIdentity() {
    CombatEvent event = new CombatEvent.CrystalSpawned(
        431,
        new BlockPos(10, 63, -4),
        9_000L
    );
    CombatEvent.CrystalSpawned spawned = (CombatEvent.CrystalSpawned) event;
    assertEquals(431, spawned.entityId());
    assertEquals(new BlockPos(10, 63, -4), spawned.basePos());
    assertEquals(9_000L, spawned.timestampNanos());
}
```

Architecture test requirements:
```text
ClientPacketListenerMixin observes handleAddEntity at TAIL.
ClientPacketListenerMixin observes handleRemoveEntities before vanilla mutation when old entity identity is needed.
EntityEvent PROTECTED_FROM_DEATH still feeds OpponentIntelService and also emits TotemPopped.
BlockChangedAck still records its real sequence.
No mixin invokes BeamPlanner, CandidateGenerator, or ClientCombatSnapshotBuilder.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*' --tests dev.adrien.crystaloptimizer.client.V2PacketObservationArchitectureTest
```

- [ ] **Step 3: Implement event bus and observation adapters**

```java
public sealed interface CombatEvent {
    long timestampNanos();

    record CrystalSpawned(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {}
    record CrystalRemoved(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {}
    record TotemPopped(UUID targetId, long timestampNanos) implements CombatEvent {}
    record EquipmentChanged(UUID targetId, long timestampNanos) implements CombatEvent {}
    record BlockAcked(int sequence, long timestampNanos) implements CombatEvent {}
    record BlockChanged(BlockPos pos, long timestampNanos) implements CombatEvent {}
    record InventoryChanged(long revision, long timestampNanos) implements CombatEvent {}
    record TargetMoved(UUID targetId, long targetRevision, long timestampNanos) implements CombatEvent {}
    record ConfigChanged(long configRevision, long timestampNanos) implements CombatEvent {}
}
```

Spawn handling must derive the base from the real spawned `EndCrystal` position after vanilla creates it; removal handling must publish the entity/base identity before it is no longer recoverable. Existing opponent-intel calls remain intact.

- [ ] **Step 4: Run observation tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*' --tests 'dev.adrien.crystaloptimizer.client.*Observation*'
```
Expected: PASS; no planner/snapshot work appears in packet handlers.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: emit v2 combat timing events"
```

---

### Task 7: Add in-flight item reservations and lightweight live combat view

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedgerTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ClientLiveCombatViewArchitectureTest.java`

**Interfaces:**
- `reserve(long actionId, Item item, int count)`, `release(long actionId)`, `reserved(Item)`, and `available(Item,int observedCount)`.
- `LiveCombatView` provides only cheap current checks needed by `ActionArbiter`; it never builds `CombatSnapshot`.

- [ ] **Step 1: Write failing reservation tests**

```java
@Test
void predictedPlacementCannotDoubleSpendVisibleStack() {
    PendingItemLedger ledger = new PendingItemLedger();
    ledger.reserve(100L, Items.END_CRYSTAL, 1);
    assertEquals(0, ledger.available(Items.END_CRYSTAL, 1));
    assertThrows(IllegalStateException.class, () -> ledger.reserve(101L, Items.END_CRYSTAL, 1, 1));
    ledger.release(100L);
    assertEquals(1, ledger.available(Items.END_CRYSTAL, 1));
}
```

Use the overload `reserve(long actionId, Item item, int count, int observedCount)` for availability-checked reservations; the three-argument overload is for prevalidated callers.

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.PendingItemLedgerTest
```

- [ ] **Step 3: Implement ledger and live-view contract**

```java
public interface LiveCombatView {
    long worldRevision();
    long targetRevision(UUID targetId);
    long inventoryRevision();
    boolean targetValid(UUID targetId);
    boolean liveCrystal(int entityId);
    boolean withinEntityReach(int entityId);
    boolean withinBlockReach(BlockPos pos);
    boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId);
    int observedCount(Item item);
    int selectedHotbarSlot();
}
```

`ClientLiveCombatView` reads current `Minecraft.player`, `Minecraft.level`, known entity/block state, and monotonically maintained revisions only. Architecture tests must reject references to `BeamPlanner`, `CandidateGenerator`, or `ClientCombatSnapshotBuilder` in this class.

- [ ] **Step 4: Run ledger/live-view tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.execution.*' --tests dev.adrien.crystaloptimizer.client.ClientLiveCombatViewArchitectureTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: reserve predicted combat inventory"
```

---

### Task 8: Implement the final cheap ActionArbiter

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiterTest.java`

**Interfaces:**
- `ActionArbiter.evaluate(ActionApproval,List<CombatAction>,LiveCombatView,PendingItemLedger,OptimizerConfig,long)`.
- Produces `ArbitrationResult.allowed(actions)` or typed rejection reason without battlefield rescoring.

- [ ] **Step 1: Write failing stale/illegal/ordered-transition tests**

```java
@Test
void allowsBreakThenSameBasePlaceAgainstPredictedPostBreakState() {
    ActionApproval approval = approvalForRecycle(targetId, crystalId, basePos);
    LiveCombatView view = fakeView()
        .withLiveCrystal(crystalId, true)
        .withFollowBreakBase(basePos, crystalId, true)
        .withObservedCount(Items.END_CRYSTAL, 1);

    ArbitrationResult result = arbiter.evaluate(
        approval,
        List.of(new AttackKnownCrystal(crystalId), new PlaceCrystal(basePos)),
        view,
        new PendingItemLedger(),
        OptimizerConfig.defaults(),
        500L
    );
    assertTrue(result.allowed());
}

@Test
void rejectsInventedOrAlreadyRemovedCrystal() {
    ArbitrationResult result = arbiter.evaluate(
        breakApproval(targetId, 999),
        List.of(new AttackKnownCrystal(999)),
        fakeView().withLiveCrystal(999, false),
        new PendingItemLedger(),
        OptimizerConfig.defaults(),
        500L
    );
    assertEquals(ArbitrationResult.Reason.CRYSTAL_NOT_LIVE, result.reason());
}
```

- [ ] **Step 2: Run focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.ActionArbiterTest
```

- [ ] **Step 3: Implement the arbiter as a linear action/revision gate**

The method must check, in order: approval expiry/revisions, target validity, approval worst-case self damage vs config policy, each concrete action's current reach/entity/resource condition, special `AttackKnownCrystal -> PlaceCrystal(same base)` predicted-post-break legality, and pending item availability. It must not call candidate generation, damage simulation, target prediction, or beam search.

```java
public ArbitrationResult evaluate(
    ActionApproval approval,
    List<CombatAction> actions,
    LiveCombatView view,
    PendingItemLedger pendingItems,
    OptimizerConfig config,
    long nowNanos
) {
    if (!approval.isCurrent(
        view.worldRevision(),
        view.targetRevision(approval.targetId()),
        view.inventoryRevision(),
        approval.configRevision(),
        nowNanos
    )) return ArbitrationResult.rejected(ArbitrationResult.Reason.STALE_APPROVAL);
    if (!view.targetValid(approval.targetId())) {
        return ArbitrationResult.rejected(ArbitrationResult.Reason.INVALID_TARGET);
    }
    return validateActions(approval, actions, view, pendingItems, config);
}
```

- [ ] **Step 4: Run arbiter and legacy legality tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.ActionArbiterTest --tests dev.adrien.crystaloptimizer.action.ActionLegalityTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiterTest.java
git commit -m "feat: gate v2 reactive actions"
```

---

### Task 9: Implement the event-driven ReactiveCombatEngine and recycle state machine

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBasePhase.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTracker.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveDecision.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngineTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTrackerTest.java`

**Interfaces:**
- `ReactiveCombatEngine.decide(CombatEvent,CombatBlackboardSnapshot,long)` returns zero or one highest-priority `ReactiveDecision`.
- `ReactiveDecision` contains approval ID, source slot, materialized action list, event timestamp, decision timestamp, and `critical=true` for lethal/finisher/staircase/recycle events.
- No world scan or planning dependencies.

- [ ] **Step 1: Write failing priority/materialization/duplicate tests**

```java
@Test
void spawnedCrystalOnApprovedRecycleBaseMaterializesRealEntityIdImmediately() {
    CombatBlackboardSnapshot snapshot = snapshotWith(
        ApprovalSlot.RECYCLE,
        spawnCycleApproval(targetId, basePos, true)
    );
    CombatEvent.CrystalSpawned event = new CombatEvent.CrystalSpawned(712, basePos, 1_000L);

    ReactiveDecision decision = engine.decide(event, snapshot, 1_050L).orElseThrow();
    assertEquals(
        List.of(new AttackKnownCrystal(712), new PlaceCrystal(basePos)),
        decision.actions()
    );
    assertTrue(decision.critical());
}

@Test
void popFinisherPreemptsRecycle() {
    CombatBlackboardSnapshot snapshot = snapshotWithFinisherAndRecycle(targetId);
    ReactiveDecision decision = engine.decide(
        new CombatEvent.TotemPopped(targetId, 2_000L), snapshot, 2_010L
    ).orElseThrow();
    assertEquals(ApprovalSlot.FINISHER, decision.slot());
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```

- [ ] **Step 3: Implement fixed priority selection and base lifecycle**

Priority is exactly:
```text
LETHAL -> FINISHER -> STAIRCASE -> RECYCLE -> BREAK/PLACE/PRESSURE -> PREPARE
```

`CrystalBaseTracker` transitions:
```text
EMPTY -> PLACE_SENT -> LIVE(entityId) -> BREAK_SENT -> EMPTY
```
with `INVALID` entered on reconciliation failure/blockage. Repeated identical event/action tokens must return no decision and increment duplicate suppression diagnostics instead of dispatching again.

- [ ] **Step 4: Run reactive tests plus an architecture guard**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```
Expected: PASS; source inspection test confirms no imports/references to `BeamPlanner`, `CandidateGenerator`, `ClientCombatSnapshotBuilder`, or `TargetPredictor` from `ReactiveCombatEngine`.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive
git commit -m "feat: add event-driven crystal fast lane"
```

---

### Task 10: Dispatch ordered break->replace bursts through real vanilla interactions

**Files:**
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/execution/RotationController.java` only if a V2 critical-mode overload is required.
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2ReactiveBurstArchitectureTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/BreakReplaceOrderingTest.java`

**Interfaces:**
- Keep existing `ActionDispatcher.dispatch(CombatAction)` for V1 during migration.
- Add `VanillaInteractionDispatcher.dispatch(CombatAction, RotationMode, boolean critical)`.
- `ReactiveBurstDispatcher.dispatch(ReactiveDecision,OptimizerConfig)` sends actions sequentially in the same callback until one fails/deferred/waits.

- [ ] **Step 1: Write failing ordering/critical-rotation architecture tests**

Requirements encoded in tests:
```text
A ReactiveDecision [AttackKnownCrystal(381), PlaceCrystal(base)] calls attack before useItemOn.
No client-side entity ID is synthesized for the replacement.
Critical ADAPTIVE rotation passes critical=true to RotationController.
PlaceCrystal still calls Minecraft.gameMode.useItemOn; there is no hand-built ServerboundUseItemOnPacket.
A failed first action prevents the second action from being sent.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2ReactiveBurstArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.BreakReplaceOrderingTest
```

- [ ] **Step 3: Implement burst dispatch while retaining V1 compatibility**

```java
public DispatchReceipt dispatch(CombatAction action, RotationMode mode, boolean critical) {
    // Same real action implementations as dispatch(action), but aimAt receives mode/critical explicitly.
}

@Override
public DispatchReceipt dispatch(CombatAction action) {
    return dispatch(action, rotationMode, scheduler.phase() == CommitPhase.COMMITTED);
}
```

```java
public BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config) {
    List<DispatchReceipt> receipts = new ArrayList<>();
    for (CombatAction action : decision.actions()) {
        DispatchReceipt receipt = dispatcher.dispatch(action, config.rotationMode(), decision.critical());
        receipts.add(receipt);
        if (receipt.status() != DispatchReceipt.Status.SENT) break;
    }
    return BurstReceipt.of(receipts);
}
```

Reserve the item in `PendingItemLedger` immediately before an in-flight `PlaceCrystal`/anchor/charge dispatch and release it only on matching reconciliation or failure.

- [ ] **Step 4: Run dispatcher/rotation/hand-truthfulness tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.*' --tests dev.adrien.crystaloptimizer.execution.RotationMathTest --tests dev.adrien.crystaloptimizer.candidate.HandTruthfulnessTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/execution \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: dispatch v2 break replace bursts"
```

---

### Task 11: Add hurt-window threshold tracking and useful-damage staircase selection

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtThresholdEstimate.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTracker.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTrackerTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelectorTest.java`

**Interfaces:**
- `HurtWindowTracker.observeAttributedDamage(UUID,float,long)` and `estimate(UUID,int,long)`.
- `FastOpportunitySelector.select(List<DamageOpportunity>,SelectionContext)` ranks lethal time/useful marginal damage, not raw damage alone.

- [ ] **Step 1: Write failing staircase tests**

```java
@Test
void zeroFeedbackUsefulDamageBeatsHigherRawDelayedAction() {
    SelectionContext context = new SelectionContext(
        new HurtThresholdEstimate(18.0f, 19.0f, 20.0f, 0.8),
        10.0f,
        OptimizerStrategy.LETHAL_SPEED
    );
    DamageOpportunity immediate = opportunity("anchor", 29.0f, 0, 15.0);
    DamageOpportunity delayed = opportunity("crystal-respawn", 33.0f, 1, 120.0);

    DamageOpportunity selected = selector.select(List.of(delayed, immediate), context).orElseThrow();
    assertEquals("anchor", selected.id());
}

@Test
void equalOrWeakerProtectedHitHasNoUsefulMarginalDamage() {
    HurtThresholdEstimate threshold = new HurtThresholdEstimate(18.0f, 18.0f, 18.0f, 1.0);
    assertEquals(0.0f, FastOpportunitySelector.usefulLowerBound(17.0f, threshold), 1.0e-5f);
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*'
```

- [ ] **Step 3: Implement threshold evidence and lethal-time ranking**

Known attributed explosions produce exact/derived threshold evidence until the protected window expires. Unknown remote windows return a broad threshold estimate and mark `HURT_THRESHOLD_UNKNOWN`; they never fabricate an exact `lastHurt`.

Selector comparison order for `LETHAL_SPEED`:
```text
certain/high-confidence lethal
pop+immediate finisher value
lower-bound useful marginal damage / p90 completion milliseconds
expected useful marginal damage / expected completion milliseconds
raw expected target damage
lower self damage
fewer hard feedback boundaries
```

Face-place policy may lower `minDamage` when target effective health is at or below `facePlaceHealth`, but it may not bypass max-self-damage or suicide checks.

- [ ] **Step 4: Run staircase and legacy hurt-window tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*' --tests 'dev.adrien.crystaloptimizer.sim.damage.HurtWindow*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/strategy
git commit -m "feat: rank useful hurt window damage"
```

---

### Task 12: Build incremental target-local DamageMap and cheap strategic approvals

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMap.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageScenarioFactory.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java`
- Modify/reuse: `candidate/CandidateGenerator.java`, `candidate/CandidatePruner.java` only through bounded strategic calls; do not call `BeamPlanner` for hot approvals.
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMapTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2StrategicScannerArchitectureTest.java`

**Interfaces:**
- `ClientDamageMapBuilder.update(target, revisions, config)` returns immutable `DamageMap`.
- `ClientStrategicScanner.scan(...)` converts selected opportunities into `ActionApproval`s and atomically publishes one blackboard snapshot.

- [ ] **Step 1: Write failing incremental invalidation tests**

```java
@Test
void unrelatedBlockChangeDoesNotInvalidateAllDamageEntries() {
    DamageMap map = DamageMap.of(
        targetId,
        10L,
        Map.of(
            opportunityA.id(), opportunityA,
            opportunityB.id(), opportunityB
        )
    );
    DamageMap next = map.invalidateGeometry(Set.of(new BlockPos(100, 20, 100)));
    assertEquals(2, next.opportunities().size());
}

@Test
void targetRevisionInvalidatesPositionDependentEntries() {
    DamageMap next = map.withTargetRevision(11L);
    assertTrue(next.opportunities().values().stream().noneMatch(DamageOpportunity::positionDependent));
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.strategy.DamageMapTest --tests dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest
```

- [ ] **Step 3: Implement target-local map and scanner**

The scanner may use `ClientCombatSnapshotBuilder` and existing candidate generation on its non-reactive tick, but must cache target-local opportunities and invalidate only entries touched by relevant target/world/inventory revisions. Immediate approvals come from `FastOpportunitySelector`; `BeamPlanner` may be called only for `ApprovalSlot.PREPARE` setup exploration.

Publish at least:
```text
BREAK
PLACE
RECYCLE (SpawnCrystalCycle for approved base)
FINISHER
STAIRCASE
PRESSURE
PREPARE
```

Each approval gets an expiry measured in low hundreds of milliseconds and exact current revision keys. The scanner must never publish an attack approval for an unobserved entity ID; spawn-based cycles use `SpawnCrystalCycle` templates.

- [ ] **Step 4: Run scanner, candidate, prediction, and planner regression tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*' --tests 'dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest' --tests 'dev.adrien.crystaloptimizer.candidate.*' --tests 'dev.adrien.crystaloptimizer.prediction.*' --tests 'dev.adrien.crystaloptimizer.planner.*'
```
Expected: PASS; architecture test proves reactive code has no scanner/planner dependency.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: scan v2 immediate damage opportunities"
```

---

### Task 13: Add bounded TargetManager and ClientCombatCoordinator alongside V1

**Files:**
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2CoordinatorArchitectureTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CoordinatorReplayTest.java`

**Interfaces:**
- `ClientCombatCoordinator.tick()` performs non-reactive target/scanner/restock work.
- `ClientCombatCoordinator.onEvent(CombatEvent)` performs the fast reactive decision->arbiter->dispatch path synchronously on the client packet/event thread.
- V1 `ClientCombatRuntime` remains the active bootstrap until Task 16.

- [ ] **Step 1: Write failing coordinator replay/architecture tests**

Replay requirement:
```text
prepublish RECYCLE approval
emit CrystalSpawned(realId, base)
coordinator materializes -> arbitrates -> dispatches attack/place
scanner invocation count remains unchanged during onEvent
```

Architecture requirement:
```text
ClientCombatCoordinator.onEvent contains no ClientCombatSnapshotBuilder.build call.
ClientCombatCoordinator.onEvent contains no BeamPlanner.plan call.
ClientCombatCoordinator.tick may invoke ClientStrategicScanner.scan.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2CoordinatorArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.CoordinatorReplayTest
```

- [ ] **Step 3: Implement coordinator and target selection**

`TargetManager` keeps the current valid target sticky but reevaluates a bounded shortlist of at most three visible non-allied players using immediate `DamageOpportunity` score rather than a beam-plan prepass. A recent attacker receives a shortlist boost, but lethal-time opportunity remains the final primary score.

`ClientCombatCoordinator.onEvent` exact order:
```text
capture event timestamp
read current config + blackboard snapshot
ReactiveCombatEngine.decide
materialize actions
ActionArbiter.evaluate
record decision-complete timestamp
ReactiveBurstDispatcher.dispatch
record dispatch timestamp
update base/pending-item state
publish cached diagnostics
```

- [ ] **Step 4: Run V2 coordinator tests plus V1 runtime tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.V2*' --tests 'dev.adrien.crystaloptimizer.v2.reactive.*' --tests 'dev.adrien.crystaloptimizer.execution.CombatRuntime*'
```
Expected: PASS; V1 remains unaffected.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: coordinate v2 reactive combat"
```

---

### Task 14: Add calibrated diagnostics and cached TIME_TO_DAMAGE HUD data

**Files:**
- Modify: `.../src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageMismatch.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibration.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibrationTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ClientDiagnosticsHudArchitectureTest.java`

**Interfaces:**
- `DamageCalibration.observePrediction(actionId,LiveDamageTrace)` then `observeResult(...)` classifies mismatch without tuning the simulator.
- HUD reads one cached immutable diagnostics snapshot only.

- [ ] **Step 1: Write failing mismatch classification and HUD architecture tests**

```java
@Test
void outOfIntervalObservedDamageIsClassifiedNotFudged() {
    DamageCalibration calibration = new DamageCalibration();
    calibration.observePrediction(44L, traceWithEstimate(14.0f, 16.0f, 18.0f));
    DamageMismatch mismatch = calibration.observeResult(
        44L,
        new ObservedDamageResult(5.0f, false, false, 9L)
    ).orElseThrow();
    assertNotEquals(DamageMismatch.Kind.NONE, mismatch.kind());
    assertEquals(1.0, calibration.damageMultiplier(), 0.0);
}
```

HUD source test must assert it does not reference `Minecraft.level`, `ClientCombatSnapshotBuilder`, `BeamPlanner`, or candidate generation from render callbacks.

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageCalibrationTest --tests dev.adrien.crystaloptimizer.client.ClientDiagnosticsHudArchitectureTest
```

- [ ] **Step 3: Implement mismatch classes and cached diagnostics**

`DamageMismatch.Kind` exactly:
```text
NONE
EXPOSURE_MISMATCH
STALE_GEOMETRY
HURT_THRESHOLD_UNKNOWN
ABSORPTION_UNCERTAINTY
EFFECT_STATE_CHANGED
TARGET_MOVED
ARMOR_STATE_CHANGED
ACTION_NOT_SERVER_ACCEPTED
INTERFERENCE
UNKNOWN
```

The HUD summary should show enabled/strategy, target, reactive state, best approved action, damage lower/expected/upper, worst self damage, place->spawn p50/p90, last spawn->attack decision/dispatch latency, last mismatch kind, and last rejection reason. Detailed traces stay in diagnostics data, not every HUD line.

- [ ] **Step 4: Run diagnostics tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.damage.*' --tests 'dev.adrien.crystaloptimizer.client.*Diagnostics*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: diagnose v2 combat latency and damage"
```

---

### Task 15: Add optional Mod Menu config and developer diagnostics screens

**Files:**
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigService.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java`
- Modify: `.../src/main/resources/fabric.mod.json`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/OptimizerConfigServiceTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ModMenuIntegrationArchitectureTest.java`

**Interfaces:**
- `OptimizerConfigService.current()`, `apply(OptimizerConfig)`, `revision()`, `addListener(Consumer<OptimizerConfig>)`.
- `CrystalOptimizerModMenu implements com.terraformersmc.modmenu.api.ModMenuApi` and returns `OptimizerConfigScreen` from `getModConfigScreenFactory()`.

- [ ] **Step 1: Write failing persistence/optional-integration tests**

```java
@Test
void applyValidatesSavesAndAtomicallyPublishes() throws IOException {
    Path dir = Files.createTempDirectory("crystaloptimizer-config");
    OptimizerConfigService service = OptimizerConfigService.forDirectory(dir);
    OptimizerConfig changed = new OptimizerConfig(
        true, OptimizerStrategy.AGGRESSIVE, 10.0, 5.0f, 10.0f, 7.0f,
        true, true, false, RotationMode.INSTANT, true
    );
    service.apply(changed);
    assertEquals(changed, service.current());
    assertTrue(service.revision() > 0L);
    assertEquals(changed, OptimizerConfigService.forDirectory(dir).current());
}
```

Architecture test requirements:
```text
fabric.mod.json has a modmenu entrypoint.
fabric.mod.json does not list modmenu under depends.
Main user screen exposes exactly the spec's normal settings.
Advanced screen reads ClientCombatDiagnostics but does not mutate timing/damage internals.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.OptimizerConfigServiceTest --tests dev.adrien.crystaloptimizer.client.ModMenuIntegrationArchitectureTest
```

- [ ] **Step 3: Implement config service and vanilla-styled screens**

Persist to Fabric config directory `crystaloptimizer.json` with Gson. Write to a sibling temporary file and atomically replace where supported so a crash cannot leave a partial JSON file. Invalid/malformed files fall back to `OptimizerConfig.defaults()` and preserve the bad file by renaming it with `.invalid` suffix before writing defaults.

`OptimizerConfigScreen` layout groups:
```text
General: Enabled, Strategy, Target Range
Combat: Min Damage, Max Self Damage, Face Place HP, Crystals, Anchors, Auto Restock
Execution: Rotation
Visual: HUD, Advanced Diagnostics button
Footer: Cancel, Save
```

Use normal Minecraft `Button`, `CycleButton`, and numeric `EditBox` widgets; Save builds one validated `OptimizerConfig` and calls `OptimizerConfigService.apply` exactly once.

Mod Menu entrypoint:
```java
public final class CrystalOptimizerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new OptimizerConfigScreen(parent, OptimizerConfigService.instance());
    }
}
```

Add under Fabric `entrypoints`:
```json
"modmenu": [
  "dev.adrien.crystaloptimizer.client.integration.CrystalOptimizerModMenu"
]
```
Do not add `modmenu` to `depends`; optionally add it to `suggests` with `">=18.0.0-beta.1"`.

- [ ] **Step 4: Run config/integration tests and a full build with Mod Menu on the dev classpath**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.*Config*' --tests dev.adrien.crystaloptimizer.client.ModMenuIntegrationArchitectureTest
gradle --no-daemon build
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/build.gradle \
        projects/crystal-anchor-combat-optimizer-26-1-2/gradle.properties \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/main/resources/fabric.mod.json \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/config \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/integration \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client
git commit -m "feat: add optional mod menu controls"
```

---

### Task 16: Prove acceptance gates, switch bootstrap to V2, and remove superseded V1 orchestration

**Files:**
- Create: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveLatencyGateTest.java`
- Create: `.../src/test/java/dev/adrien/crystaloptimizer/v2/timing/TimingReplayTest.java`
- Create: `.../src/test/resources/dev/adrien/crystaloptimizer/v2/timing/low-ping.trace`
- Create: `.../src/test/resources/dev/adrien/crystaloptimizer/v2/timing/jitter.trace`
- Create: `.../src/test/resources/dev/adrien/crystaloptimizer/v2/timing/degraded-tps.trace`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Modify: `.../README.md`
- Modify: `.github/workflows/crystal-anchor-combat-optimizer-26-1-2-ci.yml` only if the new focused gate command is not already covered by `test`.
- Delete after gates pass: `.../src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java`
- Delete if no remaining production references after gates pass: `.../src/main/java/dev/adrien/crystaloptimizer/execution/CombatRuntimeEngine.java`, `CommitPolicy.java`, `CommitScheduler.java`, `PlanExecutionController.java`, `PlanExecutionDriver.java`, `RuntimeFrame.java`, `RuntimePlanner.java` and obsolete tests dedicated only to those removed V1 orchestration classes.
- Keep: `BeamPlanner` and preparation-relevant planner classes.

**Interfaces:**
- `CrystalOptimizerClient` bootstraps `OptimizerConfigService`, `ClientCombatCoordinator`, event subscription, O-key toggle, and cached V2 HUD only.
- All final acceptance gates from the spec are executable tests or explicit verification commands.

- [ ] **Step 1: Write failing latency and timing replay gates before switching bootstrap**

```java
@Test
void preapprovedReactivePathMeetsCpuLatencyGateAndBeatsV1() {
    LatencySamples v2 = benchmarkV2SpawnBreak(20_000, 2_000);
    LatencySamples v1 = benchmarkEquivalentV1Decision(20_000, 2_000);
    assertTrue(v2.p50Millis() <= 1.0, () -> "V2 p50=" + v2.p50Millis());
    assertTrue(v2.p95Millis() <= 2.0, () -> "V2 p95=" + v2.p95Millis());
    assertTrue(v1.p50Nanos() / (double)Math.max(1L, v2.p50Nanos()) >= 5.0,
        () -> "V2 did not achieve 5x median speedup");
}
```

Timing replay tests parse each trace into typed start/end observations, feed the exact same `TimingEngine`, and assert ordering properties such as p90 >= p50, stale confidence decay, more pessimistic completion under jitter/degraded cadence, and no fabricated certainty for missing transitions.

- [ ] **Step 2: Run the acceptance-focused suite while V1 is still present**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.*' --tests 'dev.adrien.crystaloptimizer.client.V2*' --stacktrace
gradle --no-daemon runGameTest --stacktrace
```
Expected: every V2 unit/architecture/differential/recycle/timing/latency gate passes before production bootstrap changes.

- [ ] **Step 3: Switch `CrystalOptimizerClient` to V2 and remove dead V1 orchestration**

Final bootstrap shape:
```java
public final class CrystalOptimizerClient implements ClientModInitializer {
    private KeyMapping toggleKey;
    private ClientCombatCoordinator coordinator;

    @Override
    public void onInitializeClient() {
        OptimizerConfigService config = OptimizerConfigService.instance();
        coordinator = ClientCombatCoordinator.create(Minecraft.getInstance(), config);
        ClientCombatEventBus.instance().subscribe(coordinator::onEvent);
        OptimizerHud.register(coordinator::diagnostics);
        toggleKey = registerToggleKey();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.consumeClick()) {
                config.apply(config.current().withEnabled(!config.current().enabled()));
            }
            coordinator.tick();
        });
    }
}
```

Add the exact `withEnabled(boolean)` copy method to `OptimizerConfig` when wiring the toggle. Remove V1 classes only after `git grep` proves there are no production references; keep mechanics, reconciliation primitives still used by V2, `InventoryCoordinator`, and `BeamPlanner` setup logic.

- [ ] **Step 4: Run the authoritative final verification from a clean state**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon clean test build runGameTest --stacktrace
```
Expected: `BUILD SUCCESSFUL`, all unit tests pass, all GameTests pass, V2 latency gates pass, and the generated runtime JAR contains `fabric.mod.json`, mixin config, V2 coordinator, reactive engine, Mod Menu entrypoint, and config screens.

Then inspect for forbidden hot-path dependencies:
```bash
git grep -n 'BeamPlanner\|ClientCombatSnapshotBuilder\|CandidateGenerator' -- \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java
```
Expected: no matches from `ReactiveCombatEngine` or `ClientCombatCoordinator.onEvent`; strategic scanner matches are allowed only in its own file.

- [ ] **Step 5: Update README and commit the V2 cutover**

README must document:
```text
Minecraft 26.1.2
Java 25
Fabric Loader >=0.19.3
Fabric API 0.155.2+26.1.2
Version 0.2.0
O toggles the optimizer
Mod Menu is optional and opens the full config when installed
Default strategy is Lethal Speed
Reactive crystal recycling waits for real server entity IDs
No silent rotations/fake packets/hidden-inventory assumptions
Damage diagnostics show uncertainty instead of a fake exact number
```

Commit:
```bash
git add .github/workflows/crystal-anchor-combat-optimizer-26-1-2-ci.yml \
        projects/crystal-anchor-combat-optimizer-26-1-2
git commit -m "feat: switch crystal optimizer to v2"
```

---

## Final Review Checklist Before Opening/Merging the V2 PR

- [ ] `OptimizerConfig.defaults()` is `LETHAL_SPEED` and Mod Menu remains optional.
- [ ] The O key toggles the same config snapshot used by Mod Menu.
- [ ] `ReactiveCombatEngine` has no planner/world-scan dependency.
- [ ] New crystal IDs come only from real spawn observations.
- [ ] Break->replace can send both legal ordered interactions without waiting for local crystal removal, while the next break waits for the real replacement spawn ID.
- [ ] Pending predicted crystal consumption cannot double-spend a locally unchanged stack.
- [ ] Pop->finisher preempts recycle when its approval has better lethal priority.
- [ ] Hurt-window selector scores useful marginal damage and lethal time, not nominal CPS/raw damage only.
- [ ] Typed timing distributions are used for place->spawn, attack->removal, block ack, refill observation, and cadence.
- [ ] Exact-observable damage matches vanilla GameTests; uncertain cases return honest intervals.
- [ ] Damage calibration classifies mismatches and never modifies damage by an opaque multiplier.
- [ ] HUD/config rendering performs no world scan/planner work.
- [ ] V2 reactive p50 <= 1 ms, p95 <= 2 ms, and median >=5x faster than equivalent V1 replay on the same harness.
- [ ] Full `gradle --no-daemon clean test build runGameTest --stacktrace` succeeds under Java 25.
- [ ] The kept branch `work/crystal-anchor-combat-optimizer-26-1-2` still exists.
