# Crystal Anchor Combat Optimizer V2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the planner-centric V1 runtime with an event-driven V2 Crystal/Anchor combat engine that prioritizes absolute lethal speed, honest damage prediction, typed server timing, same-base crystal recycling, and optional Mod Menu configuration while preserving legitimate vanilla 26.1.2 mechanics.

**Architecture:** Keep the verified simulation/legality core, but move hot combat decisions into a small reactive lane fed by immutable approvals in `CombatBlackboard`. A cheap strategic scanner continuously refreshes target-local opportunities; `ReactiveCombatEngine` materializes already-approved actions on crystal spawn/removal/totem events, `ActionArbiter` performs only cheap current-state validation, and the existing vanilla dispatcher performs real interactions. V1 stays present until V2 passes damage differential, recycle, latency, legality, and full GameTest gates.

**Tech Stack:** Java 25, Minecraft Java 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.155.2+26.1.2, Fabric Loom 1.17-SNAPSHOT, Gradle 9.5.1, JUnit 5, Fabric GameTest, optional Mod Menu 18.0.0-beta.1.

**Spec:** `projects/crystal-anchor-combat-optimizer-26-1-2/docs/superpowers/specs/2026-08-19-crystal-optimizer-v2-design.md`

## Global Constraints

- Target Minecraft Java 26.1.2 and Java 25 only.
- Remain a client-side Fabric mod.
- Use only legitimate vanilla/client-observable state: no fabricated entity IDs, fake packets, hidden opponent inventory, impossible movement/state, silent/server-only rotations, fake crits, or fake server RNG knowledge.
- Never attack a newly placed crystal until its real server-observed entity ID exists.
- Same-base throughput is `break -> place -> wait for real spawn -> break -> place`; never model simultaneous crystals in the same occupied placement volume.
- `LETHAL_SPEED` prioritizes immediate useful lethal damage and has no artificial stopwatch CPS cap in the reactive lane.
- Target-damage uncertainty is explicit; self-damage uncertainty is pessimistic.
- Never add a global damage multiplier, unexplained offset, or fudge factor.
- Server explosion terrain destruction stays uncertain until server block updates make it observable.
- Future Client is behavioral/reference material only; do not copy its decompiled source unless a compatible license is independently established.
- Mod Menu is optional at runtime; the mod must load, toggle with O, and render its HUD without Mod Menu installed.
- Keep the existing `work/crystal-anchor-combat-optimizer-26-1-2` branch; this plan never deletes it.
- Target release version is `0.2.0`.
- Every implementation task follows RED -> GREEN -> focused regression -> commit.

---

## Locked File/Type Map

Main-source additions:

```text
src/main/java/dev/adrien/crystaloptimizer/config/OptimizerStrategy.java
src/main/java/dev/adrien/crystaloptimizer/config/OptimizerConfig.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageUncertainty.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEstimate.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageScenario.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/LiveDamageTrace.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngine.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/ObservedDamageResult.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageMismatch.java
src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibration.java
src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingTransition.java
src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingCorrelation.java
src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingDistribution.java
src/main/java/dev/adrien/crystaloptimizer/v2/timing/SequenceTiming.java
src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngine.java
src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEvent.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/ApprovalSlot.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/ReactiveActionSpec.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/FixedActionSequence.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/SpawnCrystalCycle.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardSnapshot.java
src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboard.java
src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java
src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java
src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java
src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java
src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBasePhase.java
src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTracker.java
src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveDecision.java
src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngine.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtThresholdEstimate.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTracker.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/SelectionContext.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMap.java
src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java
src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java
```

Client-source additions:

```text
src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigService.java
src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java
src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java
src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageScenarioFactory.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/BurstReceipt.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java
src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java
```

Existing code kept as the mechanics/platform layer:

```text
src/main/java/dev/adrien/crystaloptimizer/action/*
src/main/java/dev/adrien/crystaloptimizer/sim/damage/*
src/main/java/dev/adrien/crystaloptimizer/sim/model/*
src/main/java/dev/adrien/crystaloptimizer/world/*
src/main/java/dev/adrien/crystaloptimizer/prediction/*
src/main/java/dev/adrien/crystaloptimizer/execution/InventoryCoordinator.java
src/main/java/dev/adrien/crystaloptimizer/planner/BeamPlanner.java
src/client/java/dev/adrien/crystaloptimizer/client/execution/RotationController.java
src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java
src/client/java/dev/adrien/crystaloptimizer/client/intel/ClientObservationBus.java
src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java
src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java
```

---

### Task 1: Establish V2 config contracts and 0.2.0 metadata

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerStrategy.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/config/OptimizerConfig.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/gradle.properties`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/build.gradle`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/config/OptimizerConfigTest.java`

**Interfaces:**
- Produces `OptimizerStrategy.LETHAL_SPEED|AGGRESSIVE|SAFE`.
- Produces `OptimizerConfig.defaults()`, `validated()`, and `withEnabled(boolean)`.
- Reuses existing `dev.adrien.crystaloptimizer.execution.RotationMode`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void defaultsAreLethalSpeedAndValidationRejectsBadRange() {
    OptimizerConfig config = OptimizerConfig.defaults();
    assertEquals(OptimizerStrategy.LETHAL_SPEED, config.strategy());
    assertEquals(RotationMode.ADAPTIVE, config.rotationMode());
    assertTrue(config.crystals());
    assertTrue(config.anchors());
    assertFalse(config.enabled());
    assertTrue(config.withEnabled(true).enabled());

    OptimizerConfig invalid = new OptimizerConfig(
        true, OptimizerStrategy.LETHAL_SPEED, 0.5, 4.0f, 12.0f, 8.0f,
        true, true, true, RotationMode.ADAPTIVE, true
    );
    assertThrows(IllegalArgumentException.class, invalid::validated);
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.config.OptimizerConfigTest
```
Expected: compile failure because V2 config types do not exist.

- [ ] **Step 3: Implement the immutable config**

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
    public static OptimizerConfig defaults() {
        return new OptimizerConfig(
            false, OptimizerStrategy.LETHAL_SPEED, 12.0, 4.0f, 12.0f, 8.0f,
            true, true, true, RotationMode.ADAPTIVE, true
        );
    }

    public OptimizerConfig validated() {
        check("targetRange", targetRange, 1.0, 16.0);
        check("minDamage", minDamage, 0.0, 40.0);
        check("maxSelfDamage", maxSelfDamage, 0.0, 40.0);
        check("facePlaceHealth", facePlaceHealth, 0.0, 40.0);
        return this;
    }

    public OptimizerConfig withEnabled(boolean next) {
        return new OptimizerConfig(
            next, strategy, targetRange, minDamage, maxSelfDamage, facePlaceHealth,
            crystals, anchors, autoRestock, rotationMode, hud
        );
    }

    private static void check(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " outside [" + min + "," + max + "]");
        }
    }
}
```

In `gradle.properties`, set:

```properties
mod_version=0.2.0
modmenu_version=18.0.0-beta.1
```

In `build.gradle`, add Terraformers Maven and:

```groovy
implementation "com.terraformersmc:modmenu:${project.modmenu_version}"
```

Do not add Mod Menu to Fabric `depends`.

- [ ] **Step 4: Run GREEN and baseline unit tests**

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

### Task 2: Lock the shared V2 contracts and CombatBlackboard

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageUncertainty.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEstimate.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing/SequenceTiming.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CombatEvent.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/ApprovalSlot.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/ReactiveActionSpec.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/FixedActionSequence.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/SpawnCrystalCycle.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardSnapshot.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboard.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/state/CombatBlackboardTest.java`

**Interfaces:** These signatures are frozen for later tasks.

- [ ] **Step 1: Write the failing contract test**

```java
@Test
void spawnCycleUsesOnlyObservedIdAndBlackboardSnapshotIsImmutable() {
    UUID target = UUID.randomUUID();
    BlockPos base = new BlockPos(4, 63, 7);
    ActionApproval approval = new ActionApproval(
        77L,
        target,
        ApprovalSlot.RECYCLE,
        new SpawnCrystalCycle(base, true),
        DamageEstimate.exact(18.0f, 3L, 5L),
        6.0f,
        SequenceTiming.immediate(),
        3L, 9L, 11L, 13L, 5_000L
    );
    CombatBlackboard board = new CombatBlackboard();
    board.publish(new CombatBlackboardSnapshot(
        target, 9L, 3L, 11L, 13L, Map.of(ApprovalSlot.RECYCLE, approval)
    ));

    List<CombatAction> actions = approval.actionSpec().materialize(
        new CombatEvent.CrystalSpawned(412, base, 1_000L)
    );
    assertEquals(List.of(new AttackKnownCrystal(412), new PlaceCrystal(base)), actions);
    assertThrows(UnsupportedOperationException.class,
        () -> board.snapshot().approvals().clear());
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.state.CombatBlackboardTest
```
Expected: missing V2 contract types.

- [ ] **Step 3: Implement the frozen contracts**

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
            throw new IllegalArgumentException("unordered damage bounds");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence outside [0,1]");
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

    public static SequenceTiming unknown(int boundaries) {
        return new SequenceTiming(
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            boundaries,
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

```java
public sealed interface ReactiveActionSpec permits FixedActionSequence, SpawnCrystalCycle {
    List<CombatAction> materialize(CombatEvent event);
}

public record FixedActionSequence(List<CombatAction> actions) implements ReactiveActionSpec {
    public FixedActionSequence {
        actions = List.copyOf(actions);
        if (actions.isEmpty()) throw new IllegalArgumentException("empty action sequence");
    }

    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        return actions;
    }
}

public record SpawnCrystalCycle(BlockPos basePos, boolean replaceAfterBreak)
    implements ReactiveActionSpec {
    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        if (!(event instanceof CombatEvent.CrystalSpawned spawned)
            || !spawned.basePos().equals(basePos)) {
            return List.of();
        }
        CombatAction attack = new AttackKnownCrystal(spawned.entityId());
        return replaceAfterBreak
            ? List.of(attack, new PlaceCrystal(basePos))
            : List.of(attack);
    }
}
```

`ActionApproval` is exactly:

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

`CombatBlackboardSnapshot` copies its approval map with `Map.copyOf`. `CombatBlackboard` stores one `AtomicReference<CombatBlackboardSnapshot>` and only publishes complete snapshots.

- [ ] **Step 4: Run GREEN**

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

### Task 3: Implement DamageEngine interval aggregation

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageScenario.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/LiveDamageTrace.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngine.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageEngineTest.java`

**Interfaces:**

```java
public record DamageScenario(
    SimCombatant victim,
    Vec3 position,
    AABB box,
    double probabilityWeight,
    double confidence,
    Set<DamageUncertainty> uncertainties
) {}
```

`DamageEngine.estimate(ExplosionContext,CombatState,List<DamageScenario>,long,long)` returns Task 2 `DamageEstimate`.

- [ ] **Step 1: Write failing exact/uncertain tests**

```java
@Test
void exactSingleScenarioCollapsesAndUncertainScenariosRemainBounded() {
    DamageEstimate exact = engine.estimate(
        explosion,
        state,
        List.of(new DamageScenario(target, targetPos, targetBox, 1.0, 1.0, Set.of())),
        7L,
        11L
    );
    assertTrue(exact.exact());

    DamageEstimate uncertain = engine.estimate(
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
    assertTrue(uncertain.lowerBound() <= uncertain.expected());
    assertTrue(uncertain.expected() <= uncertain.upperBound());
    assertTrue(uncertain.confidence() < 1.0);
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageEngineTest
```

- [ ] **Step 3: Aggregate existing exact simulator results**

```java
public DamageEstimate estimate(
    ExplosionContext explosion,
    CombatState state,
    List<DamageScenario> scenarios,
    long geometryRevision,
    long combatRevision
) {
    if (scenarios.isEmpty()) throw new IllegalArgumentException("no damage scenarios");

    float lower = Float.POSITIVE_INFINITY;
    float upper = Float.NEGATIVE_INFINITY;
    double weightedDamage = 0.0;
    double weightedConfidence = 0.0;
    double totalWeight = 0.0;
    EnumSet<DamageUncertainty> reasons = EnumSet.noneOf(DamageUncertainty.class);

    for (DamageScenario scenario : scenarios) {
        if (!Double.isFinite(scenario.probabilityWeight()) || scenario.probabilityWeight() <= 0.0) {
            throw new IllegalArgumentException("scenario probability weight must be positive");
        }
        if (!Double.isFinite(scenario.confidence())
            || scenario.confidence() < 0.0 || scenario.confidence() > 1.0) {
            throw new IllegalArgumentException("scenario confidence outside [0,1]");
        }
        float incoming = ExplosionDamageCalculator26.incoming(
            explosion, scenario.box(), scenario.position(), state.geometry()
        );
        DamageResult result = VanillaDamageSimulator.apply(
            scenario.victim(),
            DamageRequest.explosion(incoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );
        float damage = result.trace().healthDamage();
        lower = Math.min(lower, damage);
        upper = Math.max(upper, damage);
        weightedDamage += damage * scenario.probabilityWeight();
        weightedConfidence += scenario.confidence() * scenario.probabilityWeight();
        totalWeight += scenario.probabilityWeight();
        reasons.addAll(scenario.uncertainties());
    }

    return new DamageEstimate(
        lower,
        (float)(weightedDamage / totalWeight),
        upper,
        weightedConfidence / totalWeight,
        reasons,
        geometryRevision,
        combatRevision
    );
}
```

`LiveDamageTrace` stores explosion source, `DamageEstimate`, exact per-scenario `DamageTrace` values, and the revisions used; it performs no correction.

- [ ] **Step 4: Run GREEN plus existing simulator tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.damage.*' --tests 'dev.adrien.crystaloptimizer.sim.damage.*'
```
Expected: PASS with the V1 simulator unchanged.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/damage
git commit -m "feat: add v2 damage intervals"
```

---

### Task 4: Differential-test damage/exposure against vanilla GameTests

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/gametest/java/dev/adrien/crystaloptimizer/gametest/ServerLevelBlockView.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/gametest/java/dev/adrien/crystaloptimizer/gametest/GameTestCombatants.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/gametest/java/dev/adrien/crystaloptimizer/gametest/ExplosionDifferentialGameTests.java`

**Interfaces:**
- `ServerLevelBlockView implements BlockView` with real server block states/collision shapes.
- Oracle: `GameTestHelper.makeMockServerPlayerInLevel()` plus Minecraft 26.1.2 `Level.explode(Entity,double,double,double,float,Level.ExplosionInteraction)`.

- [ ] **Step 1: Write the first failing differential GameTest**

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
    DamageResult predicted = VanillaDamageSimulator.apply(
        GameTestCombatants.exactFirstHit(target),
        DamageRequest.explosion(raw)
            .withDifficulty(level.getDifficulty())
            .withSourcePosition(center)
    );

    level.explode(null, center.x, center.y, center.z, 6.0f, Level.ExplosionInteraction.NONE);
    float observedLoss = before - target.getHealth();
    helper.assertTrue(
        Math.abs(observedLoss - predicted.trace().healthDamage()) <= 1.0e-4f,
        "vanilla and simulator damage diverged"
    );
    helper.succeed();
}
```

`ServerLevelBlockView` is concrete:

```java
public record ServerLevelBlockView(ServerLevel level) implements BlockView {
    @Override
    public BlockState blockState(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public VoxelShape collisionShape(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos);
    }
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon runGameTest --stacktrace
```
Expected: the new fixture/test fails to compile or fails until the full exact state extractor is present; existing GameTests remain identifiable separately.

- [ ] **Step 3: Implement the vanilla-oracle matrix**

Add these named tests, each comparing project output against a real server outcome rather than a hardcoded damage number:

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

`GameTestCombatants.exactFirstHit(ServerPlayer)` must extract current health, armor/toughness/durability, protection/blast-protection, resistance/effects, blocking, absorption, visible hand totem state, and use `new HurtWindowState(0, 0.0f)` for first-hit fixtures. Protected-window sequence tests apply the first real vanilla explosion and first simulator explosion from the same initial state, then use the simulator's now-known threshold for the second calculation; they do not claim that a remote client knows hidden `lastHurt`.

- [ ] **Step 4: Run GREEN twice for flake detection**

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
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingTransition.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingCorrelation.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingDistribution.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngine.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/timing/TimingEngineTest.java`

**Interfaces:**
- Reuses Task 2 `SequenceTiming`.
- Exposes `recordStart`, `recordEnd`, `distribution`, and `estimateSequence`.

- [ ] **Step 1: Write failing typed-percentile test**

```java
@Test
void placeToSpawnHasItsOwnDistributionAndCountsOneHardBoundary() {
    TimingEngine engine = new TimingEngine(64, 5_000_000_000L);
    long base = 1_000_000_000L;
    for (int i = 0; i < 10; i++) {
        TimingCorrelation key = TimingCorrelation.place(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            i,
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
    assertEquals(1, engine.estimateSequence(
        List.of(TimingTransition.IMMEDIATE, TimingTransition.CRYSTAL_PLACE_TO_SPAWN),
        base + 1_100_000_000L
    ).hardFeedbackBoundaries());
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.timing.TimingEngineTest
```

- [ ] **Step 3: Implement typed correlations/distributions**

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
public record TimingCorrelation(
    TimingTransition transition,
    long high,
    long low
) {
    public static TimingCorrelation sequence(TimingTransition t, int sequence) {
        return new TimingCorrelation(t, 0L, Integer.toUnsignedLong(sequence));
    }

    public static TimingCorrelation entity(TimingTransition t, int entityId) {
        return new TimingCorrelation(t, 1L, Integer.toUnsignedLong(entityId));
    }

    public static TimingCorrelation place(TimingTransition t, int sequence, BlockPos pos) {
        return new TimingCorrelation(t, Integer.toUnsignedLong(sequence), pos.asLong());
    }

    public static TimingCorrelation player(TimingTransition t, UUID id) {
        return new TimingCorrelation(t, id.getMostSignificantBits(), id.getLeastSignificantBits());
    }
}
```

`TimingEngine` keeps a bounded deque of completed duration samples per `TimingTransition`. It computes sorted p50/p90 and median absolute deviation. Confidence uses sample count and freshness only; it never changes measured milliseconds. If any required hard-feedback transition in a candidate sequence has no usable samples, `estimateSequence` returns `SequenceTiming.unknown(boundaryCount)`.

- [ ] **Step 4: Run GREEN plus V1 timing regression**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.timing.*' --tests 'dev.adrien.crystaloptimizer.timing.*'
```
Expected: PASS; `ServerTimingModel` remains unchanged for V1 comparison.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/timing \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/timing
git commit -m "feat: add typed server timing engine"
```

---

### Task 6: Wire exact 26.1.2 packet/world events into V2

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/intel/ClientObservationBus.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/V2PacketObservationArchitectureTest.java`

**Interfaces:**
- `ClientCombatEventBus.subscribe(Consumer<CombatEvent>)` and synchronous `publish(CombatEvent)`.
- `ClientTimingObserver` owns one `TimingEngine`, a per-base pending-place FIFO, and typed correlation helpers.

- [ ] **Step 1: Write failing source-architecture test**

The test asserts these exact Minecraft 26.1.2 hooks exist after implementation:

```text
handleAddEntity @ TAIL
handleRemoveEntities @ HEAD
handleBlockUpdate @ TAIL
handleChunkBlocksUpdate @ TAIL
handleBlockChangedAck @ TAIL
handleEntityEvent @ TAIL
```

It also asserts mixin/event-bus code does not reference `BeamPlanner`, `CandidateGenerator`, `TargetPredictor`, or `ClientCombatSnapshotBuilder`.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2PacketObservationArchitectureTest
```

- [ ] **Step 3: Implement exact event/timing routing**

Use the source-verified 26.1.2 methods:

```java
@Inject(method = "handleAddEntity", at = @At("TAIL"))
private void crystaloptimizer$v2AddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) return;
    Entity entity = level.getEntity(packet.getId());
    if (entity instanceof EndCrystal crystal) {
        BlockPos base = BlockPos.containing(
            crystal.getX(), crystal.getY() - 1.0, crystal.getZ()
        );
        long now = System.nanoTime();
        ClientTimingObserver.instance().onCrystalSpawned(base, now);
        ClientCombatEventBus.instance().publish(
            new CombatEvent.CrystalSpawned(crystal.getId(), base, now)
        );
    }
}
```

At `handleRemoveEntities` HEAD, iterate `packet.getEntityIds()`, inspect still-present entities, and publish `CrystalRemoved` before vanilla deletes them; call `ClientTimingObserver.onCrystalRemoved(entityId,now)`.

At `handleBlockUpdate` TAIL publish `BlockChanged(packet.getPos(),now)`. At `handleChunkBlocksUpdate` TAIL use the source-verified `packet.runUpdates((pos,state) -> ...)` and publish each immutable position.

Outgoing `ServerboundUseItemOnPacket` has source-verified `getHitResult()` and `getSequence()`. Keep the existing block-ack start, and if the local main-hand item is `Items.END_CRYSTAL`, also call:

```java
ClientTimingObserver.instance().onCrystalPlaceSent(
    useItemOn.getSequence(),
    useItemOn.getHitResult().getBlockPos(),
    System.nanoTime()
);
```

`ClientTimingObserver.onCrystalPlaceSent` stores `TimingCorrelation.place(...)` in a FIFO keyed by base. `onCrystalSpawned(base,now)` pops the oldest pending correlation for that base and ends it; this avoids guessing a server sequence from the spawn packet.

When `PROTECTED_FROM_DEATH` targets a non-local player, keep the existing intel update and also publish `TotemPopped`; start `TOTEM_POP_TO_VISIBLE_REFILL` keyed by UUID. A later visible equipment packet placing a totem in either hand completes it.

- [ ] **Step 4: Run GREEN plus intel regression**

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

### Task 7: Add in-flight item reservations and lightweight live state

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedgerTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/ClientLiveCombatViewArchitectureTest.java`

**Interfaces:**

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

- [ ] **Step 1: Write failing double-spend test**

```java
@Test
void predictedPlacementCannotSpendOneVisibleCrystalTwice() {
    PendingItemLedger ledger = new PendingItemLedger();
    ledger.reserve(100L, Items.END_CRYSTAL, 1, 1);
    assertEquals(0, ledger.available(Items.END_CRYSTAL, 1));
    assertThrows(IllegalStateException.class,
        () -> ledger.reserve(101L, Items.END_CRYSTAL, 1, 1));
    ledger.release(100L);
    assertEquals(1, ledger.available(Items.END_CRYSTAL, 1));
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.PendingItemLedgerTest
```

- [ ] **Step 3: Implement reservation arithmetic and live-view adapter**

```java
public synchronized void reserve(long actionId, Item item, int count, int observedCount) {
    if (actionId < 0L || count <= 0) throw new IllegalArgumentException();
    if (available(item, observedCount) < count) {
        throw new IllegalStateException("insufficient unreserved item count");
    }
    reservations.put(actionId, new Reservation(item, count));
}

public synchronized int available(Item item, int observedCount) {
    return Math.max(0, observedCount - reserved(item));
}
```

`ClientLiveCombatView` receives world/target/inventory/config revision suppliers and reads only current player/world/entity/block/inventory state. `crystalBaseCanFollowBreak(base,id)` validates the same base using the known post-removal transition: the specified live crystal may be excluded from the entity occupancy check, but every other blocker remains real. It must not instantiate a `CombatSnapshot` or planner.

- [ ] **Step 4: Run GREEN and architecture guard**

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

### Task 8: Implement ActionArbiter as the final cheap gate

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiterTest.java`

**Interfaces:** `evaluate(ActionApproval,List<CombatAction>,LiveCombatView,PendingItemLedger,OptimizerConfig,long)` returns allowed actions or a typed rejection reason.

- [ ] **Step 1: Write failing ordered-transition and stale-state tests**

```java
@Test
void breakThenSameBasePlaceUsesPredictedPostBreakLegality() {
    ActionApproval approval = TestFixtures.recycleApproval(targetId, crystalId, basePos);
    LiveCombatView view = TestFixtures.liveViewFor(approval)
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

In the same test class, add cases for stale world/target/inventory/config revisions, expiry, removed crystal, out-of-reach block/entity, excessive worst-case self damage, and reserved/unavailable items. `TestFixtures` is a private nested test helper in `ActionArbiterTest`, not a production type.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.execution.ActionArbiterTest
```

- [ ] **Step 3: Implement the fixed gate order**

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
        view.configRevision(),
        nowNanos
    )) return ArbitrationResult.rejected(Reason.STALE_APPROVAL);

    if (!view.targetValid(approval.targetId())) {
        return ArbitrationResult.rejected(Reason.INVALID_TARGET);
    }
    if (approval.worstCaseSelfDamage() > config.maxSelfDamage()) {
        return ArbitrationResult.rejected(Reason.SELF_DAMAGE_LIMIT);
    }
    return validateConcreteActions(approval, actions, view, pendingItems);
}
```

`validateConcreteActions` is a linear walk. `AttackKnownCrystal` requires the real live entity and reach. A following `PlaceCrystal` on the approved base may use `crystalBaseCanFollowBreak`; ordinary placements require current block reach and current/legal base state. Item actions require `pendingItems.available(item,view.observedCount(item)) > 0`. The arbiter never calls candidate generation, target prediction, damage simulation, or beam search.

- [ ] **Step 4: Run GREEN plus legacy legality tests**

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

### Task 9: Implement ReactiveCombatEngine and crystal recycle lifecycle

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBasePhase.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTracker.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveDecision.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngine.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveCombatEngineTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CrystalBaseTrackerTest.java`

**Interfaces:**

```java
public record ReactiveDecision(
    long approvalId,
    ApprovalSlot slot,
    List<CombatAction> actions,
    long eventNanos,
    long decisionNanos,
    boolean critical
) {}
```

- [ ] **Step 1: Write failing priority/materialization tests**

```java
@Test
void realSpawnIdBecomesImmediateRecycleAttackAndPopFinisherPreemptsRecycle() {
    CombatBlackboardSnapshot recycle = TestFixtures.snapshotWith(
        ApprovalSlot.RECYCLE,
        TestFixtures.spawnCycleApproval(targetId, basePos, true)
    );
    ReactiveDecision spawned = engine.decide(
        new CombatEvent.CrystalSpawned(712, basePos, 1_000L),
        recycle,
        1_050L
    ).orElseThrow();
    assertEquals(List.of(
        new AttackKnownCrystal(712), new PlaceCrystal(basePos)
    ), spawned.actions());

    ReactiveDecision popped = engine.decide(
        new CombatEvent.TotemPopped(targetId, 2_000L),
        TestFixtures.snapshotWithFinisherAndRecycle(targetId),
        2_010L
    ).orElseThrow();
    assertEquals(ApprovalSlot.FINISHER, popped.slot());
}
```

Private nested `TestFixtures` constructs approvals/snapshots using Task 2 types.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```

- [ ] **Step 3: Implement fixed reactive priority and duplicate suppression**

```java
private static final List<ApprovalSlot> PRIORITY = List.of(
    ApprovalSlot.LETHAL,
    ApprovalSlot.FINISHER,
    ApprovalSlot.STAIRCASE,
    ApprovalSlot.RECYCLE,
    ApprovalSlot.BREAK,
    ApprovalSlot.PLACE,
    ApprovalSlot.PRESSURE,
    ApprovalSlot.PREPARE
);
```

`CrystalBasePhase` is `EMPTY`, `PLACE_SENT`, `LIVE`, `BREAK_SENT`, `INVALID`. `CrystalBaseTracker` stores base, phase, optional real entity ID, and the last consumed `(event identity, approvalId)` key. `ReactiveCombatEngine.decide` scans only the constant priority list, asks each approval template to materialize the current event, rejects empty/materialized duplicates, and returns the first non-empty decision. It never reads the world or invokes a planner.

- [ ] **Step 4: Run GREEN and hot-path architecture guard**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.reactive.*'
```
Expected: PASS; source guard finds no `BeamPlanner`, `CandidateGenerator`, `TargetPredictor`, or `ClientCombatSnapshotBuilder` in `ReactiveCombatEngine`.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive
git commit -m "feat: add event driven crystal fast lane"
```

---

### Task 10: Dispatch ordered break->replace bursts through vanilla APIs

**Files:**
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/BurstReceipt.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientTimingObserver.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/V2ReactiveBurstArchitectureTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive/BreakReplaceOrderingTest.java`

**Interfaces:**
- Keep V1 `dispatch(CombatAction)` until cutover.
- Add `dispatch(CombatAction,RotationMode,boolean critical)`.
- `BurstReceipt` is an immutable list of `DispatchReceipt`s.

- [ ] **Step 1: Write failing ordering/vanilla-path tests**

The tests assert:

```text
AttackKnownCrystal(381) is dispatched before PlaceCrystal(base).
The replacement calls Minecraft.gameMode.useItemOn.
No client-side replacement entity ID is generated.
Critical ADAPTIVE passes critical=true to RotationController.
A DEFERRED, WAITING, or FAILED receipt stops the rest of the burst.
An attack send starts CRYSTAL_ATTACK_TO_REMOVAL timing for that real entity ID.
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2ReactiveBurstArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.BreakReplaceOrderingTest
```

- [ ] **Step 3: Refactor the dispatcher without changing its vanilla action path**

Concrete compatibility wrapper:

```java
@Override
public DispatchReceipt dispatch(CombatAction action) {
    return dispatch(
        action,
        rotationMode,
        scheduler.phase() == CommitPhase.COMMITTED
    );
}
```

Concrete V2 rotation helper:

```java
private boolean aimAt(Vec3 target, RotationMode mode, boolean critical) {
    return rotations.updateToward(target, mode, critical);
}
```

In the V2 overload, keep the existing action branches and replace calls to the old `aimAt(target)` with `aimAt(target,mode,critical)`. In the real crystal attack branch, immediately after `minecraft.gameMode.attack(player,entity)` call:

```java
ClientTimingObserver.instance().onCrystalAttackSent(
    attack.entityId(),
    System.nanoTime()
);
```

`ReactiveBurstDispatcher` is:

```java
public BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config) {
    List<DispatchReceipt> receipts = new ArrayList<>();
    for (CombatAction action : decision.actions()) {
        reserveIfNeeded(decision.approvalId(), action);
        DispatchReceipt receipt = dispatcher.dispatch(
            action, config.rotationMode(), decision.critical()
        );
        receipts.add(receipt);
        if (receipt.status() != DispatchReceipt.Status.SENT) {
            releaseReservationIfSendDidNotHappen(decision.approvalId(), action, receipt);
            break;
        }
    }
    return new BurstReceipt(List.copyOf(receipts));
}
```

`reserveIfNeeded` reserves one End Crystal for `PlaceCrystal`, one Respawn Anchor for `PlaceAnchor`, and one Glowstone for `ChargeAnchor` against the current observed stack. Release the reservation on matching spawn/block-state success, explicit non-send failure, timeout/reconciliation failure, or inventory revision proving consumption.

- [ ] **Step 4: Run GREEN plus dispatcher/rotation/hand regressions**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2ReactiveBurstArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.BreakReplaceOrderingTest --tests dev.adrien.crystaloptimizer.execution.RotationMathTest --tests dev.adrien.crystaloptimizer.candidate.HandTruthfulnessTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: dispatch v2 break replace bursts"
```

---

### Task 11: Track hurt thresholds and rank useful damage by lethal time

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtThresholdEstimate.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTracker.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/SelectionContext.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/strategy/HurtWindowTrackerTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelectorTest.java`

**Interfaces:**

```java
public record HurtThresholdEstimate(
    float lowerBound,
    float expected,
    float upperBound,
    double confidence
) {}

public record SelectionContext(
    HurtThresholdEstimate threshold,
    float targetEffectiveHealth,
    OptimizerStrategy strategy
) {}

public record DamageOpportunity(
    String id,
    ReactiveActionSpec actionSpec,
    DamageEstimate targetDamage,
    float worstCaseSelfDamage,
    SequenceTiming timing,
    boolean lethal,
    boolean popsTotem,
    boolean positionDependent,
    Set<BlockPos> geometryDependencies
) {
    public DamageOpportunity {
        geometryDependencies = Set.copyOf(geometryDependencies);
    }
}
```

- [ ] **Step 1: Write failing staircase tests**

```java
@Test
void immediateUsefulDamageBeatsHigherRawDamageBehindFeedbackBoundary() {
    SelectionContext context = new SelectionContext(
        new HurtThresholdEstimate(18.0f, 19.0f, 20.0f, 0.8),
        10.0f,
        OptimizerStrategy.LETHAL_SPEED
    );
    DamageOpportunity immediate = TestFixtures.opportunity(
        "anchor", 29.0f, SequenceTiming.immediate()
    );
    DamageOpportunity delayed = TestFixtures.opportunity(
        "respawned-crystal", 33.0f, new SequenceTiming(120.0, 150.0, 1, 0.9)
    );
    assertEquals("anchor",
        selector.select(List.of(delayed, immediate), context).orElseThrow().id());
}

@Test
void weakerProtectedHitHasZeroUsefulLowerBound() {
    HurtThresholdEstimate threshold = new HurtThresholdEstimate(18.0f, 18.0f, 18.0f, 1.0);
    assertEquals(0.0f,
        FastOpportunitySelector.usefulLowerBound(17.0f, threshold), 1.0e-5f);
}
```

`TestFixtures` is private to the test class.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*'
```

- [ ] **Step 3: Implement evidence-backed threshold tracking and fixed ranking**

`HurtWindowTracker.observeAttributedIncoming(UUID,float,int,long)` records a derived exact threshold only when one project action can be unambiguously associated with the observed damage event. `estimate(UUID,int,long)` returns a known/derived envelope while protected time remains; otherwise it returns an unknown broad envelope and downstream damage includes `HURT_THRESHOLD_UNKNOWN`.

`LETHAL_SPEED` comparator order is fixed:

```text
high-confidence lethal
pop plus immediate finisher value
lower-bound useful marginal damage / p90 completion milliseconds
expected useful marginal damage / expected completion milliseconds
raw expected target damage
lower worst-case self damage
fewer hard feedback boundaries
```

Face-place may relax `minDamage` only when target effective health is at/below `facePlaceHealth`; it never bypasses max-self-damage or suicide policy.

- [ ] **Step 4: Run GREEN plus existing hurt-window tests**

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

### Task 12: Build the target-local DamageMap and cheap strategic approvals

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMap.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageScenarioFactory.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/strategy/DamageMapTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/V2StrategicScannerArchitectureTest.java`

**Interfaces:**
- `ClientDamageMapBuilder.update(AbstractClientPlayer,long,long,OptimizerConfig)` returns immutable `DamageMap`.
- `ClientStrategicScanner.scan(...)` atomically publishes one `CombatBlackboardSnapshot`.

- [ ] **Step 1: Write failing incremental invalidation tests**

```java
@Test
void unrelatedGeometryChangePreservesEntriesAndTargetMoveDropsPositionEntries() {
    DamageMap map = TestFixtures.mapWithTwoIndependentOpportunities();
    DamageMap unaffected = map.invalidateGeometry(Set.of(new BlockPos(100, 20, 100)));
    assertEquals(2, unaffected.opportunities().size());

    DamageMap moved = map.withTargetRevision(map.targetRevision() + 1L);
    assertTrue(moved.opportunities().values().stream()
        .noneMatch(DamageOpportunity::positionDependent));
}
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.strategy.DamageMapTest --tests dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest
```

- [ ] **Step 3: Implement incremental map and publication policy**

The scanner may use `ClientCombatSnapshotBuilder`, existing candidate generation, and target prediction on its non-reactive tick. Immediate `BREAK`, `PLACE`, `RECYCLE`, `FINISHER`, `STAIRCASE`, and `PRESSURE` approvals come from `FastOpportunitySelector`. `BeamPlanner` may only propose `PREPARE` setup actions.

`ClientDamageScenarioFactory` converts target position hypotheses and incomplete remote state into explicit `DamageScenario`s. Unknown absorption and protected-window threshold create lower/upper plausible scenarios with lower confidence and the matching `DamageUncertainty`; they are never replaced with one guessed scalar.

Each published approval stores current world/target/inventory/config revisions and an expiry in low hundreds of milliseconds. A spawn-cycle approval is `new SpawnCrystalCycle(base,replaceAfterBreak)`; it never contains a future entity ID.

- [ ] **Step 4: Run GREEN plus candidate/prediction/planner regressions**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.strategy.*' --tests dev.adrien.crystaloptimizer.client.V2StrategicScannerArchitectureTest --tests 'dev.adrien.crystaloptimizer.candidate.*' --tests 'dev.adrien.crystaloptimizer.prediction.*' --tests 'dev.adrien.crystaloptimizer.planner.*'
```
Expected: PASS; source guard proves reactive packages do not import scanner/planner code.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/strategy \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: scan v2 immediate damage opportunities"
```

---

### Task 13: Add bounded TargetManager and ClientCombatCoordinator beside V1

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/V2CoordinatorArchitectureTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CoordinatorReplayTest.java`

**Interfaces:**
- `tick()` does non-reactive target/scanner/restock work.
- `onEvent(CombatEvent)` does only reactive decision -> arbitration -> dispatch.
- V1 `ClientCombatRuntime` remains the production bootstrap in this task.

- [ ] **Step 1: Write failing coordinator replay/architecture test**

The replay prepublishes `RECYCLE`, emits `CrystalSpawned(realId,base)`, asserts attack/place dispatch order, and asserts the strategic scanner invocation counter does not change during `onEvent`.

The source guard asserts:

```text
ClientCombatCoordinator.onEvent contains no ClientCombatSnapshotBuilder.build call.
ClientCombatCoordinator.onEvent contains no BeamPlanner.plan call.
ClientCombatCoordinator.tick may call ClientStrategicScanner.scan.
```

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.V2CoordinatorArchitectureTest --tests dev.adrien.crystaloptimizer.v2.reactive.CoordinatorReplayTest
```

- [ ] **Step 3: Implement target selection and exact hot-path ordering**

`TargetManager` keeps a valid target sticky, shortlists at most three visible non-allied players, may boost a recent attacker into the shortlist, and chooses by best current immediate lethal-time opportunity rather than a beam-plan prepass.

`onEvent` is exactly:

```java
public void onEvent(CombatEvent event) {
    if (!configService.current().enabled()) return;
    CombatBlackboardSnapshot snapshot = blackboard.snapshot();
    Optional<ReactiveDecision> decision = reactive.decide(
        event, snapshot, System.nanoTime()
    );
    if (decision.isEmpty()) return;
    ArbitrationResult allowed = arbiter.evaluate(
        snapshot.approval(decision.orElseThrow().slot()).orElseThrow(),
        decision.orElseThrow().actions(),
        liveView,
        pendingItems,
        configService.current(),
        System.nanoTime()
    );
    if (!allowed.allowed()) {
        diagnostics.recordRejection(allowed.reason());
        return;
    }
    burstDispatcher.dispatch(decision.orElseThrow(), configService.current());
}
```

Wrap the three timing points around this body for `TimeToDamageTrace`: event timestamp, decision complete, and dispatch complete. Base/pending/timing reconciliation updates happen after the dispatch call and from later observed events.

- [ ] **Step 4: Run GREEN plus V1 runtime tests**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.client.V2*' --tests 'dev.adrien.crystaloptimizer.v2.reactive.*' --tests 'dev.adrien.crystaloptimizer.execution.CombatRuntime*'
```
Expected: PASS with both runtimes compiling.

- [ ] **Step 5: Commit**

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2 \
        projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer
git commit -m "feat: coordinate v2 reactive combat"
```

---

### Task 14: Add calibration diagnostics and cached TIME_TO_DAMAGE HUD data

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/ObservedDamageResult.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageMismatch.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibration.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/diagnostics/TimeToDamageTrace.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/damage/DamageCalibrationTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/ClientDiagnosticsHudArchitectureTest.java`

**Interfaces:**

```java
public record ObservedDamageResult(
    float healthLoss,
    boolean totemPopped,
    boolean targetDied,
    long combatRevision
) {}
```

- [ ] **Step 1: Write failing mismatch and HUD architecture tests**

```java
@Test
void outOfIntervalObservedDamageProducesMismatchWithoutCorrectionState() {
    DamageCalibration calibration = new DamageCalibration();
    calibration.observePrediction(
        44L,
        TestFixtures.traceWithEstimate(14.0f, 16.0f, 18.0f)
    );
    DamageMismatch mismatch = calibration.observeResult(
        44L,
        new ObservedDamageResult(5.0f, false, false, 9L)
    ).orElseThrow();
    assertNotEquals(DamageMismatch.Kind.NONE, mismatch.kind());
    assertEquals(44L, mismatch.actionId());
}
```

A source test rejects any `DamageCalibration` field/method containing `damageMultiplier`, `damageOffset`, or `fudge`. HUD render callbacks may not reference `Minecraft.level`, `ClientCombatSnapshotBuilder`, `BeamPlanner`, `CandidateGenerator`, or `TargetPredictor`.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.v2.damage.DamageCalibrationTest --tests dev.adrien.crystaloptimizer.client.ClientDiagnosticsHudArchitectureTest
```

- [ ] **Step 3: Implement fixed taxonomy and cached diagnostics**

```java
public record DamageMismatch(long actionId, Kind kind, float error) {
    public enum Kind {
        NONE,
        EXPOSURE_MISMATCH,
        STALE_GEOMETRY,
        HURT_THRESHOLD_UNKNOWN,
        ABSORPTION_UNCERTAINTY,
        EFFECT_STATE_CHANGED,
        TARGET_MOVED,
        ARMOR_STATE_CHANGED,
        ACTION_NOT_SERVER_ACCEPTED,
        INTERFERENCE,
        UNKNOWN
    }
}
```

`DamageCalibration` stores bounded prediction records by action ID, compares attributable observed health loss/totem outcome against the original `DamageEstimate` interval/revisions, emits one mismatch classification, and then evicts the record. It never mutates `DamageEngine`.

HUD summary reads only `ClientCombatDiagnostics` and shows enabled/strategy, target, reactive state, selected approval, target lower/expected/upper damage, worst self damage, place->spawn p50/p90, last event->decision and decision->dispatch latency, last mismatch kind, and last arbiter rejection.

- [ ] **Step 4: Run GREEN**

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

### Task 15: Add optional Mod Menu configuration and developer diagnostics

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigService.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/resources/fabric.mod.json`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/OptimizerConfigServiceTest.java`
- Test: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/client/ModMenuIntegrationArchitectureTest.java`

**Interfaces:**
- `OptimizerConfigService.current()`, `apply`, `revision`, `addListener`.
- `CrystalOptimizerModMenu implements ModMenuApi`.

- [ ] **Step 1: Write failing persistence/optional-integration tests**

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

Architecture assertions: `fabric.mod.json` has a `modmenu` entrypoint; `modmenu` is absent from `depends`; main screen exposes exactly the normal settings; diagnostics screen is read-only for timing/damage internals.

- [ ] **Step 2: Run RED**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests dev.adrien.crystaloptimizer.client.OptimizerConfigServiceTest --tests dev.adrien.crystaloptimizer.client.ModMenuIntegrationArchitectureTest
```

- [ ] **Step 3: Implement atomic JSON config and vanilla-style screens**

Persist `crystaloptimizer.json` in Fabric's config directory with Gson. Save to a sibling temporary file and move it over the target with `StandardCopyOption.ATOMIC_MOVE` when supported, falling back to `REPLACE_EXISTING`. If parsing/validation fails, rename the bad file to `crystaloptimizer.json.invalid`, load defaults, and write valid defaults.

Main screen sections:

```text
General: Enabled, Strategy, Target Range
Combat: Min Damage, Max Self Damage, Face Place HP, Crystals, Anchors, Auto Restock
Execution: Rotation
Visual: HUD, Advanced Diagnostics
Footer: Cancel, Save
```

Use vanilla `Button`, `CycleButton`, and numeric `EditBox` widgets. Save constructs one validated `OptimizerConfig` and invokes `OptimizerConfigService.apply` once.

```java
public final class CrystalOptimizerModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> new OptimizerConfigScreen(
            parent,
            OptimizerConfigService.instance()
        );
    }
}
```

Add to Fabric entrypoints:

```json
"modmenu": [
  "dev.adrien.crystaloptimizer.client.integration.CrystalOptimizerModMenu"
]
```

Add optional metadata:

```json
"suggests": {
  "modmenu": ">=18.0.0-beta.1"
}
```

Do not add Mod Menu under `depends`.

- [ ] **Step 4: Run GREEN and build**

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

### Task 16: Prove V2 gates, switch bootstrap, and remove superseded V1 orchestration

**Files:**
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/reactive/ReactiveLatencyGateTest.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/java/dev/adrien/crystaloptimizer/v2/timing/TimingReplayTest.java`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/resources/dev/adrien/crystaloptimizer/v2/timing/low-ping.trace`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/resources/dev/adrien/crystaloptimizer/v2/timing/jitter.trace`
- Create: `projects/crystal-anchor-combat-optimizer-26-1-2/src/test/resources/dev/adrien/crystaloptimizer/v2/timing/degraded-tps.trace`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Modify: `projects/crystal-anchor-combat-optimizer-26-1-2/README.md`
- Delete after gates pass: `projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java`
- Delete after zero production references are proven: superseded V1 orchestration classes under `projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/execution/` such as `CombatRuntimeEngine`, `CommitPolicy`, `CommitScheduler`, `PlanExecutionController`, `PlanExecutionDriver`, `RuntimeFrame`, `RuntimePlanner`, and tests dedicated only to removed classes.
- Keep: `BeamPlanner`, simulation/legality/prediction, `InventoryCoordinator`, and any reconciliation primitive still used by V2.

**Interfaces:** Final `CrystalOptimizerClient` bootstraps config + V2 coordinator, subscribes the event bus, registers cached HUD diagnostics, and keeps O as the same config enable toggle.

- [ ] **Step 1: Write latency/timing replay gates before switching production bootstrap**

The latency test uses private nested helpers, including:

```java
private record LatencySamples(long[] nanos) {
    long p50Nanos() { return percentile(0.50); }
    long p95Nanos() { return percentile(0.95); }
    double p50Millis() { return p50Nanos() / 1_000_000.0; }
    double p95Millis() { return p95Nanos() / 1_000_000.0; }

    private long percentile(double fraction) {
        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        int index = (int)Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
```

Gate:

```java
@Test
void preapprovedReactivePathMeetsCpuLatencyGateAndBeatsV1() {
    LatencySamples v2 = benchmarkV2SpawnBreak(2_000, 200);
    LatencySamples v1 = benchmarkEquivalentV1Decision(2_000, 200);
    assertTrue(v2.p50Millis() <= 1.0, () -> "V2 p50=" + v2.p50Millis());
    assertTrue(v2.p95Millis() <= 2.0, () -> "V2 p95=" + v2.p95Millis());
    assertTrue(v1.p50Nanos() / (double)Math.max(1L, v2.p50Nanos()) >= 5.0,
        "V2 did not achieve a 5x median CPU-path speedup");
}
```

Both benchmark helpers preallocate immutable state, warm up 200 iterations, then time only the decision/arbitration path with `System.nanoTime()`; they exclude Minecraft rendering/network completion and allocation-heavy setup.

Trace format:

```text
transition,startNanos,endNanos,correlationHigh,correlationLow
CRYSTAL_PLACE_TO_SPAWN,1000000000,1025000000,2,12345
```

`TimingReplayTest` parses the three resource files and asserts p90 >= p50, stale confidence decay, larger p90 under jitter/degraded traces, and confidence `0.0` when a required hard-feedback transition is absent.

- [ ] **Step 2: Run all V2 gates while V1 still exists**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon test --tests 'dev.adrien.crystaloptimizer.v2.*' --tests 'dev.adrien.crystaloptimizer.client.V2*' --stacktrace
gradle --no-daemon runGameTest --stacktrace
```
Expected: every V2 unit/architecture/differential/recycle/timing/latency gate passes before bootstrap changes.

- [ ] **Step 3: Switch production bootstrap only after Step 2 is green**

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
                OptimizerConfig current = config.current();
                config.apply(current.withEnabled(!current.enabled()));
            }
            coordinator.tick();
        });
    }
}
```

Before deleting a V1 class, run `git grep -n '<SimpleClassName>' -- projects/crystal-anchor-combat-optimizer-26-1-2/src/main projects/crystal-anchor-combat-optimizer-26-1-2/src/client`; delete it only when no V2 production reference remains. Do not delete mechanics/reconciliation code merely because it lived beside the old runtime.

- [ ] **Step 4: Run authoritative clean verification**

```bash
cd projects/crystal-anchor-combat-optimizer-26-1-2
gradle --no-daemon clean test build runGameTest --stacktrace
```
Expected: `BUILD SUCCESSFUL`; all unit tests and GameTests pass; latency gates pass; runtime JAR contains Fabric metadata, mixins, V2 coordinator/reactive engine, Mod Menu entrypoint, and config screens.

Verify the hot path contains no heavy dependencies:

```bash
git grep -n 'BeamPlanner\|ClientCombatSnapshotBuilder\|CandidateGenerator\|TargetPredictor' -- \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/main/java/dev/adrien/crystaloptimizer/v2/reactive \
  projects/crystal-anchor-combat-optimizer-26-1-2/src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java
```
Expected: no matches.

- [ ] **Step 5: Update README and commit the cutover**

README records:

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
No silent rotations, fake packets, or hidden-inventory assumptions
Damage diagnostics expose uncertainty rather than fake exact numbers
```

Commit:

```bash
git add projects/crystal-anchor-combat-optimizer-26-1-2
git commit -m "feat: switch crystal optimizer to v2"
```

---

## Required Final Review Before Opening/Merging the V2 PR

- [ ] Default strategy is `LETHAL_SPEED`; there is no artificial reactive CPS timer.
- [ ] O and Mod Menu mutate the same `OptimizerConfigService` snapshot.
- [ ] Mod Menu is absent from hard runtime `depends`.
- [ ] `ReactiveCombatEngine` and `ClientCombatCoordinator.onEvent` have no planner/world-scan dependency.
- [ ] New crystal IDs enter V2 only through real `CrystalSpawned` observations.
- [ ] Break->replace can send both ordered vanilla interactions without waiting for local crystal removal, but the next break waits for the real replacement spawn ID.
- [ ] In-flight predicted item consumption cannot double-spend a locally unchanged stack.
- [ ] Pop->finisher preempts recycle when the finisher is the higher-priority legal approval.
- [ ] Hurt-window selection scores useful marginal damage and lethal time rather than raw damage/CPS alone.
- [ ] Typed timing covers block ack, place->spawn, attack->removal, pop->visible refill, and server cadence.
- [ ] Exact-observable damage matches vanilla differential GameTests; uncertain cases expose lower/expected/upper bounds.
- [ ] Calibration diagnoses mismatches and never mutates simulator output.
- [ ] HUD/config rendering does no world scan/planner work.
- [ ] V2 reactive CPU p50 <= 1 ms, p95 <= 2 ms, and median is >=5x faster than equivalent V1 replay on the same harness.
- [ ] `gradle --no-daemon clean test build runGameTest --stacktrace` succeeds under Java 25.
- [ ] `work/crystal-anchor-combat-optimizer-26-1-2` still exists.
