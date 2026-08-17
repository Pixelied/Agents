# Predictive Survival 26.1.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Minecraft Java 26.1.2 Fabric client mod that predicts observable lethal damage with vanilla-faithful semantics and executes the safest server-valid survival action before the damage is processed.

**Architecture:** A deterministic damage/timeline core consumes immutable snapshots. Threat-specific predictors emit bounded future events, a planner compares a small bounded action set against the same timeline, and client executors perform only server-valid actions before conservative deadlines. Minecraft/Fabric adapters stay at the edge so core math and policy are unit-testable.

**Tech Stack:** Java 25; Minecraft Java 26.1.2; Fabric Loader 0.19.3; Fabric Loom 1.17-SNAPSHOT with plugin `net.fabricmc.fabric-loom`; Fabric API 0.155.2+26.1.2; Fabric Loader JUnit; JUnit Jupiter 5.12.2; Gradle; GitHub Actions.

## Global Constraints

- Target exactly Minecraft Java Edition `26.1.2` and Java `25`.
- Use Fabric Loader `0.19.3`, Fabric Loom `1.17-SNAPSHOT`, Fabric API `0.155.2+26.1.2`, and plugin id `net.fabricmc.fabric-loom`.
- Do not add Yarn mappings or the legacy remapping Loom plugin.
- Production code is client-only. Game-test helpers live only in Loom's `gametest` source set and must not be packaged into the production jar.
- Treat exact 26.1.2 Minecraft source as authoritative; regenerate sources through Loom and re-check source before changing formulas.
- Runtime damage tags, item components, effects, enchantments, collision rules, and menu state are authoritative where exposed; do not replace them with item-name heuristics.
- Never treat client-only desync, ghost inventory state, impossible movement, or packet flooding as protection.
- Unknown raw damage, unknown server `lastHurt`, uncertain event order, missed deadlines, or contradictory authoritative state must fail conservatively.
- Deliberate hurt-cooldown manipulation remains disabled outside Experimental mode until a tactic has deterministic tests and exact-runtime evidence.
- User settings stay small: safety mode, restore prior hand/item, automatic movement/evasion, block placement/clutches, debug overlay/logging.
- Every implementation task follows red-green-refactor discipline and ends with a focused commit.

## Test Fixture Convention

Test snippets use helpers such as `fixture()` only as private builders inside that test class. Step 1 of each task includes creating the local builder state needed by the shown assertions; these helper names are not production interfaces.

---

## Locked File Structure

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
    config/SurvivalConfig.java
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
      EngineLimits.java
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
      TimelineEventResult.java
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
      ReactiveDamagePredictor.java
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
    core/SurvivalEngineTest.java
    config/SurvivalConfigTest.java
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
    threat/ReactiveDamagePredictorTest.java
    inventory/DeathProtectionRoutePlannerTest.java
    inventory/EmergencyInventoryTransactionTest.java
    planner/SurvivalPlannerSafeModeTest.java
    planner/NonTotemActionTest.java
    planner/HurtCooldownStrategyTest.java
    planner/BalancedPolicyTest.java
    action/DeathProtectionExecutorTest.java
    action/ShieldExecutorTest.java
    action/NonTotemExecutorTest.java
    debug/DecisionHistoryTest.java
  src/gametest/java/dev/pixelied/survival/validation/
    ValidationStatus.java
    ValidationResult.java
    SurvivalValidationClientGameTest.java
    DamageValidationScenarios.java
  src/gametest/resources/fabric.mod.json
.github/workflows/predictive-survival-26-1-2-ci.yml
```

## Locked Shared Interfaces

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

```java
public record DamageSourceSnapshot(
    DamageRange rawDamage,
    java.util.Set<DamageFlag> flags,
    boolean scalesWithDifficulty,
    float freezingMultiplier,
    boolean piercingProjectile,
    java.util.Optional<Vec3Snapshot> sourcePosition,
    String sourceKey
) {}
```

```java
public final class DamageTrace {
    public float before(DamageStage stage);
    public float after(DamageStage stage);
}
public record DamageResult(PlayerSnapshot after, DamageTrace trace,
                           boolean rejected, boolean deathProtectionConsumed) {}
```

```java
public record ThreatEvent(
    String id,
    ThreatKind kind,
    TickWindow impact,
    DamageSourceSnapshot damage,
    Confidence confidence,
    java.util.Optional<Vec3Snapshot> sourcePosition,
    java.util.Optional<Vec3Snapshot> impactPosition,
    boolean avoidable,
    boolean blockable,
    boolean relocatable,
    boolean canDisableBlocking
) {}
public record TimelineEventResult(ThreatEvent event, float preMitigationRaw,
                                  float finalDamage, DamageResult damageResult) {}
public record TimelineResult(
    java.util.List<TimelineEventResult> eventResults,
    float finalHealth,
    float finalAbsorption,
    boolean survived,
    int consumedDeathProtectionCount,
    java.util.Optional<String> firstLethalEventId
) {
    public TimelineEventResult eventResult(String id);
}
```

```java
public sealed interface SurvivalAction {
    enum FallRescueKind { WATER, LANDING_BLOCK, ELYTRA, WIND_CHARGE, MACE_SMASH, ENDER_PEARL }
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

```java
public sealed interface DeathProtectionRoute {
    enum Destination { MAIN_HAND, OFF_HAND }
    record AlreadyInHand(Destination destination) implements DeathProtectionRoute {}
    record HotbarSelect(int hotbarIndex) implements DeathProtectionRoute {}
    record ContainerSwap(int sourceMenuSlot, int button, Destination destination) implements DeathProtectionRoute {}
}
```

```java
public record ActionFeasibility(boolean feasible, String reason, Deadline deadline) {}
public record ActionSimulation(SurvivalAction action, ActionFeasibility feasibility, TimelineResult result) {}
public record SurvivalPlan(SurvivalAction action, ActionSimulation simulation, String reason, int evaluatedCandidateCount) {}
```

---

### Task 1: Bootstrap exact Fabric 26.1.2, split client sources, unit tests, and CI

**Files:** Create project root files, `PredictiveSurvivalClient.java`, `ModConstants.java`, `BuildContractTest.java`, and CI workflow.

- [ ] **Step 1: Write the failing mod-id contract test**

```java
@Test void modIdIsStable() { assertEquals("predictive_survival", ModConstants.MOD_ID); }
```

- [ ] **Step 2: Configure exact versions and split sources**

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

```groovy
plugins { id 'net.fabricmc.fabric-loom' version "${loom_version}" }
loom {
    splitEnvironmentSourceSets()
    mods {
        predictive_survival {
            sourceSet sourceSets.main
            sourceSet sourceSets.client
        }
    }
}
dependencies {
    minecraft "com.mojang:minecraft:${minecraft_version}"
    implementation "net.fabricmc:fabric-loader:${loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${fabric_api_version}"
    testImplementation "net.fabricmc:fabric-loader-junit:${loader_version}"
    testImplementation "org.junit.jupiter:junit-jupiter:${junit_version}"
}
sourceSets.test.compileClasspath += sourceSets.client.output
sourceSets.test.runtimeClasspath += sourceSets.client.output
test { useJUnitPlatform() }
tasks.withType(JavaCompile).configureEach { options.release = 25 }
java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}
```

Run `./gradlew test`. Expected: FAIL before `ModConstants` exists.

- [ ] **Step 3: Add minimal client code and metadata**

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

`fabric.mod.json` sets `"environment": "client"` and registers the `client` entrypoint.

- [ ] **Step 4: Verify and commit**

```bash
./gradlew clean test build
git add projects/predictive-survival-26-1-2 .github/workflows/predictive-survival-26-1-2-ci.yml
git commit -m "build: bootstrap predictive survival for 26.1.2"
```

---

### Task 2: Implement immutable simulation domain types

**Files:** Create core primitives, damage snapshot records, and `DomainTypesTest.java`.

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

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest`
- [ ] **Step 3: Implement validated records.** `DamageFlag` includes `BYPASSES_INVULNERABILITY`, `BYPASSES_COOLDOWN`, `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, `BYPASSES_ENCHANTMENTS`, `IS_FIRE`, `DAMAGES_HELMET`, and `IS_FREEZING`. `PlayerSnapshot` carries health, absorption, invulnerability/dead state, difficulty, mitigation/effects/blocking, hurt state, death protection, AABB, position, velocity, and equipment snapshot data.
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add survival simulation domain model"
```

---

### Task 3: Implement vanilla preprocessing, blocking, and hurt-cooldown order

**Files:** Create `DamageStage`, `DamageTrace`, `DamageResult`, `VanillaDamageMath`, `DamageSimulator`, and preprocessing tests.

- [ ] **Step 1: Write exact ordering tests**

```java
@Test void easyDifficultyUsesVanillaFormula() {
    DamageResult r = fixture().difficulty(EASY).raw(10f).simulate();
    assertEquals(6f, r.trace().after(DamageStage.DIFFICULTY), 0.0001f);
}
@Test void fireResistanceRejectsBeforeCooldown() {
    assertTrue(fixture().fireResistance(true).flag(IS_FIRE).raw(8f).simulate().rejected());
}
@Test void largerHitDuringStrongCooldownAppliesOnlyExcess() {
    DamageResult r = fixture().hurt(new HurtState(DamageRange.exact(5f), 15, EXACT)).raw(12f).simulate();
    assertEquals(7f, r.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
    assertEquals(12f, r.after().hurtState().lastHurt().max(), 0.0001f);
}
@Test void fullyBlockedHitLeavesZeroLastHurt() {
    assertEquals(0f, fixture().fullBlock().raw(8f).simulate().after().hurtState().lastHurt().max(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest`
- [ ] **Step 3: Implement exact order:** player/gamerule invulnerability -> ability invulnerability unless bypassed -> dead/dying -> difficulty -> zero rejection -> living invulnerability/dead -> Fire Resistance fire rejection -> clamp negative -> blocking -> freezing multiplier -> helmet reduction -> finite-value sanitation -> hurt cooldown/`lastHurt`. A synthetic `BYPASSES_COOLDOWN` source skips strong-cooldown rejection/subtraction.
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: model vanilla hurt preprocessing and cooldown"
```

---

### Task 4: Complete mitigation, armor durability, absorption, and death protection

**Files:** Add armor/effect snapshots; modify simulator; create mitigation/death-protection tests.

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
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests 'dev.pixelied.survival.damage.*MitigationTest' --tests dev.pixelied.survival.damage.DeathProtectionTest`
- [ ] **Step 3: Implement source order:** armor unless `BYPASSES_ARMOR`; skip effect/enchantment stage for `BYPASSES_EFFECTS`; otherwise Resistance unless `BYPASSES_RESISTANCE`, then enchantment protection unless `BYPASSES_ENCHANTMENTS`; absorption before health; source-confirmed armor durability changes; both-hand death protection at health `<= 0` unless `BYPASSES_INVULNERABILITY`.
- [ ] **Step 4: Add sequential armor-break regression, verify, commit**

```java
@Test void oneDurabilityArmorBreakRaisesSecondHitDamage() {
    DamageSimulator simulator = fixture().simulator();
    DamageSourceSnapshot hit = fixture().exactRawSource(8f);
    DamageResult first = simulator.simulate(fixture().oneDurabilityChestplatePlayer(), hit);
    DamageResult second = simulator.simulate(first.after(), hit);
    assertTrue(second.trace().after(DamageStage.HEALTH_DAMAGE)
        > first.trace().after(DamageStage.HEALTH_DAMAGE));
}
```

```bash
./gradlew test
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: complete mitigation and death protection"
```

---

### Task 5: Track conservative server hurt state

**Files:** Create `ServerHurtStateTracker.java` and test.

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
- [ ] **Step 3: Implement EXACT/MATCHED/BOUNDED/UNKNOWN transitions. Never infer server raw `lastHurt` directly from local post-mitigation health delta or `LocalPlayer.lastHurt`.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.damage.ServerHurtStateTrackerTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: track conservative server hurt state"
```

---

### Task 6: Simulate ordered and uncertain multi-hit timelines

**Files:** Create all timeline files and `ThreatTimelineSimulatorTest.java`.

```java
public record ThreatTimeline(java.util.List<ThreatEvent> events) {}
public final class ThreatTimelineSimulator {
    public TimelineResult simulate(PlayerSnapshot start, ThreatTimeline timeline);
}
```

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
- [ ] **Step 3: Implement deterministic order plus bounded permutations only for overlapping windows whose order changes material state; when the cap is exceeded, use the most damaging conservative order. Carry post-hit hurt state, armor durability, absorption, effects, and death-protection consumption into following events.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.timeline.*' --tests 'dev.pixelied.survival.damage.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: simulate multi-hit threat timelines"
```

---

### Task 7: Estimate server timing and action deadlines

**Files:** Create all timing files and test.

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
- [ ] **Step 3: Implement a short rolling RTT/jitter sample window. Feasibility always uses the conservative latest packet-processing bound, never guessed exact one-way latency.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.timing.ServerTimingEstimatorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: estimate conservative server deadlines"
```

---

### Task 8: Adapt live Minecraft 26.1.2 state into pure snapshots

**Files:** Create snapshot/damage/blocking/equipment adapters, accessor mixin/config, and `MinecraftAdapterContractTest.java`.

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
- [ ] **Step 3: Regenerate/open exact 26.1.2 sources and re-check every source-audit method before mapping runtime tags/components/effects/enchantments. Keep mixins only for state not obtainable through public client APIs.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test compileClientJava
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: snapshot live minecraft survival state"
```

---

### Task 9: Implement emergency inventory transactions and death-protection routes

**Files:** Create all inventory files and both inventory tests.

`EmergencyInventoryTransaction.State` is `PLANNED`, `SENT`, `AWAITING_RECONCILE`, `CONFIRMED`, `CONTRADICTED`, `CONSUMED`, `RESTORING`, or `DONE`.

- [ ] **Step 1: Write routing, resync, restoration, and consumption tests**

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
@Test void staleStateIdWaitsForFullReconcile() {
    EmergencyInventoryTransaction tx = fixture().sentTransaction().observeStateIdMismatch();
    assertEquals(EmergencyInventoryTransaction.State.AWAITING_RECONCILE, tx.state());
}
@Test void restorationWaitsUntilThreatGraceWindowIsClear() {
    assertEquals(EmergencyInventoryTransaction.State.CONFIRMED,
        fixture().confirmedTransaction().lethalThreatStillPending().attemptRestore().state());
}
@Test void consumedProtectionInvalidatesSavedRestoreStack() {
    assertFalse(fixture().confirmedTransaction().markConsumed().canRestoreOriginalDestinationStack());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests 'dev.pixelied.survival.inventory.*'`
- [ ] **Step 3: Implement routes:** already in hand; carried-slot hotbar selection; one vanilla `SWAP` to selected hotbar or offhand button `40`. Derive current menu slot mapping; never hard-code screen slot ids. Valid stale-state-id clicks are applied then fully resynchronized; wrong container/menu/slot remains untrusted until authoritative state proves the move.
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.inventory.*' --tests 'dev.pixelied.survival.timeline.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: route death protection inventory actions"
```

---

### Task 10: Define predictor contracts and bounded broad phase

**Files:** Create `WorldSnapshot`, `PredictionContext`, `EngineLimits`, `ThreatPredictor`, `ThreatPredictorRegistry`, and test.

```java
public interface ThreatPredictor {
    java.util.List<ThreatEvent> predict(PredictionContext context);
}
public record EngineLimits(int maxThreats, int maxPlannerCandidates,
                           int maxProjectileHorizonTicks, int maxDecisionHistory) {
    public static EngineLimits defaults() { return new EngineLimits(128, 32, 80, 128); }
}
```

- [ ] **Step 1: Write merge and bound tests**

```java
@Test void duplicatePhysicalThreatsMergeToWiderBounds() {
    ThreatEvent merged = fixture().sameIdEvents(raw(8,10), raw(9,12)).predictAll().get(0);
    assertEquals(12f, merged.damage().rawDamage().max(), 0.0001f);
}
@Test void registryNeverReturnsMoreThanConfiguredThreatCap() {
    assertEquals(8, fixture().limits(new EngineLimits(8,32,80,128)).twentyThreats().predictAll().size());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.ThreatPredictorRegistryTest`
- [ ] **Step 3: Implement stable threat ids, horizon caps, spatial filtering, and conservative merging. `WorldSnapshot` contains only nearby entities/blocks needed by predictors; no world-wide scans.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.threat.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add bounded threat predictor framework"
```

---

### Task 11: Predict explosions and evaluate emergency cover

**Files:** Create `OcclusionView`, `CoverCandidate`, `ExplosionExposure`, `ExplosionPredictor`, `CoverCandidateEvaluator`, and tests.

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
@Test void solidCoverLowersExposureDamage() { assertTrue(fixture().coveredDamage() < fixture().openDamage()); }
@Test void coverCountsAtEntityDamagePhaseEvenIfDestroyedLater() {
    assertTrue(fixture().candidateBlockPresentBeforeEntityDamage().simulate().survived());
}
@Test void tntFuseProducesExactImpactTick() {
    assertEquals(new TickWindow(80,80), fixture().tntFuse(80).event().impact());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests '*Explosion*' --tests '*CoverCandidate*'`
- [ ] **Step 3: Mirror 26.1.2 `ServerExplosion` exposure/damage ordering. Cover primed/minecart TNT, creepers, end crystals, bad-respawn bed/anchor, fireworks, and other observable explosion families. No-fuse crystal/anchor/bed threats emit `POTENTIAL` immediate windows only when triggering is legal inside the horizon.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests '*Explosion*' --tests '*CoverCandidate*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict explosions and emergency cover"
```

---

### Task 12: Predict projectile families with discrete motion and swept collision

**Files:** Create projectile step/models/predictor and test.

```java
public record ProjectileStep(Vec3Snapshot position, Vec3Snapshot velocity, long tick) {}
public interface ProjectileMotionModel { ProjectileStep step(ProjectileStep current); }
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
- [ ] **Step 3: Implement exact family motion from 26.1.2 source for arrows, tridents, mob ballistic projectiles, llama spit where applicable, fireballs, wither skulls, wind charges, harmful thrown potions, and fireworks. Use source-defined gravity/drag/acceleration and swept block/entity collision.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ProjectilePredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict projectile impacts by vanilla family"
```

---

### Task 13: Predict fall, void, wall-collision, and falling-object damage

**Files:** Create `LandingPrediction`, `FallLandingSolver`, `FallPredictor`, and test.

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
- [ ] **Step 3: Use future AABB/collision geometry and velocity, not `fallDistance` alone. Cover normal landings, stalagmites, elytra `FLY_INTO_WALL`, observable falling blocks/stalactites, and void trajectories. `FELL_OUT_OF_WORLD` and any observable `GENERIC_KILL`-equivalent source are marked unsavable by death protection from their runtime tags.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.FallPredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict lethal fall and movement damage"
```

---

### Task 14: Predict periodic environmental and status damage

**Files:** Create `HazardClockSnapshot`, `PeriodicHazardPredictor`, and test.

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
- [ ] **Step 3: Cover fire/campfire/lava/hot-floor, cactus/berry bush, suffocation, cramming, drowning, starvation, freezing, world border, dry-out where relevant, dragon-breath/area-effect hazards, Wither, poison/magic effect ticks, and observable lightning. Predictor computes deadlines; `DamageSimulator` owns mitigation/rejection.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.PeriodicHazardPredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict periodic environmental damage"
```

---

### Task 15: Predict reactive damage such as Thorns and pending pearl impact

**Files:** Create `ReactiveDamagePredictor.java` and test.

- [ ] **Step 1: Write reactive tests**

```java
@Test void outgoingAttackCanEmitBoundedThornsThreat() {
    ThreatEvent e = fixture().outgoingAttackAgainstVisibleThornsArmor().predict().get(0);
    assertEquals(BOUNDED, e.confidence());
}
@Test void ownPendingPearlPredictsFiveRawDamageAtTeleport() {
    ThreatEvent e = fixture().ownPearlWithPredictedHitIn(6).predict().get(0);
    assertEquals(5f, e.damage().rawDamage().min(), 0.0001f);
    assertEquals(5f, e.damage().rawDamage().max(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.threat.ReactiveDamagePredictorTest`
- [ ] **Step 3: Add source-faithful Thorns randomness bounds before outgoing attacks and guaranteed 5-raw-damage own-pearl events when hit/teleport is predictable. Instant command/mod damage with no observable precursor remains explicitly unobservable.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ReactiveDamagePredictorTest
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict reactive thorns and pearl damage"
```

---

### Task 16: Predict potential melee, mace, spear, and mob attacks

**Files:** Create `MaceThreatModel`, `SpearThreatModel`, `MeleePredictor`, and test.

- [ ] **Step 1: Write capability tests**

```java
@Test void unreachableAttackerEmitsNoImmediateHit() { assertTrue(fixture().distance(9).legalReach(3).predict().isEmpty()); }
@Test void fallingMaceAttackerEmitsBoundedPotentialDamage() {
    ThreatEvent e = fixture().mace().fallDistanceRange(8,12).inReachSoon().predict().get(0);
    assertEquals(POTENTIAL, e.confidence());
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

### Task 17: Implement bounded planner and Safe mode

**Files:** Create planner contracts and `SurvivalPlanner`; create Safe-mode test.

```java
public final class SurvivalPlanner {
    public SurvivalPlan plan(PredictionContext context, ThreatTimeline timeline,
                             java.util.List<SurvivalAction> candidates, SafetyMode mode);
}
```

- [ ] **Step 1: Write dominance/candidate-cap tests**

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
@Test void plannerNeverEvaluatesBeyondCandidateCap() {
    assertEquals(32, fixture().fortyCandidateActions().plan().evaluatedCandidateCount());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.planner.SurvivalPlannerSafeModeTest`
- [ ] **Step 3: Hard constraints first: server deadline, legality, authoritative prerequisites, worst-case survival. Secondary ranking: reliability, remaining health, consumable cost, disruption. Safe mode forbids deliberate damage manipulation and uses `EngineLimits.maxPlannerCandidates()`.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.timeline.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: plan conservative survival actions"
```

---

### Task 18: Execute death protection and shields with authoritative reconciliation

**Files:** Create execution contracts, death-protection/shield executors, and tests.

```java
public enum ExecutionStatus { STARTED, WAITING_FOR_SERVER, WARMING_UP, CONFIRMED, CONTRADICTED, MISSED_DEADLINE, CANCELLED }
public record ExecutionContext(net.minecraft.client.Minecraft minecraft,
                               TimingSnapshot timing, long clientTick) {}
public interface ActionExecutor<A extends SurvivalAction> {
    ExecutionStatus tick(A action, ExecutionContext context);
    void cancel(ExecutionContext context);
}
```

- [ ] **Step 1: Write authoritative-state and shield-angle tests**

```java
@Test void hotbarRouteConfirmsOnlyAfterAuthoritativeHeldSlotMatches() {
    assertEquals(WAITING_FOR_SERVER, fixture().hotbarRoute(5).firstTick());
    assertEquals(CONFIRMED, fixture().hotbarRoute(5).serverHeldSlot(5).nextTick());
}
@Test void shieldIsNotReadyAtFourUseTicks() { assertEquals(WARMING_UP, fixture().shieldUseTicks(4).tick()); }
@Test void shieldIsReadyAtFiveUseTicks() { assertEquals(CONFIRMED, fixture().shieldUseTicks(5).tick()); }
@Test void shieldPlanIsContradictedWhenSourceLeavesBlockAngle() {
    assertEquals(CONTRADICTED, fixture().activeShield().sourceBehindPlayer().tick());
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests 'dev.pixelied.survival.action.*ExecutorTest'`
- [ ] **Step 3: Use source-confirmed carried-slot/menu `SWAP`, item-use, and rotation flow. Count shield protection only after the server can have accepted use for 5 ticks and angle/bypass/piercing rules still allow blocking. Never report success from local inventory/use state alone. Preserve active offhand blocking with a mainhand protection route when required.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.action.*' --tests 'dev.pixelied.survival.inventory.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute protection and shield states"
```

---

### Task 19: Generate and execute non-totem survival actions

**Files:** Create candidate generators and movement/cover/equipment/effect/fall-rescue executors; create non-totem tests.

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
    ActionSimulation s = fixture().lethalFall().pearlRescue().simulate();
    assertEquals(5f, s.result().eventResult("ender_pearl").preMitigationRaw(), 0.0001f);
}
```

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.planner.NonTotemActionTest --tests dev.pixelied.survival.action.NonTotemExecutorTest`
- [ ] **Step 3: Implement only physically/server-valid candidates.** Movement must move the accepted server AABB; cover must be reachable/legal; equipment candidates are scored by the same source-specific timeline so chestplates, specialized Protection enchantments, elytra, and freeze-relevant wearables are compared from runtime stats/tags rather than names; effect candidates include quickly applicable Resistance, Fire Resistance, healing/absorption and longer-horizon food/Slow Falling/Water Breathing with their real use/flight durations; fall rescues cover water, legal landing-block use, elytra, wind charge, valid mace smash, and pearl relocation. Pearl action simulation inserts a synthetic `ender_pearl` event with 5 raw damage before scoring the resulting timeline.
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.action.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute non-totem survival strategies"
```

---

### Task 20: Add Balanced and strictly gated Experimental hurt-cooldown policy

**Files:** Create hurt-cooldown candidate/strategy; modify planner/mode; create policy tests.

```java
public record HurtCooldownCandidate(String strategyId, ThreatEvent precursor,
                                    SurvivalAction action, boolean runtimeValidated) {}
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
- [ ] **Step 3: Balanced may preserve a protection item only when a proven non-totem action is conservatively safe. Experimental deliberate damage requires high-confidence server `lastHurt`, controllable timing, worst-case survival, material advantage, and `runtimeValidated == true`; every strategy starts false.**
- [ ] **Step 4: Verify and commit**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*'
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: gate balanced and experimental survival policy"
```

---

### Task 21: Add minimal persisted settings, integrate SurvivalEngine, and bound diagnostics

**Files:** Create `SurvivalConfig`, `SurvivalEngine`, `DecisionRecord`, `DecisionHistory`, `SurvivalDebugHud`; wire entrypoint; create config/engine/history tests.

```java
public record SurvivalConfig(SafetyMode safetyMode, boolean restoreHandState,
                             boolean automaticMovement, boolean blockPlacementAndClutches,
                             boolean debugEnabled) {
    public static SurvivalConfig defaults() {
        return new SurvivalConfig(SafetyMode.SAFE, true, false, true, false);
    }
}
public final class SurvivalEngine {
    public void tick();
    public java.util.Optional<SurvivalPlan> currentPlan();
}
```

- [ ] **Step 1: Write config/replanning/history tests**

```java
@Test void defaultsAreSafeAndDebugOff() {
    SurvivalConfig c = SurvivalConfig.defaults();
    assertEquals(SafetyMode.SAFE, c.safetyMode());
    assertFalse(c.debugEnabled());
}
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

- [ ] **Step 2: Run failure:** `./gradlew test --tests dev.pixelied.survival.config.SurvivalConfigTest --tests dev.pixelied.survival.core.SurvivalEngineTest --tests dev.pixelied.survival.debug.DecisionHistoryTest`
- [ ] **Step 3: Persist only the five settings above to `config/predictive_survival.json`. Wire tick flow:** timing -> snapshots -> hurt tracker -> predictors -> timeline -> no-action result -> candidate generation -> planner -> executor -> telemetry. Filter automatic movement and block/clutch actions from settings. HUD shows impact windows, raw/final ranges, hurt confidence, chosen action/deadline, rejected reason, inventory transaction state, and predicted-vs-observed result; normal chat/disk logging stays off.
- [ ] **Step 4: Verify, smoke-run, commit**

```bash
./gradlew test
./gradlew runClient
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: integrate configured predictive survival engine"
```

---

### Task 22: Add exact-runtime 26.1.2 client GameTests and final acceptance gate

**Files:** Modify `build.gradle`; create gametest files/resources and `VALIDATION.md`; update CI only for ordinary build/unit checks unless client GameTests prove stable enough to gate CI.

- [ ] **Step 1: Configure Loom's dedicated game-test source set**

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

Create `src/gametest/resources/fabric.mod.json` with mod id `predictive-survival-gametest` and a client game-test entrypoint. `SurvivalValidationClientGameTest` implements Fabric API's client game-test interface, creates a singleplayer test context, and runs scenarios against the integrated server.

- [ ] **Step 2: Add result types and first runtime scenarios**

```java
public enum ValidationStatus { SOURCE_CONFIRMED, RUNTIME_CONFIRMED, EXPERIMENTAL }
public record ValidationResult(String id, float predictedHealth, float actualHealth,
                               ValidationStatus status, float tolerance) {
    public boolean passes() { return Math.abs(predictedHealth - actualHealth) <= tolerance; }
}
```

First scenarios: normal melee; armor + Resistance + Protection; shield at 4 vs 5 use ticks; smaller/equal/larger hurt-cooldown follow-ups; mainhand and offhand death-protection pop; TNT/crystal exposure with and without cover.

- [ ] **Step 3: Run unit tests then exact-runtime client GameTests**

```bash
./gradlew clean test
./gradlew runClientGameTest
```

Expand scenarios to arrows/tridents; bed/anchor/crystal/TNT cover; fall/wind-charge/mace/pearl; lava/fire/drowning/freezing/Wither-like ticks; Thorns; repeated threats after a pop; and every Experimental hurt-cooldown candidate. Promote a strategy to `RUNTIME_CONFIRMED` only when actual server state agrees within its declared tolerance.

- [ ] **Step 4: Run final acceptance checks and inspect packaging**

```bash
./gradlew clean test build
cd ../..
python -m unittest discover -s tests -v
python agentctl.py validate
```

Inspect the production jar and confirm no `dev/pixelied/survival/validation/` classes are present. `VALIDATION.md` lists supported threat families, runtime-confirmed cases, tolerances, explicitly unobservable instant damage, disabled experimental strategies, and all known discrepancies.

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
- Shield is never credited before its five server-tick warmup plus packet-arrival margin, and angle/piercing/bypass rules remain satisfied at impact.
- Unknown server `lastHurt` never grants hurt-cooldown reduction.
- Small precursor damage never falsely cancels a larger hit.
- `BYPASSES_INVULNERABILITY` never scores death protection as successful.
- Explosion cover is evaluated at entity-damage time before block destruction.
- Multi-hit simulation continues after a pop and can still identify a later lethal threat.
- Valid stale-state-id inventory clicks reconcile from authoritative full resync instead of being assumed rejected.
- Restoration never runs while an imminent threat still requires the emergency hand/equipment state.
- No executor claims success solely from local client state.
- Every client-observable vanilla damage family is predicted or explicitly documented as lacking a useful precursor; Thorns and own pending pearl damage are included.
- Planner/predictor/history work respects `EngineLimits` and never performs world-wide scans or unbounded candidate search.
- Experimental deliberate-damage strategies remain disabled unless exact-runtime validation proves them beneficial and server-valid.
- Debug history is bounded and optional; normal chat/disk logging is off by default.
- The production jar contains no gametest validation classes.
