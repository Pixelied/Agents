# Crystal Anchor Combat Optimizer V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the planner-centric V1 runtime with an event-driven V2 Crystal/Anchor combat engine that prioritizes absolute lethal speed, honest damage prediction, typed server timing, same-base crystal recycling, and optional Mod Menu configuration while preserving legitimate vanilla 26.1.2 mechanics.

**Architecture:** Keep the verified simulation/legality core, but move hot combat decisions into a small reactive lane fed by immutable approvals in `CombatBlackboard`. A cheap strategic scanner continuously refreshes target-local damage opportunities; `ReactiveCombatEngine` materializes already-approved actions on crystal spawn/removal/totem events, `ActionArbiter` performs only cheap current-state validation, and the existing vanilla dispatcher performs real client interactions. V1 remains present until V2 passes damage differential, latency, recycle, legality, and full GameTest gates.

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

### Main-source contracts and pure logic

- `config/OptimizerStrategy.java` — `LETHAL_SPEED`, `AGGRESSIVE`, `SAFE`.
- `config/OptimizerConfig.java` — validated immutable user config.
- `v2/damage/DamageUncertainty.java` — explicit provenance enum.
- `v2/damage/DamageEstimate.java` — lower/expected/upper estimate contract.
- `v2/damage/DamageScenario.java` — weighted exact simulator scenario.
- `v2/damage/LiveDamageTrace.java` — diagnostic calculation trace.
- `v2/damage/DamageEngine.java` — scenario aggregation over existing exact simulator.
- `v2/timing/TimingTransition.java` — typed event transitions.
- `v2/timing/TimingCorrelation.java` — collision-safe action/event correlation.
- `v2/timing/TimingDistribution.java` — p50/p90/dispersion/confidence.
- `v2/timing/SequenceTiming.java` — sequence completion estimate contract.
- `v2/timing/TimingEngine.java` — typed rolling timing model.
- `v2/reactive/CombatEvent.java` — sealed immutable event contract.
- `v2/state/ApprovalSlot.java` — named reactive approval slots.
- `v2/state/ReactiveActionSpec.java` — sealed materialization contract.
- `v2/state/FixedActionSequence.java` — concrete pre-approved action burst.
- `v2/state/SpawnCrystalCycle.java` — turns a real spawn event into attack/recycle actions.
- `v2/state/ActionApproval.java` — revisioned short-lived approval.
- `v2/state/CombatBlackboardSnapshot.java` — immutable target/revision/approval snapshot.
- `v2/state/CombatBlackboard.java` — atomic publication.
- `v2/execution/PendingItemLedger.java` — in-flight predicted item reservations.
- `v2/execution/LiveCombatView.java` — minimal cheap legality/resource view.
- `v2/execution/ArbitrationResult.java` — typed arbiter result.
- `v2/execution/ActionArbiter.java` — final linear legality/revision/resource gate.
- `v2/reactive/CrystalBasePhase.java` — recycle lifecycle.
- `v2/reactive/CrystalBaseTracker.java` — base lifecycle + duplicate suppression.
- `v2/reactive/ReactiveDecision.java` — materialized action burst.
- `v2/reactive/ReactiveCombatEngine.java` — event-driven approval selection.
- `v2/strategy/HurtThresholdEstimate.java` — progressive-damage threshold envelope.
- `v2/strategy/HurtWindowTracker.java` — attributed threshold history.
- `v2/strategy/DamageOpportunity.java` — target/self/timing evidence.
- `v2/strategy/DamageMap.java` — bounded target-local opportunity cache.
- `v2/strategy/FastOpportunitySelector.java` — max-useful-damage/lethal-time selector.
- `v2/diagnostics/TimeToDamageTrace.java` — event/decision/dispatch/result timestamps.
- `v2/damage/DamageMismatch.java` and `DamageCalibration.java` — mismatch diagnosis only.

### Client-source adapters and UI

- `client/config/OptimizerConfigService.java` — persistence + atomic config publication.
- `client/config/OptimizerConfigScreen.java` — compact Mod Menu/main screen.
- `client/config/OptimizerDiagnosticsScreen.java` — read-only developer diagnostics.
- `client/integration/CrystalOptimizerModMenu.java` — optional `ModMenuApi` entrypoint.
- `client/v2/ClientCombatEventBus.java` — synchronous event fan-out.
- `client/v2/ClientTimingObserver.java` — typed timing correlation adapter.
- `client/v2/ClientLiveCombatView.java` — cheap current legality/resource/revision view.
- `client/v2/ClientDamageScenarioFactory.java` — position/state uncertainty scenarios.
- `client/v2/ClientDamageMapBuilder.java` — incremental target-local damage cache.
- `client/v2/TargetManager.java` — bounded sticky target choice.
- `client/v2/ClientStrategicScanner.java` — refreshes blackboard approvals outside hot path.
- `client/v2/ReactiveBurstDispatcher.java` — same-callback sequential vanilla dispatch.
- `client/v2/ClientCombatDiagnostics.java` — cached V2 diagnostics.
- `client/v2/ClientCombatCoordinator.java` — tick/event orchestration.

### Existing code deliberately reused

- `sim/damage/*`, `sim/model/*`, `world/*`, `action/*`, `prediction/*`.
- `execution/InventoryCoordinator.java` for future AutoTotem/offhand ownership boundaries.
- `client/execution/RotationController.java` and `VanillaInteractionDispatcher.java`.
- `client/intel/ClientObservationBus.java`.
- `client/mixin/ClientPacketListenerMixin.java` and `ClientCommonPacketListenerImplMixin.java`.
- `planner/BeamPlanner.java` only for preparation/comparison, never reactive execution.

---

### Task 1: Establish V2 config contracts and 0.2.0 build metadata

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerStrategy.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerConfig.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/gradle.properties`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/build.gradle`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/config/OptimizerConfigTest.java`

**Interfaces:**
- Produces: `OptimizerConfig.defaults()`, `validated()`, `withEnabled(boolean)` and `OptimizerStrategy`.
- Reuses: existing `dev.adrien.crystaloptimizer.execution.RotationMode`.

- [ ] **Step 1: Write the failing config test**

```java
package dev.adrien.crystaloptimizer.config;

import static org.junit.jupiter.api.Assertions.*;

import dev.adrien.crystaloptimizer.execution.RotationMode;
import org.junit.jupiter.api.Test;

final class OptimizerConfigTest {
    @Test
    void defaultsExpressLethalSpeedPolicy() {
        OptimizerConfig config = OptimizerConfig.defaults();
        assertEquals(OptimizerStrategy.LETHAL_SPEED, config.strategy());
        assertEquals(RotationMode.ADAPTIVE, config.rotationMode());
        assertTrue(config.crystals());
        assertTrue(config.anchors());
        assertTrue(config.autoRestock());
        assertFalse(config.enabled());
    }

    @Test
    void enabledCopyChangesOnlyEnabledFlag() {
        OptimizerConfig base = OptimizerConfig.defaults();
        assertEquals(true, base.withEnabled(true).enabled());
        assertEquals(base.strategy(), base.withEnabled(true).strategy());
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

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.config.OptimizerConfigTest
```
Expected: compilation failure because the config types do not exist.

- [ ] **Step 3: Add immutable config and build metadata**

```java
public enum OptimizerStrategy {
    LETHAL_SPEED,
    AGGRESSIVE,
    SAFE
}
```

```java
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

    public OptimizerConfig withEnabled(boolean next) {
        return new OptimizerConfig(
            next, strategy, targetRange, minDamage, maxSelfDamage, facePlaceHealth,
            crystals, anchors, autoRestock, rotationMode, hud
        );
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " must be in [" + min + ", " + max + "]");
        }
    }
}
```

Set `mod_version=0.2.0`, add `modmenu_version=18.0.0-beta.1`, add Terraformers Maven, and add `implementation "com.terraformersmc:modmenu:${project.modmenu_version}"`. Do not add a hard Mod Menu metadata dependency.

- [ ] **Step 4: Run config and baseline unit tests**

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

### Task 2: Define final damage/timing/event contracts and the CombatBlackboard

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageUncertainty.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEstimate.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/SequenceTiming.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEvent.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ApprovalSlot.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ReactiveActionSpec.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/FixedActionSequence.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/SpawnCrystalCycle.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardSnapshot.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboard.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardTest.java`

**Interfaces:**
- These are final cross-task signatures; later tasks implement producers/consumers without changing them.
- `SpawnCrystalCycle` may materialize only a real `CombatEvent.CrystalSpawned` matching its approved base.

- [ ] **Step 1: Write the failing contract/blackboard test**

```java
@Test
void spawnTemplateUsesOnlyRealObservedEntityIdAndSnapshotIsImmutable() {
    UUID target = UUID.randomUUID();
    BlockPos base = new BlockPos(4, 63, 7);
    DamageEstimate damage = DamageEstimate.exact(18.0f, 3L, 5L);
    SequenceTiming timing = SequenceTiming.immediate();
    ActionApproval approval = new ActionApproval(
        77L, target, ApprovalSlot.RECYCLE, new SpawnCrystalCycle(base, true),
        damage, 6.0f, timing, 3L, 9L, 11L, 13L, 5_000L
    );
    CombatBlackboard board = new CombatBlackboard();
    board.publish(new CombatBlackboardSnapshot(
        target, 9L, 3L, 11L, 13L, Map.of(ApprovalSlot.RECYCLE, approval)
    ));

    List<CombatAction> actions = approval.actionSpec().materialize(
        new CombatEvent.CrystalSpawned(412, base, 1_000L)
    );
    assertEquals(new AttackKnownCrystal(412), actions.get(0));
    assertEquals(new PlaceCrystal(base), actions.get(1));
    assertThrows(UnsupportedOperationException.class,
        () -> board.snapshot().approvals().clear());
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.state.CombatBlackboardTest
```

- [ ] **Step 3: Implement the final shared contracts**

```java
public enum DamageUncertainty {
    PREDICTED_POSITION,
    HURT_THRESHOLD_UNKNOWN,
    ABSORPTION_UNKNOWN,
    TERRAIN_UNOBSERVED,
    ARMOR_STATE_STALE,
    EFFECT_STATE_STALE,
    PENDING_SERVER_ACCEPTANCE
}
```

```java
public record DamageEstimate(
    float lowerBound,
    float expected,
    float upperBound,
    double confidence,
    Set<DamageUncertainty> uncertainties,
    long geometryRevision,
    long combatRevision
) {
    public DamageEstimate {
        uncertainties = Set.copyOf(uncertainties);
        if (lowerBound < 0.0f || lowerBound > expected || expected > upperBound) {
            throw new IllegalArgumentException("damage bounds must be ordered and non-negative");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0,1]");
        }
    }

    public static DamageEstimate exact(float damage, long geometryRevision, long combatRevision) {
        return new DamageEstimate(
            damage, damage, damage, 1.0, Set.of(), geometryRevision, combatRevision
        );
    }

    public boolean exact() {
        return uncertainties.isEmpty()
            && Float.compare(lowerBound, expected) == 0
            && Float.compare(expected, upperBound) == 0;
    }
}
```

```java
public record SequenceTiming(
    double expectedMillis,
    double p90Millis,
    int hardFeedbackBoundaries,
    double confidence
) {
    public static SequenceTiming immediate() {
        return new SequenceTiming(0.0, 0.0, 0, 1.0);
    }

    public static SequenceTiming unknown(int hardFeedbackBoundaries) {
        return new SequenceTiming(
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            hardFeedbackBoundaries,
            0.0
        );
    }
}
```

```java
public sealed interface CombatEvent {
    long timestampNanos();
    record CrystalSpawned(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {}
    record CrystalRemoved(int entityId, BlockPos basePos, long timestampNanos) implements CombatEvent {}
    record TotemPopped(UUID targetId, long timestampNanos) implements CombatEvent {}
    record EquipmentChanged(UUID targetId, long timestampNanos) implements CombatEvent {}
    record BlockAcked(int sequence, long timestampNanos) implements CombatEvent {}
    record BlockChanged(BlockPos pos, long timestampNanos) implements CombatEvent {}
    record InventoryChanged(long inventoryRevision, long timestampNanos) implements CombatEvent {}
    record TargetMoved(UUID targetId, long targetRevision, long timestampNanos) implements CombatEvent {}
    record ConfigChanged(long configRevision, long timestampNanos) implements CombatEvent {}
}
```

`ActionApproval` final signature:
```java
public record ActionApproval(
    long approvalId,
    UUID targetId,
    ApprovalSlot slot,
    ReactiveActionSpec actionSpec,
    DamageEstimate targetDamage,
    float worstCaseSelfDamage,
    SequenceTiming timing,
    long worldRevision,
    long targetRevision,
    long inventoryRevision,
    long configRevision,
    long expiresAtNanos
) {
    public boolean isCurrent(
        long currentWorldRevision,
        long currentTargetRevision,
        long currentInventoryRevision,
        long currentConfigRevision,
        long nowNanos
    ) {
        return worldRevision == currentWorldRevision
            && targetRevision == currentTargetRevision
            && inventoryRevision == currentInventoryRevision
            && configRevision == currentConfigRevision
            && nowNanos <= expiresAtNanos;
    }
}
```

`CombatBlackboardSnapshot` defensively copies the approval map; `CombatBlackboard` owns an `AtomicReference<CombatBlackboardSnapshot>` and publishes whole snapshots only.

- [ ] **Step 4: Run contract tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.state.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/state
git commit -m "feat: define v2 combat contracts"
```

---

### Task 3: Implement DamageEngine scenario aggregation without fake precision

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageScenario.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/LiveDamageTrace.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngineTest.java`
- Reuse unchanged: `ExplosionDamageCalculator26`, `VanillaDamageSimulator`, `DamageTrace`, `CombatState`, `ExplosionContext`.

**Interfaces:**
- `DamageScenario(SimCombatant victim,Vec3 position,AABB box,double probabilityWeight,double confidence,Set<DamageUncertainty> uncertainties)`.
- `DamageEngine.estimate(ExplosionContext,CombatState,List<DamageScenario>,long geometryRevision,long combatRevision)` returns the Task 2 `DamageEstimate`.

- [ ] **Step 1: Write failing exact/uncertain aggregation tests**

```java
@Test
void exactSingleScenarioCollapsesInterval() {
    DamageEstimate estimate = engine.estimate(
        explosion,
        state,
        List.of(new DamageScenario(target, targetPos, targetBox, 1.0, 1.0, Set.of())),
        7L,
        11L
    );
    assertTrue(estimate.exact());
}

@Test
void uncertainScenariosBoundEverySimulatedOutcome() {
    DamageEstimate estimate = engine.estimate(
        explosion,
        state,
        List.of(
            new DamageScenario(targetA, posA, boxA, 0.7, 0.8,
                Set.of(DamageUncertainty.PREDICTED_POSITION)),
            new DamageScenario(targetB, posB, boxB, 0.3, 0.4,
                Set.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN))
        ),
        8L,
        12L
    );
    assertTrue(estimate.lowerBound() <= estimate.expected());
    assertTrue(estimate.expected() <= estimate.upperBound());
    assertTrue(estimate.confidence() < 1.0);
    assertFalse(estimate.exact());
}
```

- [ ] **Step 2: Run focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageEngineTest
```

- [ ] **Step 3: Aggregate exact simulator outcomes**

For each scenario, run existing `ExplosionDamageCalculator26.incoming(...)` and `VanillaDamageSimulator.apply(...)`. Compute:

```text
lowerBound = minimum scenario healthDamage
expected = probability-weighted scenario healthDamage / total probability weight
upperBound = maximum scenario healthDamage
confidence = probability-weighted scenario confidence / total probability weight
uncertainties = union of scenario reasons
```

Reject empty scenarios, non-positive/NaN probability weights, and confidence outside `[0,1]`. Never alter the underlying damage result by a learned multiplier or offset.

- [ ] **Step 4: Run V2 damage + existing simulator tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.damage.*' --tests 'dev.adrien.crystaloptimizer.sim.damage.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/damage
git commit -m "feat: add v2 damage intervals"
```

---

### Task 4: Add vanilla differential damage/exposure GameTests

**Files:**
- Create: `.../src/gametest/java/dev/adrien/crystaloptimizer/gametest/ServerLevelBlockView.java`
- Create: `.../src/gametest/java/dev/adrien/crystaloptimizer/gametest/GameTestCombatants.java`
- Create: `.../src/gametest/java/dev/adrien/crystaloptimizer/gametest/ExplosionDifferentialGameTests.java`
- Test: Fabric GameTest runtime.

**Interfaces:**
- `ServerLevelBlockView implements BlockView` by reading `ServerLevel.getBlockState` and collision shapes.
- Differential tests use vanilla `GameTestHelper.makeMockServerPlayerInLevel()` and the 26.1.2 `Level.explode(Entity,double,double,double,float,Level.ExplosionInteraction)` overload as oracle.

- [ ] **Step 1: Write the first failing exposed-target GameTest**

```java
@GameTest
public void exposedCrystalDamageMatchesVanilla(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    ServerPlayer target = helper.makeMockServerPlayerInLevel();
    target.setGameMode(GameType.SURVIVAL);
    target.setHealth(target.getMaxHealth());
    Vec3 center = target.position().add(2.5, 0.0, 0.0);
    float before = target.getHealth();

    float raw = ExplosionDamageCalculator26.incoming(
        ExplosionContext.crystal(center),
        target.getBoundingBox(),
        target.position(),
        new ServerLevelBlockView(level)
    );
    SimCombatant initial = GameTestCombatants.exactFirstHit(target);
    DamageResult predicted = VanillaDamageSimulator.apply(
        initial,
        DamageRequest.explosion(raw)
            .withDifficulty(level.getDifficulty())
            .withSourcePosition(center)
    );

    level.explode(null, center.x, center.y, center.z, 6.0f, Level.ExplosionInteraction.NONE);
    float observedLoss = before - target.getHealth();
    helper.assertTrue(
        Math.abs(observedLoss - predicted.trace().healthDamage()) <= 1.0e-4f,
        "vanilla and simulator crystal damage diverged"
    );
    helper.succeed();
}
```

- [ ] **Step 2: Run GameTests and confirm RED before fixture completion**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon runGameTest --stacktrace
```

- [ ] **Step 3: Complete the vanilla-oracle matrix**

Implement:
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

For protected-window tests, make the first real vanilla explosion and the first simulator application from the same known initial state, then feed the simulator's resulting known threshold into the second calculation. This tests exact sequence math without pretending a remote observer knows hidden `lastHurt`.

- [ ] **Step 4: Run the full GameTest matrix twice**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon runGameTest --stacktrace
gradle --no-daemon runGameTest --stacktrace
```
Expected: PASS twice.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/gametest
git commit -m "test: differential-check explosion damage"
```

---

### Task 5: Implement typed TimingEngine V2

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingTransition.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingCorrelation.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingDistribution.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngineTest.java`
- Keep temporarily for comparison: existing `timing/ServerTimingModel.java`.

**Interfaces:**
- Uses final `SequenceTiming` from Task 2.
- `recordStart(TimingCorrelation,long)`, `recordEnd(TimingCorrelation,long)`, `distribution(TimingTransition,long)`, `estimateSequence(List<TimingTransition>,long)`.

- [ ] **Step 1: Write failing percentile/freshness/sequence tests**

```java
@Test
void placeToSpawnHasIndependentDistributionAndHardBoundary() {
    TimingEngine engine = new TimingEngine(64, 5_000_000_000L);
    long base = 1_000_000_000L;
    for (int i = 0; i < 10; i++) {
        TimingCorrelation key = TimingCorrelation.block(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            new BlockPos(i, 64, 0)
        );
        long sent = base + i * 100_000_000L;
        engine.recordStart(key, sent);
        engine.recordEnd(key, sent + (20L + i) * 1_000_000L);
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

- [ ] **Step 3: Implement typed correlations and distributions**

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

```java
public record TimingCorrelation(TimingTransition transition, long high, long low) {
    public static TimingCorrelation sequence(TimingTransition t, int sequence) {
        return new TimingCorrelation(t, 0L, Integer.toUnsignedLong(sequence));
    }
    public static TimingCorrelation entity(TimingTransition t, int entityId) {
        return new TimingCorrelation(t, 1L, Integer.toUnsignedLong(entityId));
    }
    public static TimingCorrelation block(TimingTransition t, BlockPos pos) {
        return new TimingCorrelation(t, 2L, pos.asLong());
    }
    public static TimingCorrelation player(TimingTransition t, UUID id) {
        return new TimingCorrelation(t, id.getMostSignificantBits(), id.getLeastSignificantBits());
    }
}
```

Each transition owns a bounded completed-sample deque. Compute p50/p90 from sorted durations and median absolute deviation as dispersion. Confidence combines sample count and freshness only; it does not alter measured milliseconds. If a required hard-feedback transition has no usable distribution, `estimateSequence` returns `SequenceTiming.unknown(boundaryCount)`.

- [ ] **Step 4: Run V2 and legacy timing tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.timing.*' --tests 'dev.adrien.crystaloptimizer.timing.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/timing
git commit -m "feat: add typed server timing engine"
```

---

### Task 6: Wire exact 26.1.2 packet/world events into V2 observation

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/intel/ClientObservationBus.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2PacketObservationArchitectureTest.java`

**Interfaces:**
- `ClientCombatEventBus.subscribe(Consumer<CombatEvent>)` and synchronous `publish(CombatEvent)`.
- `ClientTimingObserver` owns one `TimingEngine` and translates real sequence/entity/base/player correlations.

- [ ] **Step 1: Write the failing architecture test**

Assert the source contains these exact 26.1.2 hooks:

```text
handleAddEntity @ TAIL
handleRemoveEntities @ HEAD
handleBlockUpdate @ TAIL
handleChunkBlocksUpdate @ TAIL
handleBlockChangedAck @ TAIL
handleEntityEvent @ TAIL
```

and does not reference `BeamPlanner`, `CandidateGenerator`, `TargetPredictor`, or `ClientCombatSnapshotBuilder` inside mixin/event-bus code.

- [ ] **Step 2: Run the architecture test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2PacketObservationArchitectureTest
```

- [ ] **Step 3: Implement synchronous observation**

At `handleAddEntity` TAIL, resolve the newly created entity from `Minecraft.level`; if it is an `EndCrystal`, derive its base as `BlockPos.containing(crystal.getX(), crystal.getY() - 1.0, crystal.getZ())`, complete a matching `CRYSTAL_PLACE_TO_SPAWN` timing correlation, and publish `CrystalSpawned(realId,base,now)`.

At `handleRemoveEntities` HEAD, inspect each still-present entity before vanilla removal; publish `CrystalRemoved` for real crystals and complete `CRYSTAL_ATTACK_TO_REMOVAL` correlations.

At `handleBlockUpdate` TAIL, publish `BlockChanged(packet.getPos(),now)`. At `handleChunkBlocksUpdate` TAIL, call `packet.runUpdates((pos,state) -> publish BlockChanged(pos.immutable(),now))`.

Keep existing opponent equipment/pickup/totem evidence. When `EntityEvent.PROTECTED_FROM_DEATH` targets a non-local player, also publish `TotemPopped` and start `TOTEM_POP_TO_VISIBLE_REFILL` correlation for that UUID. A later visible totem equipment update completes it.

Keep outgoing `ServerboundUseItemOnPacket.getSequence()` observation and route it to `BLOCK_INTERACTION_TO_ACK`; `handleBlockChangedAck` completes the same sequence correlation.

- [ ] **Step 4: Run packet/intel observation tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2PacketObservationArchitectureTest --tests 'dev.adrien.crystaloptimizer.intel.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client
git commit -m "feat: observe v2 combat events"
```

---

### Task 7: Add in-flight item reservations and the lightweight live combat view

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedgerTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ClientLiveCombatViewArchitectureTest.java`

**Interfaces:**
- `PendingItemLedger.reserve(long,Item,int,int observedCount)`, `release(long)`, `reserved(Item)`, `available(Item,int)`.
- `LiveCombatView` exposes current world/target/inventory/config revisions, target/entity/reach checks, post-break crystal-base legality, and observed item counts without building a full snapshot.

- [ ] **Step 1: Write the failing reservation test**

```java
@Test
void inFlightPredictionCannotDoubleSpendVisibleCrystal() {
    PendingItemLedger ledger = new PendingItemLedger();
    ledger.reserve(100L, Items.END_CRYSTAL, 1, 1);
    assertEquals(0, ledger.available(Items.END_CRYSTAL, 1));
    assertThrows(IllegalStateException.class,
        () -> ledger.reserve(101L, Items.END_CRYSTAL, 1, 1));
    ledger.release(100L);
    assertEquals(1, ledger.available(Items.END_CRYSTAL, 1));
}
```

- [ ] **Step 2: Run focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.PendingItemLedgerTest
```

- [ ] **Step 3: Implement ledger and final live-view interface**

```java
public interface LiveCombatView {
    long worldRevision();
    long targetRevision(UUID targetId);
    long inventoryRevision();
    long configRevision();
    boolean targetValid(UUID targetId);
    boolean liveCrystal(int entityId);
    boolean withinEntityReach(int entityId);
    boolean withinBlockReach(BlockPos pos);
    boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId);
    int observedCount(Item item);
    int selectedHotbarSlot();
}
```

`ClientLiveCombatView` takes revision suppliers from the coordinator/config service and reads only current `Minecraft.player`, `Minecraft.level`, entity/block state and inventory. The architecture test must fail if this class imports or names `BeamPlanner`, `CandidateGenerator`, or `ClientCombatSnapshotBuilder`.

- [ ] **Step 4: Run reservation/live-view tests**

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
git commit -m "feat: reserve predicted combat items"
```

---

### Task 8: Implement ActionArbiter as a linear final gate

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiterTest.java`

**Interfaces:**
- `evaluate(ActionApproval,List<CombatAction>,LiveCombatView,PendingItemLedger,OptimizerConfig,long nowNanos)`.
- No candidate generation, target prediction, damage simulation, or beam search.

- [ ] **Step 1: Write stale/illegal/ordered-transition tests**

```java
@Test
void allowsBreakThenSameBasePlaceAgainstPredictedPostBreakState() {
    ActionApproval approval = Fixtures.recycleApproval(targetId, crystalId, basePos);
    LiveCombatView view = Fixtures.liveView()
        .withRevisions(approval.worldRevision(), approval.targetRevision(),
            approval.inventoryRevision(), approval.configRevision())
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
```

Also cover stale world/target/inventory/config revision, expired approval, removed crystal, out-of-reach block/entity, excessive worst-case self damage, and unavailable/reserved item.

- [ ] **Step 2: Run focused test and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.ActionArbiterTest
```

- [ ] **Step 3: Implement the exact gate order**

```text
approval expiry + all four revisions
target valid
worst-case self damage policy
entity/block reach and liveness
special AttackKnownCrystal -> PlaceCrystal predicted post-break legality
actual/reserved item availability
allow materialized action list unchanged
```

Use `view.configRevision()` when calling `approval.isCurrent(...)`; do not substitute the approval's own revision as the current value.

- [ ] **Step 4: Run arbiter and existing legality tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.ActionArbiterTest --tests dev.adrien.crystaloptimizer.action.ActionLegalityTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/execution
git commit -m "feat: gate v2 reactive actions"
```

---

### Task 9: Implement the event-driven ReactiveCombatEngine and recycle lifecycle

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBasePhase.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTracker.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveDecision.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngine.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngineTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTrackerTest.java`

**Interfaces:**
- `decide(CombatEvent,CombatBlackboardSnapshot,long)` returns `Optional<ReactiveDecision>`.
- `ReactiveDecision(long approvalId,ApprovalSlot slot,List<CombatAction> actions,long eventNanos,long decisionNanos,boolean critical)`.

- [ ] **Step 1: Write priority/materialization/duplicate tests**

```java
@Test
void approvedSpawnCycleUsesRealSpawnedId() {
    CombatBlackboardSnapshot snapshot = Fixtures.snapshotWith(
        ApprovalSlot.RECYCLE,
        Fixtures.spawnCycleApproval(targetId, basePos, true)
    );
    ReactiveDecision decision = engine.decide(
        new CombatEvent.CrystalSpawned(712, basePos, 1_000L),
        snapshot,
        1_050L
    ).orElseThrow();
    assertEquals(List.of(
        new AttackKnownCrystal(712),
        new PlaceCrystal(basePos)
    ), decision.actions());
}

@Test
void popFinisherPreemptsRecycle() {
    ReactiveDecision decision = engine.decide(
        new CombatEvent.TotemPopped(targetId, 2_000L),
        Fixtures.snapshotWithFinisherAndRecycle(targetId),
        2_010L
    ).orElseThrow();
    assertEquals(ApprovalSlot.FINISHER, decision.slot());
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```

- [ ] **Step 3: Implement fixed reactive priority and lifecycle**

Priority is exactly:
```text
LETHAL -> FINISHER -> STAIRCASE -> RECYCLE -> BREAK -> PLACE -> PRESSURE -> PREPARE
```

Base phases:
```text
EMPTY -> PLACE_SENT -> LIVE(entityId) -> BREAK_SENT -> EMPTY
```
with `INVALID` on rejection/interference/stale reconciliation. Duplicate keys combine event identity + approval ID; an already-consumed key yields no decision.

- [ ] **Step 4: Run reactive tests and source architecture guard**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```
Expected: PASS; architecture assertion finds no `BeamPlanner`, `CandidateGenerator`, `TargetPredictor`, or client snapshot builder reference in `ReactiveCombatEngine`.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive
git commit -m "feat: add event-driven crystal fast lane"
```

---

### Task 10: Dispatch ordered break->replace bursts through vanilla APIs

**Files:**
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/BurstReceipt.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2ReactiveBurstArchitectureTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/BreakReplaceOrderingTest.java`

**Interfaces:**
- Keep V1 `dispatch(CombatAction)` during migration.
- Add `dispatch(CombatAction,RotationMode,boolean critical)`.
- `ReactiveBurstDispatcher.dispatch(ReactiveDecision,OptimizerConfig)` sends in list order and stops on non-`SENT`.

- [ ] **Step 1: Write ordering/vanilla-path tests**

Encode these requirements:
```text
AttackKnownCrystal is dispatched before same-base PlaceCrystal.
Replacement uses Minecraft.gameMode.useItemOn, not a hand-built interaction packet.
No replacement entity ID is created client-side.
Critical ADAPTIVE passes critical=true to real RotationController.
Failure/defer/wait on action N prevents action N+1.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2ReactiveBurstArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.BreakReplaceOrderingTest
```

- [ ] **Step 3: Add V2 overload and burst loop**

```java
public DispatchReceipt dispatch(CombatAction action, RotationMode mode, boolean critical) {
    // Reuse the existing real Minecraft attack/useItemOn/slot/swing implementations.
    // Rotation calls receive mode and critical directly instead of reading V1 commit phase.
}
```

```java
public BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config) {
    List<DispatchReceipt> receipts = new ArrayList<>();
    for (CombatAction action : decision.actions()) {
        DispatchReceipt receipt = dispatcher.dispatch(
            action, config.rotationMode(), decision.critical()
        );
        receipts.add(receipt);
        if (receipt.status() != DispatchReceipt.Status.SENT) break;
    }
    return new BurstReceipt(List.copyOf(receipts));
}
```

Before a `PlaceCrystal`, `PlaceAnchor`, or `ChargeAnchor` send, reserve exactly one required item against observed count. Release on matching success observation, explicit dispatch failure, timeout/reconciliation failure, or inventory revision proving consumption.

- [ ] **Step 4: Run dispatcher, rotation, and hand-truthfulness tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.*' --tests dev.adrien.crystaloptimizer.execution.RotationMathTest --tests dev.adrien.crystaloptimizer.candidate.HandTruthfulnessTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: dispatch v2 break replace bursts"
```

---

### Task 11: Track hurt thresholds and rank useful damage per lethal time

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtThresholdEstimate.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTracker.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTrackerTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelectorTest.java`

**Interfaces:**
- `observeAttributedIncoming(UUID,float,int invulnerableTime,long)` and `estimate(UUID,int invulnerableTime,long)`.
- `select(List<DamageOpportunity>,SelectionContext)` returns the best immediate action.

- [ ] **Step 1: Write the failing staircase tests**

```java
@Test
void zeroFeedbackUsefulDamageBeatsHigherRawDelayedAction() {
    SelectionContext context = new SelectionContext(
        new HurtThresholdEstimate(18.0f, 19.0f, 20.0f, 0.8),
        10.0f,
        OptimizerStrategy.LETHAL_SPEED
    );
    DamageOpportunity immediate = Fixtures.opportunity("anchor", 29.0f, 0, 15.0);
    DamageOpportunity delayed = Fixtures.opportunity("respawned-crystal", 33.0f, 1, 120.0);
    assertEquals("anchor",
        selector.select(List.of(delayed, immediate), context).orElseThrow().id());
}

@Test
void equalOrWeakerProtectedHitHasZeroUsefulLowerBound() {
    HurtThresholdEstimate threshold = new HurtThresholdEstimate(18.0f, 18.0f, 18.0f, 1.0);
    assertEquals(0.0f,
        FastOpportunitySelector.usefulLowerBound(17.0f, threshold), 1.0e-5f);
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*'
```

- [ ] **Step 3: Implement evidence-backed threshold tracking and selector order**

Known attributed incoming damage can produce a derived threshold until the protected window expires. If attribution is not reliable, return an unknown/broad estimate and mark downstream damage `HURT_THRESHOLD_UNKNOWN`; never fabricate remote `lastHurt`.

`LETHAL_SPEED` comparison order:
```text
high-confidence lethal
pop + immediate finisher value
lower-bound useful marginal damage / p90 completion time
expected useful marginal damage / expected completion time
raw expected target damage
lower worst-case self damage
fewer hard feedback boundaries
```

Face-place may relax `minDamage` when target effective health <= `facePlaceHealth`; it cannot bypass max-self-damage/suicide policy.

- [ ] **Step 4: Run staircase and existing hurt-window tests**

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

### Task 12: Build the incremental target-local DamageMap and strategic approvals

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMap.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageScenarioFactory.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMapTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2StrategicScannerArchitectureTest.java`

**Interfaces:**
- `ClientDamageMapBuilder.update(AbstractClientPlayer target,long worldRevision,long targetRevision,OptimizerConfig)`.
- `ClientStrategicScanner.scan(...)` publishes one immutable `CombatBlackboardSnapshot`.

- [ ] **Step 1: Write failing incremental invalidation tests**

```java
@Test
void unrelatedBlockChangePreservesIndependentEntries() {
    DamageMap map = Fixtures.damageMapWithIndependentEntries();
    DamageMap next = map.invalidateGeometry(Set.of(new BlockPos(100, 20, 100)));
    assertEquals(map.opportunities().size(), next.opportunities().size());
}

@Test
void targetRevisionDropsPositionDependentEntries() {
    DamageMap next = Fixtures.damageMapWithPositionEntry().withTargetRevision(11L);
    assertTrue(next.opportunities().values().stream()
        .noneMatch(DamageOpportunity::positionDependent));
}
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.strategy.DamageMapTest --tests dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest
```

- [ ] **Step 3: Implement target-local caching and approval publication**

The non-reactive scanner may reuse `ClientCombatSnapshotBuilder`, candidate generation, and target prediction. Immediate `BREAK`, `PLACE`, `RECYCLE`, `FINISHER`, `STAIRCASE`, and `PRESSURE` approvals come from `FastOpportunitySelector`, not `BeamPlanner`. `BeamPlanner` may run only to propose `PREPARE` setup actions.

`ClientDamageScenarioFactory` combines target position hypotheses with state uncertainty. When remote absorption consumption or protected-window threshold is unknown, create explicit lower/upper plausible scenarios with reduced confidence and the matching `DamageUncertainty`; do not collapse them into one guessed state.

Each approval carries low-hundreds-of-milliseconds expiry plus exact current world/target/inventory/config revisions. Spawn-cycle approvals use `SpawnCrystalCycle(base,replaceAfterBreak)` and never guess the future entity ID.

- [ ] **Step 4: Run scanner + candidate/prediction/planner regressions**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*' --tests dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest --tests 'dev.adrien.crystaloptimizer.candidate.*' --tests 'dev.adrien.crystaloptimizer.prediction.*' --tests 'dev.adrien.crystaloptimizer.planner.*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: scan v2 immediate damage opportunities"
```

---

### Task 13: Add bounded target selection and ClientCombatCoordinator alongside V1

**Files:**
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/V2CoordinatorArchitectureTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CoordinatorReplayTest.java`

**Interfaces:**
- `tick()` performs only non-reactive target/scanner/restock work.
- `onEvent(CombatEvent)` performs direct decision -> materialization -> arbitration -> dispatch.
- V1 `ClientCombatRuntime` remains the production bootstrap in this task.

- [ ] **Step 1: Write the failing coordinator replay test**

Prepublish a `RECYCLE` approval, emit `CrystalSpawned(realId,base)`, assert attack/place are dispatched in order, and assert the strategic scanner invocation counter does not change during `onEvent`.

Architecture assertions:
```text
onEvent does not call ClientCombatSnapshotBuilder.build.
onEvent does not call BeamPlanner.plan.
tick may call ClientStrategicScanner.scan.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2CoordinatorArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.CoordinatorReplayTest
```

- [ ] **Step 3: Implement target manager and exact event path**

`TargetManager` keeps a valid current target sticky and evaluates at most three visible non-allied players. Recent attacker status may boost shortlist inclusion; final choice is driven by best immediate lethal-time opportunity, not a beam-plan prepass.

`onEvent` order:
```text
capture event timestamp
read current config + blackboard snapshot
ReactiveCombatEngine.decide
ActionArbiter.evaluate against ClientLiveCombatView
record decision-complete timestamp
ReactiveBurstDispatcher.dispatch
record dispatch timestamp
update base/pending-item/timing state
publish cached diagnostics
```

- [ ] **Step 4: Run V2 coordinator and V1 runtime tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.V2*' --tests 'dev.adrien.crystaloptimizer.v2.reactive.*' --tests 'dev.adrien.crystaloptimizer.execution.CombatRuntime*'
```
Expected: PASS with both runtimes still compiling.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: coordinate v2 reactive combat"
```

---

### Task 14: Add damage calibration diagnostics and cached TIME_TO_DAMAGE HUD data

**Files:**
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageMismatch.java`
- Create: `.../src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibration.java`
- Modify: `.../src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Modify: `.../src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibrationTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ClientDiagnosticsHudArchitectureTest.java`

**Interfaces:**
- `observePrediction(long actionId,LiveDamageTrace)` + `observeResult(long actionId,ObservedDamageResult)` returns mismatch classification.
- Render code consumes only cached immutable diagnostics.

- [ ] **Step 1: Write mismatch/HUD tests**

```java
@Test
void observedDamageOutsidePredictedIntervalProducesMismatch() {
    DamageCalibration calibration = new DamageCalibration();
    calibration.observePrediction(44L, Fixtures.traceWithEstimate(14.0f, 16.0f, 18.0f));
    DamageMismatch mismatch = calibration.observeResult(
        44L,
        new ObservedDamageResult(5.0f, false, false, 9L)
    ).orElseThrow();
    assertNotEquals(DamageMismatch.Kind.NONE, mismatch.kind());
    assertEquals(44L, mismatch.actionId());
}
```

A source architecture assertion must fail if `DamageCalibration` contains a field/method named `damageMultiplier`, `damageOffset`, or `fudge`, and fail if HUD render callbacks reference world scans/planners.

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageCalibrationTest --tests dev.adrien.crystaloptimizer.client.ClientDiagnosticsHudArchitectureTest
```

- [ ] **Step 3: Implement fixed mismatch taxonomy and cached HUD snapshot**

`DamageMismatch.Kind`:
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

HUD summary: enabled/strategy, target, reactive phase, selected approval, target damage lower/expected/upper, worst self damage, place->spawn p50/p90, last spawn->attack event->decision and decision->dispatch latency, last mismatch, last arbiter rejection. Rendering never touches level/planner/candidate generation.

- [ ] **Step 4: Run diagnostics tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.damage.*' --tests 'dev.adrien.crystaloptimizer.client.*Diagnostics*'
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: diagnose v2 combat latency and damage"
```

---

### Task 15: Add optional Mod Menu configuration and developer diagnostics screens

**Files:**
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigService.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java`
- Create: `.../src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java`
- Modify: `.../src/main/resources/fabric.mod.json`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/OptimizerConfigServiceTest.java`
- Test: `.../src/test/java/dev/adrien/crystaloptimizer/client/ModMenuIntegrationArchitectureTest.java`

**Interfaces:**
- `current()`, `apply(OptimizerConfig)`, `revision()`, `addListener(Consumer<OptimizerConfig>)`.
- Mod Menu factory returns `OptimizerConfigScreen`.

- [ ] **Step 1: Write persistence/optional-integration tests**

```java
@Test
void applyValidatesPersistsAndPublishesOneSnapshot() throws IOException {
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

Architecture assertions:
```text
fabric.mod.json has a modmenu entrypoint.
modmenu is absent from depends.
Normal screen exposes only the spec's normal settings.
Advanced screen is read-only for timing/damage internals.
```

- [ ] **Step 2: Run focused tests and confirm RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.OptimizerConfigServiceTest --tests dev.adrien.crystaloptimizer.client.ModMenuIntegrationArchitectureTest
```

- [ ] **Step 3: Implement atomic persistence and compact vanilla UI**

Persist `crystaloptimizer.json` in Fabric config dir using Gson. Write to sibling temp file and atomically replace when supported. On malformed JSON, rename the bad file to `crystaloptimizer.json.invalid` and load/write defaults.

Main screen groups:
```text
General: Enabled, Strategy, Target Range
Combat: Min Damage, Max Self Damage, Face Place HP, Crystals, Anchors, Auto Restock
Execution: Rotation
Visual: HUD, Advanced Diagnostics
Footer: Cancel, Save
```

Use vanilla `Button`, `CycleButton`, and numeric `EditBox` widgets. Save constructs one validated `OptimizerConfig` and calls `OptimizerConfigService.apply` once.

```java
public final class CrystalOptimizerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new OptimizerConfigScreen(parent, OptimizerConfigService.instance());
    }
}
```

Add Fabric entrypoint:
```json
"modmenu": [
  "dev.adrien.crystaloptimizer.client.integration.CrystalOptimizerModMenu"
]
```
and `suggests.modmenu = ">=18.0.0-beta.1"`; do not add to `depends`.

- [ ] **Step 4: Run config/UI architecture tests and build**

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
- Delete after gates pass: `.../src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java`
- Delete after `git grep` proves no production references: obsolete V1 commit/runtime classes such as `CombatRuntimeEngine`, `CommitPolicy`, `CommitScheduler`, `PlanExecutionController`, `PlanExecutionDriver`, `RuntimeFrame`, `RuntimePlanner`, plus tests whose only purpose was those removed orchestration classes.
- Keep: `BeamPlanner`, simulation/legality/reconciliation primitives still used by V2, inventory coordination, prediction, and preparation logic.

**Interfaces:**
- Final `CrystalOptimizerClient` bootstraps config service + V2 coordinator, subscribes event bus, registers cached HUD, and keeps O as the same config enable toggle.

- [ ] **Step 1: Write latency and timing replay gates before the production switch**

Use preallocated deterministic replay fixtures and measure only decision/arbitration CPU work, not network time:

```java
@Test
void preapprovedReactivePathMeetsCpuLatencyGateAndBeatsV1() {
    LatencySamples v2 = benchmarkV2SpawnBreak(2_000, 200);
    LatencySamples v1 = benchmarkEquivalentV1Decision(2_000, 200);
    assertTrue(v2.p50Millis() <= 1.0, () -> "V2 p50=" + v2.p50Millis());
    assertTrue(v2.p95Millis() <= 2.0, () -> "V2 p95=" + v2.p95Millis());
    assertTrue(v1.p50Nanos() / (double)Math.max(1L, v2.p50Nanos()) >= 5.0,
        () -> "V2 did not achieve 5x median speedup");
}
```

Timing trace format is one event per line:
```text
transition,startNanos,endNanos,correlationHigh,correlationLow
CRYSTAL_PLACE_TO_SPAWN,1000000000,1025000000,2,12345
```
`TimingReplayTest` parses all three resource files and asserts p90 >= p50, stale confidence decays, jitter/degraded traces produce more pessimistic p90 completion, and missing required transitions return confidence 0 instead of fabricated certainty.

- [ ] **Step 2: Run all acceptance-focused tests with V1 still present**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.*' --tests 'dev.adrien.crystaloptimizer.client.V2*' --stacktrace
gradle --no-daemon runGameTest --stacktrace
```
Expected: all V2 unit/architecture/differential/recycle/timing/latency gates pass before bootstrap changes.

- [ ] **Step 3: Switch client bootstrap and remove dead V1 orchestration**

Final shape:
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

Before deleting each V1 class, run `git grep` for its simple name under `src/main` and `src/client`; delete only when no V2 production code needs it. Do not delete mechanics/reconciliation classes merely because they lived under an old package.

- [ ] **Step 4: Run authoritative clean verification**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon clean test build runGameTest --stacktrace
```
Expected: `BUILD SUCCESSFUL`, all unit tests and GameTests pass, latency gates pass, and runtime JAR contains Fabric metadata, mixins, V2 coordinator/reactive engine, optional Mod Menu entrypoint, and config screens.

Verify forbidden hot-path dependencies:
```bash
git grep -n 'BeamPlanner\|ClientCombatSnapshotBuilder\|CandidateGenerator\|TargetPredictor' -- \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java
```
Expected: no matches. Strategic-scanner files are intentionally outside this check.

- [ ] **Step 5: Update README and commit the V2 cutover**

README must document:
```text
Minecraft 26.1.2
Java 25
Fabric Loader >=0.19.3
Fabric API 0.155.2+26.1.2
Version 0.2.0
O toggles the optimizer
Mod Menu is optional and opens full config when installed
Default strategy is Lethal Speed
Reactive crystal recycling waits for real server entity IDs
No silent rotations/fake packets/hidden-inventory assumptions
Damage diagnostics show uncertainty instead of fake exact numbers
```

Commit:
```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2
git commit -m "feat: switch crystal optimizer to v2"
```

---

## Final Review Checklist Before Opening/Merging the V2 PR

- [ ] `OptimizerConfig.defaults()` is `LETHAL_SPEED`; Mod Menu remains optional.
- [ ] O and Mod Menu mutate the same `OptimizerConfigService` snapshot.
- [ ] `ReactiveCombatEngine` and `ClientCombatCoordinator.onEvent` have no planner/world-scan dependency.
- [ ] New crystal IDs come only from real `CrystalSpawned` observations.
- [ ] Break->replace may send both ordered vanilla interactions without waiting for local crystal removal; the next break waits for the real replacement spawn ID.
- [ ] In-flight predicted item consumption cannot double-spend locally visible stacks.
- [ ] Pop->finisher preempts recycle when the finisher is the higher-priority legal approval.
- [ ] Hurt-window selection scores useful marginal damage and lethal time, not raw damage/CPS alone.
- [ ] Typed timing distributions cover block ack, place->spawn, attack->removal, pop->visible refill, and cadence.
- [ ] Exact-observable damage matches vanilla GameTests; uncertain live cases expose lower/expected/upper bounds.
- [ ] Calibration only diagnoses mismatch; it never alters damage with an opaque multiplier/offset.
- [ ] HUD/config rendering performs no world scan or planner work.
- [ ] V2 reactive event->dispatch CPU p50 <= 1 ms, p95 <= 2 ms, and median is at least 5x faster than equivalent V1 replay on the same harness.
- [ ] `gradle --no-daemon clean test build runGameTest --stacktrace` succeeds under Java 25.
- [ ] `work/crystal-anchor-combat-optimizer-26-1-2` still exists.
