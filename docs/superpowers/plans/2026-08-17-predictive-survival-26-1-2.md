# Predictive Survival 26.1.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Minecraft Java 26.1.2 Fabric client mod that predicts observable lethal damage with vanilla-faithful semantics and executes the safest server-valid survival action before the damage is processed.

**Architecture:** A pure deterministic damage/timeline core consumes immutable snapshots. Threat-specific predictors produce bounded future events, a planner compares a small set of feasible actions against the same timeline, and state-machine executors perform only server-valid client actions before conservative deadlines. Minecraft/Fabric adapters stay at the edge so the core math and policy remain unit-testable.

**Tech Stack:** Java 25; Minecraft Java 26.1.2; Fabric Loader 0.19.3; Fabric Loom 1.17-SNAPSHOT using `net.fabricmc.fabric-loom`; Fabric API 0.155.2+26.1.2; Fabric Loader JUnit; JUnit Jupiter 5.12.2; Gradle; GitHub Actions.

## Global Constraints

- Target exactly Minecraft Java Edition `26.1.2` and Java `25`.
- Use Fabric Loader `0.19.3`, Fabric Loom `1.17-SNAPSHOT`, Fabric API `0.155.2+26.1.2`, and plugin id `net.fabricmc.fabric-loom`.
- Do not add Yarn mappings or the legacy remapping Loom plugin.
- Production code is client-only. Game-test helpers live only in the Loom-created `gametest` source set and must not be packaged into the production jar.
- Treat exact 26.1.2 Minecraft source as authoritative; regenerate sources through Loom and re-check source before changing formulas.
- Runtime damage tags, item components, effects, and enchantments are authoritative where exposed; do not replace them with item-name heuristics.
- Never treat client-only desync, ghost inventory state, impossible movement, or packet flooding as protection.
- Unknown raw damage, unknown server `lastHurt`, uncertain event order, missed deadlines, or contradictory authoritative state must fail conservatively.
- Deliberate hurt-cooldown manipulation remains disabled outside Experimental mode until a tactic has deterministic tests and exact-runtime evidence.
- Keep user settings small: safety mode, restore prior hand/item, automatic movement/evasion, block placement/clutches, debug overlay/logging.
- Every implementation task follows red-green-refactor discipline and ends with a focused commit.

## Test Fixture Convention

Test snippets use helpers such as `fixture()` only as **private builders inside that test class**. Step 1 of each task includes creating the builder state needed by the shown assertions; those helper names are not production interfaces and must not leak into main code.

---

## Locked File Structure and Shared Contracts

```text
projects/predictive-survival-26-1-2/
  build.gradle
  gradle.properties
  settings.gradle
  VALIDATION.md
  src/main/resources/fabric.mod.json
  src/client/resources/predictive_survival.client.mixins.json
  src/client/java/dev/pixelied/survival/
    PredictiveSurvivalClient.java
    core/
      ModConstants.java
      DamageRange.java
      TickWindow.java
      Vec3Snapshot.java
      AabbSnapshot.java
      Confidence.java
      DifficultySnapshot.java
      PlayerSnapshot.java
      WorldSnapshot.java
      PredictionContext.java
      MinecraftSnapshotFactory.java
      SurvivalEngine.java
    damage/
      DamageFlag.java
      DamageSourceSnapshot.java
      BlockingSnapshot.java
      ArmorPieceSnapshot.java
      MitigationSnapshot.java
      EffectInstanceSnapshot.java
      StatusEffectsSnapshot.java
      DeathProtectionSnapshot.java
      HurtState.java
      DamageStage.java
      DamageTrace.java
      DamageResult.java
      VanillaDamageMath.java
      DamageSimulator.java
      ServerHurtStateTracker.java
      MinecraftDamageAdapter.java
      MinecraftBlockingAdapter.java
      MinecraftEquipmentAdapter.java
    timing/
      TimingSnapshot.java
      Deadline.java
      ServerTimingEstimator.java
    timeline/
      ThreatKind.java
      ThreatEvent.java
      ThreatTimeline.java
      TimelineResult.java
      ThreatTimelineSimulator.java
    threat/
      ThreatPredictor.java
      ThreatPredictorRegistry.java
      OcclusionView.java
      CoverCandidate.java
      ExplosionExposure.java
      ExplosionPredictor.java
      ProjectileStep.java
      ProjectileMotionModel.java
      BallisticProjectileModel.java
      AcceleratedProjectileModel.java
      FireworkProjectileModel.java
      ProjectilePredictor.java
      LandingPrediction.java
      FallLandingSolver.java
      FallPredictor.java
      HazardClockSnapshot.java
      PeriodicHazardPredictor.java
      MaceThreatModel.java
      SpearThreatModel.java
      MeleePredictor.java
    inventory/
      InventorySnapshot.java
      MenuSlotMap.java
      DeathProtectionRoute.java
      EmergencyInventoryTransaction.java
      DeathProtectionRoutePlanner.java
    planner/
      SafetyMode.java
      SurvivalAction.java
      ActionFeasibility.java
      ActionSimulation.java
      SurvivalPlan.java
      CoverCandidateEvaluator.java
      MovementCandidateGenerator.java
      EquipmentCandidateGenerator.java
      EffectCandidateGenerator.java
      FallRescueCandidateGenerator.java
      HurtCooldownCandidate.java
      HurtCooldownStrategy.java
      SurvivalPlanner.java
    action/
      ExecutionStatus.java
      ExecutionContext.java
      ActionExecutor.java
      DeathProtectionExecutor.java
      ShieldExecutor.java
      MovementExecutor.java
      CoverExecutor.java
      EquipmentExecutor.java
      EffectExecutor.java
      FallRescueExecutor.java
    debug/
      DecisionRecord.java
      DecisionHistory.java
      SurvivalDebugHud.java
    mixin/LocalPlayerAccessor.java
  src/test/java/dev/pixelied/survival/
    core/DomainTypesTest.java
    damage/DamageSimulatorPreprocessingTest.java
    damage/DamageSimulatorMitigationTest.java
    damage/DeathProtectionTest.java
    damage/ServerHurtStateTrackerTest.java
    damage/MinecraftAdapterContractTest.java
    timing/ServerTimingEstimatorTest.java
    timeline/ThreatTimelineSimulatorTest.java
    threat/ThreatPredictorRegistryTest.java
    threat/ExplosionPredictorTest.java
    threat/ProjectilePredictorTest.java
    threat/FallPredictorTest.java
    threat/PeriodicHazardPredictorTest.java
    threat/MeleePredictorTest.java
    inventory/DeathProtectionRoutePlannerTest.java
    inventory/EmergencyInventoryTransactionTest.java
    planner/SurvivalPlannerSafeModeTest.java
    planner/NonTotemActionTest.java
    planner/HurtCooldownStrategyTest.java
    planner/BalancedPolicyTest.java
    action/DeathProtectionExecutorTest.java
    action/ShieldExecutorTest.java
    action/NonTotemExecutorTest.java
    core/SurvivalEngineTest.java
    debug/DecisionHistoryTest.java
  src/gametest/java/dev/pixelied/survival/validation/
    ValidationStatus.java
    ValidationResult.java
    SurvivalValidationClientGameTest.java
    DamageValidationScenarios.java
  src/gametest/resources/fabric.mod.json
.github/workflows/predictive-survival-26-1-2-ci.yml
```

The following interfaces are locked so later tasks do not invent incompatible names:

```java
public record DamageRange(float min, float max) {
    public static DamageRange exact(float value);
    public DamageRange scale(float factor);
    public DamageRange subtractFloorZero(float value);
}

public record TickWindow(long earliest, long latest) {
    public boolean contains(long tick);
    public boolean overlaps(TickWindow other);
}

public enum Confidence { EXACT, MATCHED, BOUNDED, POTENTIAL, UNKNOWN }
public enum DifficultySnapshot { PEACEFUL, EASY, NORMAL, HARD }
public record Vec3Snapshot(double x, double y, double z) {}
public record AabbSnapshot(double minX, double minY, double minZ,
                           double maxX, double maxY, double maxZ) {}
```

`SurvivalAction.java` is one sealed interface with nested records, so no separate action-type files are required:

```java
public sealed interface SurvivalAction {
    record EquipDeathProtection(DeathProtectionRoute route) implements SurvivalAction {}
    record RaiseShield(Vec3Snapshot sourceDirection) implements SurvivalAction {}
    record MoveToSafety(Vec3Snapshot target) implements SurvivalAction {}
    record PlaceCover(CoverCandidate candidate) implements SurvivalAction {}
    record SwapEquipment(int inventoryIndex, String equipmentSlot) implements SurvivalAction {}
    record ApplyEffect(int inventoryIndex, String effectKey) implements SurvivalAction {}
    record FallRescue(FallRescueKind kind, Vec3Snapshot target) implements SurvivalAction {}
    record HurtCooldown(HurtCooldownCandidate candidate) implements SurvivalAction {}
}
```

`DeathProtectionRoute.java` is also a sealed interface with nested route records:

```java
public sealed interface DeathProtectionRoute {
    enum Destination { MAIN_HAND, OFF_HAND }
    record AlreadyInHand(Destination destination) implements DeathProtectionRoute {}
    record HotbarSelect(int hotbarIndex) implements DeathProtectionRoute {}
    record ContainerSwap(int sourceMenuSlot, int button, Destination destination) implements DeathProtectionRoute {}
}
```

---

### Task 1: Bootstrap exact Fabric 26.1.2, unit tests, and CI

**Files:** Create the root project files, `PredictiveSurvivalClient.java`, `ModConstants.java`, `BuildContractTest.java`, and `.github/workflows/predictive-survival-26-1-2-ci.yml`.

**Produces:** `ModConstants.MOD_ID == "predictive_survival"`; a client-only Fabric entrypoint; Java 25 unit-test/build workflow.

- [ ] **Step 1: Write the failing contract test**

```java
@Test void modIdIsStable() {
    assertEquals("predictive_survival", ModConstants.MOD_ID);
}
```

- [ ] **Step 2: Configure exact versions and confirm the test fails before the class exists**

```properties
minecraft_version=26.1.2
loader_version=0.19.3
loom_version=1.17-SNAPSHOT
fabric_api_version=0.155.2+26.1.2
junit_version=5.12.2
org.gradle.jvmargs=-Xmx2G
org.gradle.parallel=true
org.gradle.configuration-cache=false
```

`build.gradle` must include:

```groovy
plugins {
    id 'net.fabricmc.fabric-loom' version "${loom_version}"
}

dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
    testImplementation "net.fabricmc:fabric-loader-junit:${loader_version}"
    testImplementation "org.junit.jupiter:junit-jupiter:${junit_version}"
}

test { useJUnitPlatform() }
tasks.withType(JavaCompile).configureEach { options.release = 25 }
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
```

Run: `./gradlew test`. Expected: FAIL because `ModConstants` is absent.

- [ ] **Step 3: Add minimal production code**

```java
public final class ModConstants {
    public static final String MOD_ID = "predictive_survival";
    private ModConstants() {}
}
```

```java
public final class PredictiveSurvivalClient implements ClientModInitializer {
    @Override public void onInitializeClient() {}
}
```

- [ ] **Step 4: Verify and commit**

```bash
./gradlew clean test build
git add projects/predictive-survival-26-1-2 .github/workflows/predictive-survival-26-1-2-ci.yml
git commit -m "build: bootstrap predictive survival for 26.1.2"
```

CI uses Java 25 and runs `./gradlew clean test build` from the project directory.

---

### Task 2: Implement immutable simulation domain types

**Files:** Create all `core/` primitives through `PlayerSnapshot.java` and damage snapshot records through `HurtState.java` listed in the file map; create `DomainTypesTest.java`.

**Produces:** immutable values used by every simulator and predictor.

- [ ] **Step 1: Write invariant tests**

```java
@Test void damageRangeRejectsInvertedBounds() {
    assertThrows(IllegalArgumentException.class, () -> new DamageRange(8f, 4f));
}
@Test void subtractFloorsAtZero() {
    assertEquals(new DamageRange(0f, 3f), new DamageRange(2f, 5f).subtractFloorZero(2f));
}
@Test void tickWindowOverlapIncludesSharedBoundary() {
    assertTrue(new TickWindow(10, 12).overlaps(new TickWindow(12, 15)));
}
```

- [ ] **Step 2: Run and confirm failure**

`./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest`

- [ ] **Step 3: Implement validated records**

`DamageFlag` includes `BYPASSES_INVULNERABILITY`, `BYPASSES_COOLDOWN`, `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, `BYPASSES_ENCHANTMENTS`, `IS_FIRE`, `DAMAGES_HELMET`, and `IS_FREEZING`.

`PlayerSnapshot` carries health, absorption, player/ability invulnerability, dead/dying state, `DifficultySnapshot`, `MitigationSnapshot`, `StatusEffectsSnapshot`, `BlockingSnapshot`, `HurtState`, and `DeathProtectionSnapshot`.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add survival simulation domain model"
```

---

### Task 3: Implement vanilla preprocessing, blocking, and hurt-cooldown order

**Files:** Create `DamageStage.java`, `DamageTrace.java`, `DamageResult.java`, `VanillaDamageMath.java`, `DamageSimulator.java`; create `DamageSimulatorPreprocessingTest.java`.

**Produces:** `DamageSimulator#simulate(PlayerSnapshot, DamageSourceSnapshot)`.

- [ ] **Step 1: Write ordering tests**

```java
@Test void easyDifficultyUsesVanillaFormula() {
    DamageResult r = fixture().difficulty(EASY).raw(10f).simulate();
    assertEquals(6f, r.trace().after(DamageStage.DIFFICULTY), 0.0001f);
}
@Test void fireResistanceRejectsBeforeCooldown() {
    DamageResult r = fixture().fireResistance(true).flag(IS_FIRE).raw(8f).simulate();
    assertTrue(r.rejected());
}
@Test void largerHitDuringStrongCooldownAppliesOnlyExcess() {
    DamageResult r = fixture().hurt(new HurtState(DamageRange.exact(5f), 15, EXACT)).raw(12f).simulate();
    assertEquals(7f, r.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
    assertEquals(12f, r.after().hurtState().lastHurt().max(), 0.0001f);
}
@Test void fullyBlockedHitLeavesZeroLastHurtForFollowupComparison() {
    assertEquals(0f, fixture().fullBlock().raw(8f).simulate().after().hurtState().lastHurt().max(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

`./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest`

- [ ] **Step 3: Implement this exact stage order**

```text
player/gamerule invulnerability
ability invulnerability unless BYPASSES_INVULNERABILITY
dead/dying rejection
difficulty scaling
zero rejection
living invulnerability/dead check
Fire Resistance + IS_FIRE rejection
negative -> zero
blocking
freezing multiplier
DAMAGES_HELMET * 0.75 when helmet present
NaN/infinity sanitation
hurt cooldown / lastHurt
```

`DamageTrace` stores before/after values for every stage. A source flagged `BYPASSES_COOLDOWN` skips the strong-cooldown subtraction/rejection logic.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: model vanilla hurt preprocessing and cooldown"
```

---

### Task 4: Complete mitigation, armor durability, absorption, and death protection

**Files:** Create `ArmorPieceSnapshot.java`, `EffectInstanceSnapshot.java`; modify simulator/snapshots; create `DamageSimulatorMitigationTest.java` and `DeathProtectionTest.java`.

**Produces:** exact `actuallyHurt`-stage simulation and post-pop state.

- [ ] **Step 1: Write mitigation/pop tests**

```java
@Test void resistanceThreeReducesBySixtyPercent() {
    assertEquals(4f, fixture().resistanceAmplifier(2).postArmorDamage(10f)
        .simulate().trace().after(DamageStage.RESISTANCE), 0.0001f);
}
@Test void bypassEffectsSkipsResistanceAndProtection() {
    assertEquals(10f, fixture().resistanceAmplifier(4).protection(20).flag(BYPASSES_EFFECTS)
        .postArmorDamage(10f).simulate().trace().after(DamageStage.MAGIC), 0.0001f);
}
@Test void absorptionIsConsumedBeforeHealth() {
    DamageResult r = fixture().health(10f).absorption(4f).finalDamage(6f).simulate();
    assertEquals(0f, r.after().absorption(), 0.0001f);
    assertEquals(8f, r.after().health(), 0.0001f);
}
@Test void bothHandsCanProvideDeathProtection() {
    assertTrue(fixture().health(4f).mainHandProtection().finalDamage(8f).simulate().deathProtectionConsumed());
    assertTrue(fixture().health(4f).offHandProtection().finalDamage(8f).simulate().deathProtectionConsumed());
}
@Test void bypassInvulnerabilityPreventsPop() {
    DamageResult r = fixture().health(4f).offHandProtection().flag(BYPASSES_INVULNERABILITY).finalDamage(8f).simulate();
    assertFalse(r.deathProtectionConsumed());
    assertEquals(0f, r.after().health(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

`./gradlew test --tests 'dev.pixelied.survival.damage.*MitigationTest' --tests dev.pixelied.survival.damage.DeathProtectionTest`

- [ ] **Step 3: Implement source order**

Armor unless `BYPASSES_ARMOR`; skip the whole effect/enchantment stage for `BYPASSES_EFFECTS`; otherwise apply Resistance unless `BYPASSES_RESISTANCE`, then enchantment protection unless `BYPASSES_ENCHANTMENTS`; consume absorption before health; update armor durability exactly as source-confirmed so later hits see broken pieces; at health `<= 0`, check runtime-modeled death protection in both hands unless `BYPASSES_INVULNERABILITY`.

- [ ] **Step 4: Add the armor-break multi-hit regression, verify, commit**

```java
@Test void oneDurabilityArmorBreakRaisesSecondHitDamage() {
    TimelineResult r = fixture().oneDurabilityChestplate().twoIdenticalHits().simulateTimeline();
    assertTrue(r.eventResults().get(1).finalDamage() > r.eventResults().get(0).finalDamage());
}
```

```bash
./gradlew test
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: complete mitigation and death protection"
```

---

### Task 5: Track conservative server hurt state

**Files:** Create `ServerHurtStateTracker.java`; create `ServerHurtStateTrackerTest.java`.

**Produces:** high-confidence shadow state without trusting client post-mitigation `lastHurt`.

```java
public final class ServerHurtStateTracker {
    public HurtState current();
    public void tick(int elapsedServerTicks);
    public void recordPredictedApplied(float preArmorLastHurt, TickWindow appliedAt);
    public void recordObservedHealthDelta(float healthDelta, TickWindow observedAt);
    public void invalidate();
    public HurtState conservativeForLethalDecision();
}
```

- [ ] **Step 1: Write confidence tests**

```java
@Test void unexpectedHealthLossInvalidatesRawLastHurt() {
    ServerHurtStateTracker t = new ServerHurtStateTracker();
    t.recordPredictedApplied(12f, new TickWindow(50, 50));
    t.recordObservedHealthDelta(3f, new TickWindow(55, 55));
    assertEquals(UNKNOWN, t.current().confidence());
}
@Test void unknownStateCreditsNoIframeReduction() {
    ServerHurtStateTracker t = new ServerHurtStateTracker();
    t.invalidate();
    assertEquals(0f, t.conservativeForLethalDecision().lastHurt().max(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.damage.ServerHurtStateTrackerTest`
- [ ] **Step 3: Implement EXACT/MATCHED/BOUNDED/UNKNOWN transitions; never infer raw server `lastHurt` directly from `LocalPlayer.lastHurt`.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.damage.ServerHurtStateTrackerTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: track conservative server hurt state"
```

---

### Task 6: Simulate ordered and uncertain multi-hit timelines

**Files:** Create all `timeline/` files; create `ThreatTimelineSimulatorTest.java`.

```java
public record ThreatEvent(String id, ThreatKind kind, TickWindow impact,
                          DamageSourceSnapshot damage, Confidence confidence) {}
public record ThreatTimeline(java.util.List<ThreatEvent> events) {}
public final class ThreatTimelineSimulator {
    public TimelineResult simulate(PlayerSnapshot start, ThreatTimeline timeline);
}
```

`TimelineResult` stores worst-case final health/absorption, survived flag, consumed protection count, ordered event results, and the first lethal event.

- [ ] **Step 1: Write sequence tests**

```java
@Test void individuallySafeHitsCanKillAsSequence() {
    assertFalse(fixture().health(10f).hitsSpacedBeyondCooldown(6f, 6f).simulate().survived());
}
@Test void sameWindowUsesWorstMaterialOrder() {
    TimelineResult r = fixture().health(10f).sameWindowHits(4f, 12f).simulate();
    assertEquals(12f, r.eventResults().get(0).preMitigationRaw(), 0.0001f);
}
@Test void simulationContinuesAfterPop() {
    TimelineResult r = fixture().health(5f).protections(1).hitsSpacedBeyondCooldown(10f, 10f).simulate();
    assertEquals(1, r.consumedDeathProtectionCount());
    assertFalse(r.survived());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.timeline.ThreatTimelineSimulatorTest`
- [ ] **Step 3: Implement deterministic ordering and bounded overlapping-window permutations; cap permutations and choose a conservative fallback ordering when the cap is exceeded.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.timeline.*' --tests 'dev.pixelied.survival.damage.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: simulate multi-hit threat timelines"
```

---

### Task 7: Estimate server timing and action deadlines

**Files:** Create all `timing/` files; create `ServerTimingEstimatorTest.java`.

```java
public final class ServerTimingEstimator {
    public void observeRttMillis(int rttMillis);
    public void observeClientTickNanos(long nanos);
    public TimingSnapshot snapshot(long clientTick);
}
public record TimingSnapshot(long clientTick, double rttMs, double jitterMs,
                             TickWindow nextPacketProcessingWindow) {
    public boolean canCompleteBefore(long requiredServerTicks, TickWindow impact);
}
```

- [ ] **Step 1: Write deadline tests**

```java
@Test void shieldNeedsArrivalPlusFiveServerTicks() {
    TimingSnapshot s = fixture().arrivalWindow(102, 103).snapshot();
    assertFalse(s.canCompleteBefore(5, new TickWindow(106, 106)));
    assertTrue(s.canCompleteBefore(5, new TickWindow(109, 110)));
}
@Test void jitterWidensConservativeArrivalWindow() {
    assertTrue(fixture().jitter(60).snapshot().nextPacketProcessingWindow().latest()
        > fixture().jitter(5).snapshot().nextPacketProcessingWindow().latest());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.timing.ServerTimingEstimatorTest`
- [ ] **Step 3: Implement a short rolling RTT/jitter sample window; feasibility always uses the conservative latest packet-processing bound, never guessed exact one-way latency.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.timing.ServerTimingEstimatorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: estimate conservative server deadlines"
```

---

### Task 8: Adapt live Minecraft state into pure snapshots

**Files:** Create `MinecraftSnapshotFactory`, damage/blocking/equipment adapters, accessor mixin/config, and `MinecraftAdapterContractTest.java`.

```java
public final class MinecraftSnapshotFactory {
    public PlayerSnapshot capture(net.minecraft.client.player.LocalPlayer player);
}
public final class MinecraftDamageAdapter {
    public DamageSourceSnapshot snapshot(net.minecraft.world.damagesource.DamageSource source,
                                         float rawDamage,
                                         net.minecraft.client.player.LocalPlayer player);
}
```

- [ ] **Step 1: Write tag/blocking contract tests**

```java
@Test void bypassArmorMapsIntoSnapshotFlags() {
    assertTrue(fixture().sourceWith(BYPASSES_ARMOR).snapshot().flags().contains(BYPASSES_ARMOR));
}
@Test void shieldIsInactiveAtFourOfFiveRequiredTicks() {
    assertFalse(fixture().blockingElapsed(4).required(5).snapshot().active());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.damage.MinecraftAdapterContractTest`
- [ ] **Step 3: Regenerate/open 26.1.2 sources, re-check the source-audit methods, then map runtime tags/components/effects/enchantments. Keep mixins only for inaccessible state.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test compileClientJava
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: snapshot live minecraft survival state"
```

---

### Task 9: Implement emergency inventory transactions and death-protection routes

**Files:** Create all `inventory/` files; create both inventory tests.

**Produces:** fastest valid route among already-held, hotbar-select, selected-slot swap, or offhand swap while preserving active defenses.

- [ ] **Step 1: Write route/resync tests**

```java
@Test void hotbarProtectionUsesOnePacketSelectionRoute() {
    DeathProtectionRoute r = fixture().protectionInHotbar(5).selected(1).choose();
    assertInstanceOf(DeathProtectionRoute.HotbarSelect.class, r);
}
@Test void activeOffhandShieldPrefersMainhandDestination() {
    DeathProtectionRoute r = fixture().activeOffhandShield().protectionInInventory(17).choose();
    assertEquals(DeathProtectionRoute.Destination.MAIN_HAND,
        ((DeathProtectionRoute.ContainerSwap) r).destination());
}
@Test void staleStateIdWaitsForFullReconcileInsteadOfAssumingFailure() {
    EmergencyInventoryTransaction tx = fixture().sentTransaction().observeStateIdMismatch();
    assertEquals(EmergencyInventoryTransaction.State.AWAITING_RECONCILE, tx.state());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests 'dev.pixelied.survival.inventory.*'`
- [ ] **Step 3: Implement one-packet routes. Use carried-slot selection for hotbar; use vanilla `SWAP` with current menu mapping for inventory-to-selected-hotbar or offhand button `40`; never hard-code screen slot ids. Valid stale-state-id clicks are reconciled from the authoritative full-state update.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.inventory.*' --tests 'dev.pixelied.survival.timeline.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: route death protection inventory actions"
```

---

### Task 10: Define predictor contracts and bounded broad phase

**Files:** Create `WorldSnapshot`, `PredictionContext`, `ThreatPredictor`, `ThreatPredictorRegistry`; create `ThreatPredictorRegistryTest.java`.

```java
public interface ThreatPredictor {
    java.util.List<ThreatEvent> predict(PredictionContext context);
}
public final class ThreatPredictorRegistry {
    public java.util.List<ThreatEvent> predictAll(PredictionContext context);
}
```

- [ ] **Step 1: Write conservative merge test**

```java
@Test void duplicatePhysicalThreatsMergeToWiderBounds() {
    ThreatEvent merged = fixture().sameIdEvents(raw(8,10), raw(9,12)).predictAll().get(0);
    assertEquals(12f, merged.damage().rawDamage().max(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.ThreatPredictorRegistryTest`
- [ ] **Step 3: Implement stable physical-threat ids, horizon caps, spatial filtering, and conservative merging. `WorldSnapshot` contains only nearby entities/blocks needed by predictors; no world-wide scans.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.threat.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add bounded threat predictor framework"
```

---

### Task 11: Predict explosions and evaluate emergency cover

**Files:** Create `OcclusionView`, `CoverCandidate`, `ExplosionExposure`, `ExplosionPredictor`, `CoverCandidateEvaluator`; create explosion/cover tests.

```java
public interface OcclusionView {
    boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to);
    OcclusionView withCandidateBlock(CoverCandidate candidate);
}
public record CoverCandidate(Vec3Snapshot blockPos, String blockId, int sourceInventoryIndex) {}
public final class ExplosionExposure {
    public float seenPercent(AabbSnapshot target, Vec3Snapshot center, OcclusionView world);
    public float rawEntityDamage(float radius, double distance, float exposure);
}
```

- [ ] **Step 1: Write exposure/timing tests**

```java
@Test void solidCoverLowersExposureDamage() {
    assertTrue(fixture().coveredDamage() < fixture().openDamage());
}
@Test void coverCountsAtEntityDamagePhaseEvenIfDestroyedLater() {
    assertTrue(fixture().candidateBlockPresentBeforeEntityDamage().simulate().survived());
}
@Test void tntFuseProducesExactImpactTick() {
    assertEquals(new TickWindow(80, 80), fixture().tntFuse(80).event().impact());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests '*Explosion*' --tests '*CoverCandidate*'`
- [ ] **Step 3: Mirror 26.1.2 `ServerExplosion` exposure/damage ordering. Cover primed/minecart TNT, creepers, end crystals, bad-respawn bed/anchor, fireworks, and other observable explosion families. No-fuse crystal/anchor/bed threats may emit `POTENTIAL` immediate windows when an opponent can legally trigger them inside the horizon.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests '*Explosion*' --tests '*CoverCandidate*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict explosions and emergency cover"
```

---

### Task 12: Predict projectile families with discrete motion and swept collision

**Files:** Create `ProjectileStep`, motion-model files, and `ProjectilePredictor`; create `ProjectilePredictorTest.java`.

```java
public record ProjectileStep(Vec3Snapshot position, Vec3Snapshot velocity, long tick) {}
public interface ProjectileMotionModel {
    ProjectileStep step(ProjectileStep current);
}
```

- [ ] **Step 1: Write collision/range tests**

```java
@Test void arrowReportsFirstSweptPlayerIntersection() {
    assertEquals(7, fixture().arrowTowardPlayer().predict().get(0).impact().earliest());
}
@Test void earlierWallCollisionRemovesPlayerThreat() {
    assertTrue(fixture().arrowWithWallAtTick4().predict().isEmpty());
}
@Test void unknownCriticalStateWidensDamageRange() {
    ThreatEvent e = fixture().arrowUnknownCritical().predict().get(0);
    assertTrue(e.damage().rawDamage().max() > e.damage().rawDamage().min());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.ProjectilePredictorTest`
- [ ] **Step 3: Implement exact family motion from 26.1.2 source for arrows, tridents, mob ballistic projectiles, llama spit where applicable, fireballs, wither skulls, wind charges, harmful thrown potions, and fireworks. Simulate source-defined gravity/drag/acceleration and swept block/entity collision.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ProjectilePredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict projectile impacts by vanilla family"
```

---

### Task 13: Predict fall, void, wall-collision, and falling-object damage

**Files:** Create `LandingPrediction`, `FallLandingSolver`, `FallPredictor`; create `FallPredictorTest.java`.

```java
public record LandingPrediction(Vec3Snapshot position, long tick, String surfaceBlockId,
                                DamageRange rawFallDamage) {}
```

- [ ] **Step 1: Write landing tests**

```java
@Test void slimeLandingIsZeroDamage() {
    assertEquals(0f, fixture().ontoSlime().event().damage().rawDamage().max(), 0.0001f);
}
@Test void hayUsesPointTwoFallMultiplier() {
    assertEquals(fixture().stoneDamage() * 0.2f, fixture().hayDamage(), 0.01f);
}
@Test void voidThreatBypassesDeathProtection() {
    assertTrue(fixture().towardVoid().event().damage().flags().contains(BYPASSES_INVULNERABILITY));
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.FallPredictorTest`
- [ ] **Step 3: Use future AABB/collision geometry and velocity rather than `fallDistance` alone. Cover normal landings, stalagmites, elytra `FLY_INTO_WALL`, observable falling objects, and void trajectories.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.FallPredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict lethal fall and movement damage"
```

---

### Task 14: Predict periodic environmental and status damage

**Files:** Create `HazardClockSnapshot`, `PeriodicHazardPredictor`; create `PeriodicHazardPredictorTest.java`.

```java
public record HazardClockSnapshot(String hazardId, int ticksUntilNextDamage, int cadenceTicks) {}
```

- [ ] **Step 1: Write cadence/floor tests**

```java
@Test void fireUsesActualNextDamageTick() {
    assertEquals(3, fixture().onFireNextTickIn(3).predict().get(0).impact().earliest());
}
@Test void poisonDoesNotPredictForbiddenLethalFloor() {
    TimelineResult r = fixture().health(1f).poisonTick().simulate();
    assertTrue(r.survived());
    assertEquals(1f, r.finalHealth(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.PeriodicHazardPredictorTest`
- [ ] **Step 3: Cover fire/campfire/lava/hot-floor, cactus/berry bush, suffocation, cramming, drowning, starvation, freezing, world border, dry-out where relevant, area-effect hazards, Wither, poison/magic effect ticks, and observable lightning. Predictor computes deadlines; `DamageSimulator` remains responsible for mitigation/rejection.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.PeriodicHazardPredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict periodic environmental damage"
```

---

### Task 15: Predict potential melee, mace, spear, and mob attacks

**Files:** Create `MaceThreatModel`, `SpearThreatModel`, `MeleePredictor`; create `MeleePredictorTest.java`.

- [ ] **Step 1: Write capability tests**

```java
@Test void unreachableAttackerEmitsNoImmediateHit() {
    assertTrue(fixture().distance(9).legalReach(3).predict().isEmpty());
}
@Test void fallingMaceAttackerEmitsBoundedPotentialDamage() {
    ThreatEvent e = fixture().mace().fallDistanceRange(8,12).inReachSoon().predict().get(0);
    assertEquals(POTENTIAL, e.confidence());
    assertTrue(e.damage().rawDamage().max() > e.damage().rawDamage().min());
}
@Test void shieldDisableCapabilityIsCarriedOnThreat() {
    assertTrue(fixture().shieldDisablingWeapon().event().canDisableBlocking());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.MeleePredictorTest`
- [ ] **Step 3: Re-check 26.1.2 `MaceItem`, spear implementation, reach/cooldown, and shield-disable behavior. Predict legal attack capability, not an opponent's unknowable future click. Use visible equipment/effects/enchantments and relative motion.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.MeleePredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict melee mace and spear threats"
```

---

### Task 16: Implement bounded planner and Safe mode

**Files:** Create `SafetyMode`, `SurvivalAction`, `ActionFeasibility`, `ActionSimulation`, `SurvivalPlan`, `SurvivalPlanner`; create `SurvivalPlannerSafeModeTest.java`.

```java
public final class SurvivalPlanner {
    public SurvivalPlan plan(PredictionContext context, ThreatTimeline timeline,
                             java.util.List<SurvivalAction> candidates, SafetyMode mode);
}
```

- [ ] **Step 1: Write dominance tests**

```java
@Test void safeModeChoosesProtectionWhenShieldDeadlineCannotBeMet() {
    assertInstanceOf(SurvivalAction.EquipDeathProtection.class,
        fixture().lethalCrystalIn(3).protectionFeasible().shieldNeeds5Ticks().safe().plan().action());
}
@Test void safeModeUsesAlreadyActiveGuaranteedBlockWithoutWastingProtection() {
    assertInstanceOf(SurvivalAction.RaiseShield.class,
        fixture().blockableLethalHit().activeShield().protectionAvailable().safe().plan().action());
}
@Test void bypassInvulnerabilityNeverScoresProtectionAsSurvival() {
    assertFalse(fixture().voidThreat().protectionAvailable().protectionSimulation().result().survived());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.planner.SurvivalPlannerSafeModeTest`
- [ ] **Step 3: Hard constraints first: server deadline, legality, required authoritative state, worst-case survival. Secondary ranking: reliability, remaining health, consumable cost, disruption. Safe mode forbids deliberate damage manipulation.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.timeline.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: plan conservative survival actions"
```

---

### Task 17: Execute death protection and shield state machines

**Files:** Create `ExecutionStatus`, `ExecutionContext`, `ActionExecutor`, `DeathProtectionExecutor`, `ShieldExecutor`; create executor tests.

```java
public enum ExecutionStatus { STARTED, WAITING_FOR_SERVER, WARMING_UP, CONFIRMED, CONTRADICTED, MISSED_DEADLINE, CANCELLED }
public record ExecutionContext(net.minecraft.client.Minecraft minecraft,
                               TimingSnapshot timing, long clientTick) {}
public interface ActionExecutor<A extends SurvivalAction> {
    ExecutionStatus tick(A action, ExecutionContext context);
    void cancel(ExecutionContext context);
}
```

- [ ] **Step 1: Write authoritative-state tests**

```java
@Test void hotbarRouteConfirmsOnlyAfterAuthoritativeHeldSlotMatches() {
    assertEquals(WAITING_FOR_SERVER, fixture().hotbarRoute(5).firstTick());
    assertEquals(CONFIRMED, fixture().hotbarRoute(5).serverHeldSlot(5).nextTick());
}
@Test void shieldIsNotReadyAtFourUseTicks() {
    assertEquals(WARMING_UP, fixture().shieldUseTicks(4).tick());
}
@Test void shieldIsReadyAtFiveUseTicks() {
    assertEquals(CONFIRMED, fixture().shieldUseTicks(5).tick());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests 'dev.pixelied.survival.action.*ExecutorTest'`
- [ ] **Step 3: Use source-confirmed carried-slot/menu `SWAP` and item-use flows. Never report success from local inventory mutation alone. Preserve active offhand blocking by choosing a mainhand protection route when the plan requires both.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.action.*' --tests 'dev.pixelied.survival.inventory.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute protection and shield states"
```

---

### Task 18: Generate and execute non-totem survival actions

**Files:** Create candidate generators and movement/cover/equipment/effect/fall-rescue executors; create `NonTotemActionTest.java` and `NonTotemExecutorTest.java`.

- [ ] **Step 1: Write representative feasibility tests**

```java
@Test void coverMustReduceWorstCaseAndMeetDeadline() {
    assertInstanceOf(SurvivalAction.PlaceCover.class,
        fixture().lethalCrystal().reachableObsidianCover().bestAction());
}
@Test void chestplateSwapCanMakeExplosionSurvivable() {
    assertTrue(fixture().elytraEquipped().chestplateAvailable().lethalExplosion().simulateSwap().result().survived());
}
@Test void thirtyTwoTickFoodUseIsRejectedForThreeTickThreat() {
    assertFalse(fixture().goldenAppleUseTicks(32).threatIn(3).feasibility().feasible());
}
@Test void pearlRescueIncludesFiveRawPearlDamage() {
    assertEquals(5f, fixture().lethalFall().pearlRescue().simulate().eventRaw("ender_pearl"), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.planner.NonTotemActionTest --tests dev.pixelied.survival.action.NonTotemExecutorTest`
- [ ] **Step 3: Implement physically/legal candidates only. Movement must move the accepted server AABB; cover must be reachable/legal; equipment counts real packet operations; effects include use/projectile duration; fall rescues cover water, legal landing-block use, elytra, wind charge, valid mace smash, and pearl relocation.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.action.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute non-totem survival strategies"
```

---

### Task 19: Add Balanced and strictly gated Experimental hurt-cooldown policy

**Files:** Create `HurtCooldownCandidate`, `HurtCooldownStrategy`; modify planner/mode; create policy tests.

```java
public final class HurtCooldownStrategy {
    public java.util.Optional<ActionSimulation> evaluate(HurtCooldownCandidate candidate,
                                                         PredictionContext context,
                                                         ThreatTimeline timeline);
}
```

- [ ] **Step 1: Write anti-folklore tests**

```java
@Test void oneDamagePrecursorDoesNotCancelTwentyDamageHit() {
    assertEquals(19f, fixture().precursor(1f).incoming(20f).simulate().incomingAfterCooldown(), 0.0001f);
}
@Test void unknownServerLastHurtRejectsIntentionalDamage() {
    assertTrue(fixture().unknownLastHurt().evaluate().isEmpty());
}
@Test void experimentalCandidateMustBeatNoActionWorstCase() {
    ActionSimulation candidate = fixture().runtimeValidatedCandidate().evaluate().orElseThrow();
    assertTrue(candidate.result().finalHealth() > fixture().noAction().finalHealth());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.planner.HurtCooldownStrategyTest --tests dev.pixelied.survival.planner.BalancedPolicyTest`
- [ ] **Step 3: Balanced may preserve a totem only when a proven non-totem action is conservatively safe. Experimental deliberate damage requires high-confidence server `lastHurt`, controllable timing, worst-case survival, material advantage, and `runtimeValidated == true`; every tactic starts false.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: gate balanced and experimental survival policy"
```

---

### Task 20: Integrate SurvivalEngine and bounded diagnostics

**Files:** Create `SurvivalEngine`, `DecisionRecord`, `DecisionHistory`, `SurvivalDebugHud`; wire `PredictiveSurvivalClient`; create engine/history tests.

```java
public final class SurvivalEngine {
    public void tick();
    public java.util.Optional<SurvivalPlan> currentPlan();
}
```

- [ ] **Step 1: Write replanning/history tests**

```java
@Test void engineEscalatesWhenShieldDeadlineIsMissed() {
    var f = fixture().lethalThreat().initialShieldPlan();
    f.missShieldDeadlineAndTick();
    assertInstanceOf(SurvivalAction.EquipDeathProtection.class, f.engine().currentPlan().orElseThrow().action());
}
@Test void decisionHistoryIsBounded() {
    DecisionHistory h = new DecisionHistory(128);
    for (int i = 0; i < 300; i++) h.add(fixture().record(i));
    assertEquals(128, h.snapshot().size());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.core.SurvivalEngineTest --tests dev.pixelied.survival.debug.DecisionHistoryTest`
- [ ] **Step 3: Wire tick flow exactly:** `timing -> snapshots -> hurt tracker -> predictors -> timeline -> no-action result -> candidate generation -> planner -> executor -> telemetry`. HUD shows impact windows, raw/final ranges, hurt confidence, chosen action/deadline, rejected reason, inventory transaction state, and predicted-vs-observed result; normal chat/disk logging remains off.
- [ ] **Step 4: Verify, smoke-run, commit**

```bash
./gradlew test
./gradlew runClient
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: integrate predictive survival engine"
```

The smoke run succeeds when the client reaches a world without initialization/mixin errors; do not claim gameplay correctness from the smoke run.

---

### Task 21: Add exact-runtime 26.1.2 client GameTests and final acceptance gate

**Files:** Modify `build.gradle`; create all `src/gametest/` files/resources and `VALIDATION.md`; update CI only for ordinary build/unit checks unless client GameTests prove stable enough to gate CI.

**Produces:** controlled integrated-server scenarios that compare simulator predictions with actual 26.1.2 health/absorption.

- [ ] **Step 1: Configure Fabric Loom's dedicated gametest source set explicitly**

```groovy
fabricApi {
    configureTests {
        createSourceSet = true
        modId = "predictive-survival-gametest"
        enableGameTests = true
        enableClientGameTests = true
        eula = true
    }
}
```

Create `src/gametest/resources/fabric.mod.json` with mod id `predictive-survival-gametest` and a client game-test entrypoint. The validation class implements Fabric API's client game-test interface and creates a singleplayer context so damage is processed by an integrated 26.1.2 server.

- [ ] **Step 2: Add explicit result types and first runtime scenarios**

```java
public enum ValidationStatus { SOURCE_CONFIRMED, RUNTIME_CONFIRMED, EXPERIMENTAL }
public record ValidationResult(String id, float predictedHealth, float actualHealth,
                               ValidationStatus status, float tolerance) {
    public boolean passes() { return Math.abs(predictedHealth - actualHealth) <= tolerance; }
}
```

First scenarios: normal melee; armor + Resistance + Protection; shield at 4 vs 5 use ticks; smaller/equal/larger hurt-cooldown follow-ups; one death-protection pop; one TNT/crystal exposure case with and without cover.

- [ ] **Step 3: Run unit tests, then exact-runtime client GameTests**

```bash
./gradlew clean test
./gradlew runClientGameTest
```

Expand runtime scenarios to arrows/tridents; bed/anchor/crystal/TNT cover; fall/wind-charge/mace/pearl; lava/fire/drowning/freezing/Wither-like ticks; repeated threats after a pop; and every Experimental hurt-cooldown candidate. Promote a tactic to `RUNTIME_CONFIRMED` only when actual server state agrees with the declared prediction tolerance.

- [ ] **Step 4: Run final acceptance checks and inspect packaging**

```bash
./gradlew clean test build
cd ../..
python -m unittest discover -s tests -v
python agentctl.py validate
```

Inspect the production jar and confirm no `dev/pixelied/survival/validation/` classes are present. `VALIDATION.md` lists supported threat families, runtime-confirmed cases, tolerances, unobservable instant damage, disabled experimental tactics, and every known discrepancy.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2 .github/workflows/predictive-survival-26-1-2-ci.yml
git commit -m "test: validate survival engine against 26.1.2 runtime"
```

---

## Final Verification Checklist

Before implementation is declared complete, run in one fresh checkout:

```bash
cd projects/predictive-survival-26-1-2
./gradlew clean test build
./gradlew runClientGameTest
cd ../..
python -m unittest discover -s tests -v
python agentctl.py validate
```

Then verify behavior, not only compilation:

- A lethal observable threat places a valid death-protection item in either server-recognized hand before the conservative deadline when available.
- Mainhand routing can preserve an already-active offhand shield when that is the safer combined defense.
- Shield is never credited before its five server-tick warmup plus packet-arrival margin.
- Unknown server `lastHurt` never grants hurt-cooldown reduction.
- Small precursor damage never falsely cancels a larger hit.
- `BYPASSES_INVULNERABILITY` never scores death protection as successful.
- Explosion cover is evaluated at entity-damage time before block destruction.
- Multi-hit simulation continues after a pop and can still identify a later lethal threat.
- Valid stale-state-id inventory clicks reconcile from authoritative full resync instead of being assumed rejected.
- No executor claims success solely from local client state.
- Every client-observable vanilla damage family is predicted or explicitly documented as lacking a useful precursor.
- Experimental deliberate-damage tactics remain disabled unless exact-runtime validation proves them beneficial and server-valid.
- Debug history is bounded and optional.
- The production jar contains no gametest validation classes.
