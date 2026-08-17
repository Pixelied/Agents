# Predictive Survival 26.1.2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a standalone Minecraft Java 26.1.2 Fabric client mod that predicts observable lethal damage with vanilla-faithful semantics and executes the safest server-valid survival action before the damage is processed.

**Architecture:** A pure deterministic damage/timeline core consumes immutable snapshots. Threat-specific predictors produce bounded future events, a planner compares a small set of feasible actions against the same timeline, and state-machine executors perform only server-valid client actions before conservative deadlines. Minecraft/Fabric adapters are kept at the edge so core math and policy remain unit-testable.

**Tech Stack:** Java 25; Minecraft Java 26.1.2; Fabric Loader 0.19.3; Fabric Loom 1.17-SNAPSHOT using `net.fabricmc.fabric-loom`; Fabric API 0.155.2+26.1.2; JUnit Jupiter 5.12.2; Gradle; GitHub Actions.

## Global Constraints

- Target exactly Minecraft Java Edition `26.1.2` and Java `25`.
- Use Fabric Loader `0.19.3`, Fabric Loom `1.17-SNAPSHOT`, Fabric API `0.155.2+26.1.2`, and plugin id `net.fabricmc.fabric-loom`.
- Do not add Yarn mappings or the legacy remapping Loom plugin; the 26.1+ Fabric workflow uses unobfuscated Minecraft names.
- Production code is client-only. Test-only runtime validation helpers must not be packaged into the production jar.
- Treat exact 26.1.2 Minecraft source as authoritative; regenerate sources through Loom and re-check source before changing formulas.
- Runtime damage tags, item components, effects, and enchantments are authoritative where the client can inspect them; do not replace them with item-name heuristics.
- Never treat client-only desync, ghost inventory state, impossible movement, or packet flooding as protection.
- Unknown raw damage, unknown `lastHurt`, uncertain event order, missed deadlines, or contradictory server state must fail conservatively.
- Deliberate hurt-cooldown manipulation remains disabled outside Experimental mode until a tactic has deterministic tests and exact-runtime evidence.
- Keep user settings small: safety mode, restore prior hand/item, automatic movement/evasion, block placement/clutches, debug overlay/logging.
- Every implementation task follows red-green-refactor discipline and ends with a focused commit.

---

## File Map

The implementation should converge on this structure; tasks below introduce it incrementally.

```text
projects/predictive-survival-26-1-2/
  build.gradle
  gradle.properties
  settings.gradle
  src/client/java/dev/pixelied/survival/
    PredictiveSurvivalClient.java
    core/
      SurvivalEngine.java
      PlayerSnapshot.java
      WorldSnapshot.java
      PredictionContext.java
      DamageRange.java
      TickWindow.java
      Vec3Snapshot.java
      Confidence.java
    damage/
      DamageFlag.java
      DamageSourceSnapshot.java
      BlockingSnapshot.java
      MitigationSnapshot.java
      StatusEffectsSnapshot.java
      DeathProtectionSnapshot.java
      HurtState.java
      DamageStage.java
      DamageTrace.java
      DamageResult.java
      DamageSimulator.java
      VanillaDamageMath.java
      MinecraftDamageAdapter.java
    timing/
      ServerTimingEstimator.java
      TimingSnapshot.java
      Deadline.java
    timeline/
      ThreatKind.java
      ThreatEvent.java
      ThreatTimeline.java
      TimelineResult.java
      ThreatTimelineSimulator.java
    threat/
      ThreatPredictor.java
      ThreatPredictorRegistry.java
      ExplosionPredictor.java
      ExplosionExposure.java
      ProjectilePredictor.java
      ProjectileMotionModel.java
      FallPredictor.java
      PeriodicHazardPredictor.java
      MeleePredictor.java
    planner/
      SafetyMode.java
      SurvivalAction.java
      ActionFeasibility.java
      ActionSimulation.java
      SurvivalPlan.java
      SurvivalPlanner.java
      HurtCooldownStrategy.java
    action/
      ActionExecutor.java
      ExecutionStatus.java
      DeathProtectionExecutor.java
      ShieldExecutor.java
      MovementExecutor.java
      CoverExecutor.java
      EquipmentExecutor.java
      EffectExecutor.java
      FallRescueExecutor.java
    inventory/
      InventorySnapshot.java
      MenuSlotMap.java
      EmergencyInventoryTransaction.java
      DeathProtectionRoute.java
      DeathProtectionRoutePlanner.java
    debug/
      DecisionRecord.java
      DecisionHistory.java
      SurvivalDebugHud.java
    mixin/
      LocalPlayerAccessor.java
  src/client/resources/
    predictive_survival.client.mixins.json
  src/main/resources/
    fabric.mod.json
  src/test/java/dev/pixelied/survival/
    ... mirrored unit-test packages ...
  src/gametest/java/dev/pixelied/survival/validation/
    SurvivalValidationTestMod.java
    DamageValidationScenarios.java
  VALIDATION.md
.github/workflows/predictive-survival-26-1-2-ci.yml
```

---

### Task 1: Bootstrap the exact Fabric 26.1.2 project and CI

**Files:**
- Create: `projects/predictive-survival-26-1-2/settings.gradle`
- Create: `projects/predictive-survival-26-1-2/gradle.properties`
- Create: `projects/predictive-survival-26-1-2/build.gradle`
- Create: `projects/predictive-survival-26-1-2/src/main/resources/fabric.mod.json`
- Create: `projects/predictive-survival-26-1-2/src/client/java/dev/pixelied/survival/PredictiveSurvivalClient.java`
- Create: `projects/predictive-survival-26-1-2/src/client/java/dev/pixelied/survival/core/ModConstants.java`
- Create: `projects/predictive-survival-26-1-2/src/test/java/dev/pixelied/survival/core/BuildContractTest.java`
- Create: `.github/workflows/predictive-survival-26-1-2-ci.yml`

**Interfaces:**
- Produces: `ModConstants.MOD_ID = "predictive_survival"`; Fabric client entrypoint `PredictiveSurvivalClient`.

- [ ] **Step 1: Write the failing build-contract test**

```java
package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildContractTest {
    @Test void modIdIsStable() {
        assertEquals("predictive_survival", ModConstants.MOD_ID);
    }
}
```

- [ ] **Step 2: Add the exact build baseline and verify the test initially fails before `ModConstants` exists**

Use these exact Gradle properties:

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

Run from `projects/predictive-survival-26-1-2`:

```bash
./gradlew test
```

Expected: compile failure because `ModConstants` does not yet exist.

- [ ] **Step 3: Add minimal Fabric client code and Java 25/JUnit configuration**

`ModConstants.java`:

```java
package dev.pixelied.survival.core;

public final class ModConstants {
    public static final String MOD_ID = "predictive_survival";
    private ModConstants() {}
}
```

`PredictiveSurvivalClient.java`:

```java
package dev.pixelied.survival;

import net.fabricmc.api.ClientModInitializer;

public final class PredictiveSurvivalClient implements ClientModInitializer {
    @Override public void onInitializeClient() {}
}
```

Configure `build.gradle` with `net.fabricmc.fabric-loom`, `minecraft`, `fabric-loader`, `fabric-api`, JUnit Jupiter, `useJUnitPlatform()`, and Java `25`. Do not declare Yarn mappings.

- [ ] **Step 4: Run unit tests and production jar build**

```bash
./gradlew clean test build
```

Expected: PASS and a jar under `build/libs/`.

- [ ] **Step 5: Add CI and commit**

CI must use a Java 25 distribution, run `./gradlew test build`, and upload only the production jar.

```bash
git add projects/predictive-survival-26-1-2 .github/workflows/predictive-survival-26-1-2-ci.yml
git commit -m "build: bootstrap predictive survival for 26.1.2"
```

---

### Task 2: Add immutable ranges, snapshots, and damage-domain types

**Files:**
- Create: `core/DamageRange.java`, `core/TickWindow.java`, `core/Vec3Snapshot.java`, `core/Confidence.java`
- Create: `damage/DamageFlag.java`, `damage/DamageSourceSnapshot.java`, `damage/BlockingSnapshot.java`, `damage/MitigationSnapshot.java`, `damage/StatusEffectsSnapshot.java`, `damage/DeathProtectionSnapshot.java`, `damage/HurtState.java`
- Create: `core/PlayerSnapshot.java`
- Test: `src/test/java/dev/pixelied/survival/core/DomainTypesTest.java`

**Interfaces:**
- Produces: immutable values used by every later simulator/predictor.
- Exact core signatures:

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
public record HurtState(DamageRange lastHurt, int invulnerableTime, Confidence confidence) {}
```

`DamageFlag` must include at least `BYPASSES_INVULNERABILITY`, `BYPASSES_COOLDOWN`, `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, `BYPASSES_ENCHANTMENTS`, `IS_FIRE`, `DAMAGES_HELMET`, and `IS_FREEZING`.

- [ ] **Step 1: Write invariant tests**

```java
@Test void damageRangeRejectsInvertedBounds() {
    assertThrows(IllegalArgumentException.class, () -> new DamageRange(8f, 4f));
}

@Test void subtractFloorsAtZero() {
    assertEquals(new DamageRange(0f, 3f), new DamageRange(2f, 5f).subtractFloorZero(2f));
}

@Test void tickWindowDetectsOverlap() {
    assertTrue(new TickWindow(10, 12).overlaps(new TickWindow(12, 15)));
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest
```

Expected: FAIL because the domain records do not exist.

- [ ] **Step 3: Implement the immutable records and validation**

`PlayerSnapshot` must carry health, absorption, player/ability invulnerability, dead/dying state, difficulty, `MitigationSnapshot`, `StatusEffectsSnapshot`, `BlockingSnapshot`, `HurtState`, and `DeathProtectionSnapshot`. Keep world scanning and Minecraft entity references out of this package.

- [ ] **Step 4: Run the focused and full tests**

```bash
./gradlew test --tests dev.pixelied.survival.core.DomainTypesTest
./gradlew test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add survival simulation domain model"
```

---

### Task 3: Implement player preprocessing, blocking, and hurt-cooldown ordering

**Files:**
- Create: `damage/DamageStage.java`, `damage/DamageTrace.java`, `damage/DamageResult.java`, `damage/VanillaDamageMath.java`, `damage/DamageSimulator.java`
- Test: `damage/DamageSimulatorPreprocessingTest.java`

**Interfaces:**
- Consumes: `PlayerSnapshot`, `DamageSourceSnapshot`.
- Produces:

```java
public final class DamageSimulator {
    public DamageResult simulate(PlayerSnapshot player, DamageSourceSnapshot source);
}
public record DamageResult(PlayerSnapshot after, DamageTrace trace,
                           boolean rejected, boolean deathProtectionConsumed) {}
```

- [ ] **Step 1: Write exact ordering tests**

```java
@Test void easyDifficultyUsesVanillaFormula() {
    DamageResult r = fixtures().easy().raw(10f).simulate();
    assertEquals(6f, r.trace().after(DamageStage.DIFFICULTY), 0.0001f);
}

@Test void fireResistanceRejectsFireBeforeHurtCooldown() {
    DamageResult r = fixtures().fireResistance(true).flag(DamageFlag.IS_FIRE).raw(8f).simulate();
    assertTrue(r.rejected());
    assertEquals(20f, r.after().health(), 0.0001f);
}

@Test void largerHitDuringStrongCooldownAppliesOnlyExcess() {
    DamageResult r = fixtures().hurt(new HurtState(DamageRange.exact(5f), 15, Confidence.EXACT))
        .raw(12f).simulate();
    assertEquals(7f, r.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
    assertEquals(12f, r.after().hurtState().lastHurt().max(), 0.0001f);
}

@Test void fullyBlockedZeroDoesNotCreateUsefulLastHurt() {
    DamageResult blocked = fixtures().blockingFull().raw(8f).simulate();
    assertEquals(0f, blocked.after().hurtState().lastHurt().max(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest
```

Expected: FAIL because simulator/trace stages are missing.

- [ ] **Step 3: Implement preprocessing in this exact order**

```text
player/gamerule invulnerability
ability invulnerability unless BYPASSES_INVULNERABILITY
dead/dying rejection
difficulty scaling
zero rejection
living invulnerability/dead check
Fire Resistance + IS_FIRE early rejection
negative -> zero
blocking
freezing multiplier
DAMAGES_HELMET * 0.75 when helmet present
NaN/infinity sanitation
hurt cooldown / lastHurt
```

`DamageTrace` must store each before/after stage value so tests can detect ordering regressions.

- [ ] **Step 4: Run focused tests and add a source-audit parity assertion for `BYPASSES_COOLDOWN`**

```bash
./gradlew test --tests dev.pixelied.survival.damage.DamageSimulatorPreprocessingTest
```

Expected: PASS including a synthetic source flagged `BYPASSES_COOLDOWN` that ignores strong hurt cooldown.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: model vanilla hurt preprocessing and cooldown"
```

---

### Task 4: Implement armor, Resistance, enchantments, absorption, durability, and death protection

**Files:**
- Modify: `damage/DamageSimulator.java`, `damage/VanillaDamageMath.java`, `core/PlayerSnapshot.java`, `damage/MitigationSnapshot.java`, `damage/DeathProtectionSnapshot.java`
- Create: `damage/ArmorPieceSnapshot.java`, `damage/EffectInstanceSnapshot.java`
- Test: `damage/DamageSimulatorMitigationTest.java`, `damage/DeathProtectionTest.java`

**Interfaces:**
- `MitigationSnapshot` must expose armor value, toughness, weapon-modified armor-effectiveness multiplier, source-specific enchantment protection, helmet state, and armor-piece durability state.
- `DeathProtectionSnapshot` must represent each interaction hand independently and carry the effect instances that become active after the pop.

- [ ] **Step 1: Write mitigation and pop tests**

```java
@Test void resistanceThreeReducesBySixtyPercent() {
    DamageResult r = fixtures().resistanceAmplifier(2).rawAfterArmor(10f).simulate();
    assertEquals(4f, r.trace().after(DamageStage.RESISTANCE), 0.0001f);
}

@Test void bypassEffectsSkipsResistanceAndProtection() {
    DamageResult r = fixtures().resistanceAmplifier(4).protection(20)
        .flag(DamageFlag.BYPASSES_EFFECTS).rawAfterArmor(10f).simulate();
    assertEquals(10f, r.trace().after(DamageStage.MAGIC), 0.0001f);
}

@Test void absorptionIsConsumedBeforeHealth() {
    DamageResult r = fixtures().health(10f).absorption(4f).rawFinal(6f).simulate();
    assertEquals(0f, r.after().absorption(), 0.0001f);
    assertEquals(8f, r.after().health(), 0.0001f);
}

@Test void offhandDeathProtectionSavesLethalHit() {
    DamageResult r = fixtures().health(4f).offhandDeathProtection(true).rawFinal(8f).simulate();
    assertTrue(r.deathProtectionConsumed());
    assertEquals(1f, r.after().health(), 0.0001f);
}

@Test void bypassInvulnerabilityPreventsDeathProtection() {
    DamageResult r = fixtures().health(4f).offhandDeathProtection(true)
        .flag(DamageFlag.BYPASSES_INVULNERABILITY).rawFinal(8f).simulate();
    assertFalse(r.deathProtectionConsumed());
    assertEquals(0f, r.after().health(), 0.0001f);
}
```

- [ ] **Step 2: Run focused tests and confirm failure**

```bash
./gradlew test --tests 'dev.pixelied.survival.damage.*MitigationTest' --tests dev.pixelied.survival.damage.DeathProtectionTest
```

- [ ] **Step 3: Implement `actuallyHurt` ordering**

Apply armor unless `BYPASSES_ARMOR`; skip the whole effect/enchantment stage when `BYPASSES_EFFECTS`; otherwise apply Resistance unless `BYPASSES_RESISTANCE`, then enchantment protection unless `BYPASSES_ENCHANTMENTS`; consume absorption before health; apply exact source-confirmed armor durability changes so a piece that breaks can change later timeline events; then invoke death protection when health reaches `<= 0`.

Use `VanillaDamageMath.damageAfterArmor(...)` and `VanillaDamageMath.damageAfterMagic(...)` as pure functions whose formulas mirror `CombatRules`.

- [ ] **Step 4: Add a two-hit durability regression and run tests**

```java
@Test void armorBreakingOnFirstHitChangesSecondHitMitigation() {
    TimelineFixture f = timelineFixtures().oneDurabilityChestplate();
    assertTrue(f.simulateTwoHits().secondHitFinalDamage() > f.simulateFirstHitOnly().firstHitFinalDamage());
}
```

```bash
./gradlew test
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: complete vanilla mitigation and death protection"
```

---

### Task 5: Add conservative shadow server hurt-state tracking

**Files:**
- Create: `damage/ServerHurtStateTracker.java`, `damage/HurtObservation.java`
- Test: `damage/ServerHurtStateTrackerTest.java`

**Interfaces:**

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

- [ ] **Step 1: Write confidence-transition tests**

```java
@Test void unexpectedHealthLossInvalidatesRawLastHurt() {
    ServerHurtStateTracker t = new ServerHurtStateTracker();
    t.recordPredictedApplied(12f, new TickWindow(50, 50));
    t.recordObservedHealthDelta(3f, new TickWindow(51, 51));
    assertEquals(Confidence.UNKNOWN, t.current().confidence());
}

@Test void unknownStateGivesNoIframeCredit() {
    ServerHurtStateTracker t = new ServerHurtStateTracker();
    t.invalidate();
    assertEquals(0f, t.conservativeForLethalDecision().lastHurt().max(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.damage.ServerHurtStateTrackerTest
```

- [ ] **Step 3: Implement EXACT/MATCHED/BOUNDED/UNKNOWN transitions**

Do not derive server raw `lastHurt` directly from `LocalPlayer.lastHurt`; health delta may be post-armor/effects/absorption. A predicted event may become `MATCHED` only when timing and observed delta agree within the event's expected interval.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests dev.pixelied.survival.damage.ServerHurtStateTrackerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: track conservative server hurt state"
```

---

### Task 6: Simulate ordered and uncertain multi-hit threat timelines

**Files:**
- Create: `timeline/ThreatKind.java`, `timeline/ThreatEvent.java`, `timeline/ThreatTimeline.java`, `timeline/TimelineResult.java`, `timeline/ThreatTimelineSimulator.java`
- Test: `timeline/ThreatTimelineSimulatorTest.java`

**Interfaces:**

```java
public record ThreatEvent(String id, ThreatKind kind, TickWindow impact,
                          DamageSourceSnapshot damage, Confidence confidence) {}
public record ThreatTimeline(java.util.List<ThreatEvent> events) {}
public final class ThreatTimelineSimulator {
    public TimelineResult simulate(PlayerSnapshot start, ThreatTimeline timeline);
}
```

`TimelineResult` must contain worst-case final health/absorption, survived flag, consumed death-protection count, ordered event results, and the event that first becomes lethal.

- [ ] **Step 1: Write order-sensitive tests**

```java
@Test void individuallySafeHitsCanBeLethalAsSequence() {
    TimelineResult r = fixture().health(10f).hits(6f, 6f, 6f).spacedBeyondCooldown().simulate();
    assertFalse(r.survived());
}

@Test void sameWindowUsesWorstMateriallyPlausibleOrder() {
    TimelineResult r = fixture().health(10f).sameWindowHits(4f, 12f).simulate();
    assertEquals(12f, r.eventResults().getFirst().preMitigationRaw(), 0.0001f);
}

@Test void timelineContinuesAfterTotemPop() {
    TimelineResult r = fixture().health(5f).totems(1).hits(10f, 10f).simulate();
    assertEquals(1, r.consumedDeathProtectionCount());
    assertFalse(r.survived());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.timeline.ThreatTimelineSimulatorTest
```

- [ ] **Step 3: Implement deterministic ordering and bounded worst-case permutations**

Only permute events whose `TickWindow`s overlap and whose order changes material results; cap permutation count and fall back to a conservative ordering when the cap would be exceeded.

- [ ] **Step 4: Run tests and full damage suite**

```bash
./gradlew test --tests 'dev.pixelied.survival.timeline.*' --tests 'dev.pixelied.survival.damage.*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: simulate multi-hit threat timelines"
```

---

### Task 7: Estimate conservative server timing and action deadlines

**Files:**
- Create: `timing/ServerTimingEstimator.java`, `timing/TimingSnapshot.java`, `timing/Deadline.java`
- Test: `timing/ServerTimingEstimatorTest.java`

**Interfaces:**

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
@Test void shieldNeedsPacketArrivalPlusFiveServerTicks() {
    TimingSnapshot s = fixture().rtt(100).jitter(20).arrivalWindow(102, 103).snapshot();
    assertFalse(s.canCompleteBefore(5, new TickWindow(106, 106)));
    assertTrue(s.canCompleteBefore(5, new TickWindow(109, 110)));
}

@Test void higherJitterWidensConservativeArrivalWindow() {
    assertTrue(fixture().jitter(60).snapshot().nextPacketProcessingWindow().latest()
        > fixture().jitter(5).snapshot().nextPacketProcessingWindow().latest());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.timing.ServerTimingEstimatorTest
```

- [ ] **Step 3: Implement bounded RTT/jitter estimation**

Use a short rolling sample window; never claim exact one-way latency. All feasibility checks use the latest conservative arrival bound, not the average.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests dev.pixelied.survival.timing.ServerTimingEstimatorTest
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: estimate server action deadlines"
```

---

### Task 8: Adapt live Minecraft 26.1.2 state into pure simulation snapshots

**Files:**
- Create: `damage/MinecraftDamageAdapter.java`
- Create: `core/MinecraftSnapshotFactory.java`
- Create: `damage/MinecraftBlockingAdapter.java`
- Create: `damage/MinecraftEquipmentAdapter.java`
- Create: `mixin/LocalPlayerAccessor.java`
- Create: `src/client/resources/predictive_survival.client.mixins.json`
- Test: `damage/MinecraftAdapterContractTest.java`

**Interfaces:**

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

- [ ] **Step 1: Write source-contract tests for runtime tag mapping**

```java
@Test void bypassArmorFlagMapsIntoSnapshot() {
    DamageSourceSnapshot s = adapterFixture().sourceWith(DamageFlag.BYPASSES_ARMOR).snapshot();
    assertTrue(s.flags().contains(DamageFlag.BYPASSES_ARMOR));
}

@Test void shieldIsInactiveBeforeRequiredUseTicks() {
    BlockingSnapshot b = blockingFixture().elapsedTicks(4).requiredTicks(5).snapshot();
    assertFalse(b.active());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.damage.MinecraftAdapterContractTest
```

- [ ] **Step 3: Re-open generated 26.1.2 sources before implementing adapters**

Verify the methods listed in `tasks/design-predictive-survival-26-1-2/artifacts/source-audit.md`, then implement runtime tag/component/effect/enchantment extraction. Keep mixins limited to data unavailable through public client APIs. Never use local `lastHurt` as the server raw value.

- [ ] **Step 4: Compile and run tests**

```bash
./gradlew test compileClientJava
```

Expected: PASS with direct unobfuscated 26.1.2 names and no Yarn dependency.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: snapshot live minecraft survival state"
```

---

### Task 9: Build emergency inventory transactions and death-protection routing

**Files:**
- Create: `inventory/InventorySnapshot.java`, `inventory/MenuSlotMap.java`, `inventory/EmergencyInventoryTransaction.java`, `inventory/DeathProtectionRoute.java`, `inventory/DeathProtectionRoutePlanner.java`
- Test: `inventory/DeathProtectionRoutePlannerTest.java`, `inventory/EmergencyInventoryTransactionTest.java`

**Interfaces:**

```java
public sealed interface DeathProtectionRoute permits AlreadyInHandRoute, HotbarSelectRoute, ContainerSwapRoute {}
public final class DeathProtectionRoutePlanner {
    public DeathProtectionRoute choose(InventorySnapshot inventory, boolean preserveOffhandBlock,
                                       TimingSnapshot timing, TickWindow lethalImpact);
}
```

Transaction states: `PLANNED`, `SENT`, `AWAITING_RECONCILE`, `CONFIRMED`, `CONTRADICTED`, `CONSUMED`, `RESTORING`, `DONE`.

- [ ] **Step 1: Write routing and resync tests**

```java
@Test void hotbarTotemUsesOnePacketMainhandRoute() {
    DeathProtectionRoute r = fixture().totemInHotbar(5).selected(1).choose();
    assertInstanceOf(HotbarSelectRoute.class, r);
}

@Test void activeOffhandShieldPrefersMainhandTotem() {
    DeathProtectionRoute r = fixture().activeOffhandShield().totemInInventory(17).choose();
    assertEquals(RouteDestination.MAIN_HAND, ((ContainerSwapRoute) r).destination());
}

@Test void staleStateIdMovesToAwaitingReconcileNotFailed() {
    EmergencyInventoryTransaction tx = transactionFixture().sent().stateIdMismatch().applyServerUpdate();
    assertEquals(TransactionState.AWAITING_RECONCILE, tx.state());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests 'dev.pixelied.survival.inventory.*'
```

- [ ] **Step 3: Implement exact one-packet routes**

Support: already in either hand; serverbound carried-slot change for a hotbar item; one vanilla container `SWAP` to selected hotbar slot or offhand button `40`. Derive player-inventory menu slots from the current menu mapping; never hard-code screen slot ids. Reconcile full-state updates after stale `stateId` because 26.1.2 applies the valid click and then resynchronizes.

- [ ] **Step 4: Run inventory and timeline tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.inventory.*' --tests 'dev.pixelied.survival.timeline.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: plan safe death protection inventory routes"
```

---

### Task 10: Define predictor contracts and bounded broad-phase scanning

**Files:**
- Create: `core/WorldSnapshot.java`, `core/PredictionContext.java`
- Create: `threat/ThreatPredictor.java`, `threat/ThreatPredictorRegistry.java`
- Test: `threat/ThreatPredictorRegistryTest.java`

**Interfaces:**

```java
public interface ThreatPredictor {
    java.util.List<ThreatEvent> predict(PredictionContext context);
}
public final class ThreatPredictorRegistry {
    public java.util.List<ThreatEvent> predictAll(PredictionContext context);
}
```

`PredictionContext` contains the player snapshot, compact nearby world/entity snapshot, timing snapshot, horizon ticks, and current client tick.

- [ ] **Step 1: Write merge/deduplication tests**

```java
@Test void duplicatePhysicalThreatsMergeToWiderConservativeBounds() {
    ThreatEvent a = event("crystal-7", 8f, 10f, 20, 21);
    ThreatEvent b = event("crystal-7", 9f, 12f, 20, 22);
    ThreatEvent merged = registryFixture(a, b).predictAll().getFirst();
    assertEquals(12f, merged.damage().rawDamage().max(), 0.0001f);
    assertEquals(22, merged.impact().latest());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ThreatPredictorRegistryTest
```

- [ ] **Step 3: Implement registry, horizon caps, and stable threat ids**

Do not perform world-wide scans. `WorldSnapshot` stores only entities/blocks needed by predictors inside the configured internal horizon.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.threat.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add bounded threat predictor framework"
```

---

### Task 11: Implement explosion prediction, exposure, and candidate cover simulation

**Files:**
- Create: `threat/ExplosionPredictor.java`, `threat/ExplosionExposure.java`, `threat/ExplosionCandidate.java`
- Create: `planner/CoverCandidateEvaluator.java`
- Test: `threat/ExplosionPredictorTest.java`, `planner/CoverCandidateEvaluatorTest.java`

**Interfaces:**

```java
public final class ExplosionExposure {
    public float seenPercent(AabbSnapshot target, Vec3Snapshot center, OcclusionView world);
    public float rawEntityDamage(float radius, double distance, float exposure);
}
public final class CoverCandidateEvaluator {
    public java.util.List<CoverCandidate> evaluate(PredictionContext ctx, ThreatEvent explosion);
}
```

- [ ] **Step 1: Write exact exposure/damage and ordering tests**

```java
@Test void solidCoverLowersExposureAndDamage() {
    float open = exposureFixture().open().damage();
    float covered = exposureFixture().solidBlockBetween().damage();
    assertTrue(covered < open);
}

@Test void candidateMayCountEvenIfExplosionWouldDestroyItLater() {
    assertTrue(coverFixture().blockPresentAtEntityDamagePhase().survivesTimeline());
}

@Test void tntFuseProducesExactImpactWindow() {
    assertEquals(new TickWindow(80, 80), tntFixture().fuseTicks(80).event().impact());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ExplosionPredictorTest --tests dev.pixelied.survival.planner.CoverCandidateEvaluatorTest
```

- [ ] **Step 3: Implement 26.1.2 source-faithful explosion math**

Support primed TNT/minecart TNT, creepers, end crystals, bad-respawn-point bed/anchor explosions, fireworks, and other observable `ServerExplosion` families. For no-fuse crystal/anchor/bed threats, emit `POTENTIAL` immediate windows when an opponent can legally trigger the lethal state inside the horizon.

- [ ] **Step 4: Run focused tests plus a high-exposure/low-exposure golden matrix**

```bash
./gradlew test --tests '*Explosion*' --tests '*CoverCandidate*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict explosions and emergency cover"
```

---

### Task 12: Implement projectile-family discrete simulation

**Files:**
- Create: `threat/ProjectilePredictor.java`, `threat/ProjectileMotionModel.java`, `threat/BallisticProjectileModel.java`, `threat/AcceleratedProjectileModel.java`, `threat/FireworkProjectileModel.java`
- Test: `threat/ProjectilePredictorTest.java`

**Interfaces:**

```java
public interface ProjectileMotionModel {
    ProjectileStep step(ProjectileStep current);
}
public final class ProjectilePredictor implements ThreatPredictor {
    public java.util.List<ThreatEvent> predict(PredictionContext context);
}
```

- [ ] **Step 1: Write trajectory/collision tests**

```java
@Test void arrowPredictsFirstSweptAabbIntersectionTick() {
    ThreatEvent e = projectileFixture().arrow().towardPlayer().predict().getFirst();
    assertEquals(7, e.impact().earliest());
}

@Test void blockCollisionBeforePlayerRemovesThreat() {
    assertTrue(projectileFixture().arrow().stoneWallAtTick4().predict().isEmpty());
}

@Test void unknownCriticalOrEnchantDamageWidensDamageRange() {
    ThreatEvent e = projectileFixture().arrow().unknownCriticalState().predict().getFirst();
    assertTrue(e.damage().rawDamage().max() > e.damage().rawDamage().min());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ProjectilePredictorTest
```

- [ ] **Step 3: Implement family models from exact 26.1.2 entity classes**

Cover arrows, tridents, mob ballistic projectiles, llama spit where applicable, fireballs, wither skulls, wind charges, harmful thrown potions, and fireworks. Simulate per tick with source-defined gravity/drag/acceleration and swept block/entity collision; stop at the first blocking collision.

- [ ] **Step 4: Run trajectory fixture matrix**

```bash
./gradlew test --tests dev.pixelied.survival.threat.ProjectilePredictorTest
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict projectile impacts by vanilla family"
```

---

### Task 13: Implement fall, void, wall-collision, and falling-object prediction

**Files:**
- Create: `threat/FallPredictor.java`, `threat/FallLandingSolver.java`
- Test: `threat/FallPredictorTest.java`

**Interfaces:**

```java
public final class FallLandingSolver {
    public java.util.Optional<LandingPrediction> firstLanding(PredictionContext context);
}
```

- [ ] **Step 1: Write landing tests**

```java
@Test void slimeLandingPredictsZeroVanillaFallDamage() {
    assertEquals(0f, fallFixture().ontoSlime().event().damage().rawDamage().max(), 0.0001f);
}

@Test void hayLandingUsesPointTwoMultiplier() {
    float normal = fallFixture().ontoStone().event().damage().rawDamage().max();
    float hay = fallFixture().ontoHay().event().damage().rawDamage().max();
    assertEquals(normal * 0.2f, hay, 0.01f);
}

@Test void voidThreatIsAvoidanceOnly() {
    ThreatEvent e = fallFixture().towardVoid().event();
    assertTrue(e.damage().flags().contains(DamageFlag.BYPASSES_INVULNERABILITY));
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.FallPredictorTest
```

- [ ] **Step 3: Implement collision-based landing prediction**

Cover normal falls, stalagmites, elytra `FLY_INTO_WALL`, observable falling blocks/stalactites, and void trajectories. Use future AABB/collision geometry and velocity; do not rely on `fallDistance` alone.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests dev.pixelied.survival.threat.FallPredictorTest
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict lethal fall and movement damage"
```

---

### Task 14: Implement periodic environmental and status-damage deadlines

**Files:**
- Create: `threat/PeriodicHazardPredictor.java`, `threat/HazardClockSnapshot.java`
- Test: `threat/PeriodicHazardPredictorTest.java`

**Interfaces:**

```java
public final class PeriodicHazardPredictor implements ThreatPredictor {
    public java.util.List<ThreatEvent> predict(PredictionContext context);
}
```

- [ ] **Step 1: Write cadence/floor tests**

```java
@Test void firePredictorUsesNextActualDamageTickNotConstantDps() {
    ThreatEvent e = hazardFixture().onFire().ticksUntilNextDamage(3).predict().getFirst();
    assertEquals(3, e.impact().earliest());
}

@Test void poisonDoesNotPredictVanillaForbiddenLethalFloor() {
    TimelineResult r = hazardFixture().health(1f).poisonTick().simulate();
    assertTrue(r.survived());
    assertEquals(1f, r.finalHealth(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.PeriodicHazardPredictorTest
```

- [ ] **Step 3: Implement exact cadence/state checks**

Cover in-fire/on-fire/campfire/lava/hot-floor, cactus/berry bush, suffocation, cramming, drowning, starvation, freezing, world border, dry-out where relevant, area-effect hazards, Wither, poison/magic effect ticks, and observable lightning. Let `DamageSimulator` handle Fire Resistance and other mitigation instead of duplicating it here.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests dev.pixelied.survival.threat.PeriodicHazardPredictorTest
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict periodic environmental damage"
```

---

### Task 15: Implement potential melee, mace, spear, and mob attack prediction

**Files:**
- Create: `threat/MeleePredictor.java`, `threat/MaceThreatModel.java`, `threat/SpearThreatModel.java`
- Test: `threat/MeleePredictorTest.java`

**Interfaces:**

```java
public final class MeleePredictor implements ThreatPredictor {
    public java.util.List<ThreatEvent> predict(PredictionContext context);
}
```

- [ ] **Step 1: Write capability-range tests**

```java
@Test void unreachableAttackerDoesNotEmitImmediateHit() {
    assertTrue(meleeFixture().attackerDistance(9).reach(3).predict().isEmpty());
}

@Test void fallingMaceAttackerEmitsBoundedPotentialDamage() {
    ThreatEvent e = meleeFixture().mace().fallDistanceRange(8, 12).inReachSoon().predict().getFirst();
    assertEquals(Confidence.POTENTIAL, e.confidence());
    assertTrue(e.damage().rawDamage().max() > e.damage().rawDamage().min());
}

@Test void shieldDisableCapabilityMarksBlockingAsUnreliable() {
    assertTrue(meleeFixture().shieldDisablingWeapon().event().canDisableBlocking());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.threat.MeleePredictorTest
```

- [ ] **Step 3: Re-check `MaceItem`, the 26.1.2 spear implementation, reach, cooldown, and shield-disable source before coding**

Predict *capability* rather than claiming another player's future click is known. Use visible equipment/effects/enchantments, relative movement, legal reach, and source-specific damage hooks. Mob attacks use their visible attack state/timers where available.

- [ ] **Step 4: Run tests**

```bash
./gradlew test --tests dev.pixelied.survival.threat.MeleePredictorTest
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: predict potential melee mace and spear threats"
```

---

### Task 16: Implement bounded survival planning and Safe mode

**Files:**
- Create: `planner/SafetyMode.java`, `planner/SurvivalAction.java`, `planner/ActionFeasibility.java`, `planner/ActionSimulation.java`, `planner/SurvivalPlan.java`, `planner/SurvivalPlanner.java`
- Test: `planner/SurvivalPlannerSafeModeTest.java`

**Interfaces:**

```java
public sealed interface SurvivalAction permits EquipDeathProtectionAction, RaiseShieldAction,
    MoveToSafetyAction, PlaceCoverAction, SwapEquipmentAction, ApplyEffectAction, FallRescueAction,
    HurtCooldownAction {}

public final class SurvivalPlanner {
    public SurvivalPlan plan(PredictionContext context, ThreatTimeline timeline,
                             java.util.List<SurvivalAction> candidates, SafetyMode mode);
}
```

- [ ] **Step 1: Write Safe-mode dominance tests**

```java
@Test void safeModeChoosesTotemWhenOtherActionCannotGuaranteeDeadline() {
    SurvivalPlan p = plannerFixture().lethalCrystalIn(3).totemFeasible().shieldNeeds5Ticks().safe().plan();
    assertInstanceOf(EquipDeathProtectionAction.class, p.action());
}

@Test void safeModeKeepsAlreadyActiveGuaranteedShieldInsteadOfWastingTotem() {
    SurvivalPlan p = plannerFixture().blockableLethalHit().activeShield().totemAvailable().safe().plan();
    assertInstanceOf(RaiseShieldAction.class, p.action());
}

@Test void bypassInvulnerabilityNeverScoresTotemAsSurvival() {
    assertFalse(plannerFixture().voidThreat().totemAvailable().totemSimulation().survived());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.planner.SurvivalPlannerSafeModeTest
```

- [ ] **Step 3: Implement bounded candidate evaluation**

Hard constraints first: server deadline, legality, required state, worst-case survival. Secondary ordering: reliability, remaining health, consumable cost, disruption. Safe mode forbids deliberate damage manipulation and equips death protection when no proven earlier action guarantees survival.

- [ ] **Step 4: Run planner + timeline tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.timeline.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: plan conservative survival actions"
```

---

### Task 17: Execute death protection and shield state machines with authoritative reconciliation

**Files:**
- Create: `action/ActionExecutor.java`, `action/ExecutionStatus.java`, `action/DeathProtectionExecutor.java`, `action/ShieldExecutor.java`
- Test: `action/DeathProtectionExecutorTest.java`, `action/ShieldExecutorTest.java`

**Interfaces:**

```java
public interface ActionExecutor<A extends SurvivalAction> {
    ExecutionStatus tick(A action, ExecutionContext context);
    void cancel(ExecutionContext context);
}
```

- [ ] **Step 1: Write executor tests**

```java
@Test void hotbarRouteCompletesOnlyAfterAuthoritativeHeldSlotMatches() {
    ExecutionStatus sent = fixture().hotbarRoute(5).tick();
    assertEquals(ExecutionStatus.WAITING_FOR_SERVER, sent);
    assertEquals(ExecutionStatus.CONFIRMED, fixture().serverHeldSlot(5).tickAgain());
}

@Test void shieldIsNotReportedProtectiveAtFourUseTicks() {
    assertEquals(ExecutionStatus.WARMING_UP, shieldFixture().serverUseTicks(4).tick());
}

@Test void shieldBecomesReadyAtFiveUseTicks() {
    assertEquals(ExecutionStatus.CONFIRMED, shieldFixture().serverUseTicks(5).tick());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests 'dev.pixelied.survival.action.*ExecutorTest'
```

- [ ] **Step 3: Implement real client actions through vanilla APIs/packets**

Use the source-confirmed carried-slot/menu `SWAP` routes and item-use flow. Do not report success from local inventory mutation alone. Preserve an active offhand shield by preferring a mainhand death-protection route when the plan requires both defenses.

- [ ] **Step 4: Run action/inventory tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.action.*' --tests 'dev.pixelied.survival.inventory.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute totem and shield survival states"
```

---

### Task 18: Execute non-totem avoidance, cover, equipment, effects, and fall rescues

**Files:**
- Create: `action/MovementExecutor.java`, `action/CoverExecutor.java`, `action/EquipmentExecutor.java`, `action/EffectExecutor.java`, `action/FallRescueExecutor.java`
- Create: `planner/MovementCandidateGenerator.java`, `planner/EquipmentCandidateGenerator.java`, `planner/EffectCandidateGenerator.java`, `planner/FallRescueCandidateGenerator.java`
- Test: `planner/NonTotemActionTest.java`, `action/NonTotemExecutorTest.java`

**Interfaces:**
- Each generator produces only physically/legal candidates that can finish before the `TimingSnapshot` deadline.
- Executors must return `CONTRADICTED` if authoritative server state no longer matches the planned prerequisite.

- [ ] **Step 1: Write representative no-totem tests**

```java
@Test void coverCandidateMustReduceWorstCaseTimelineAndMeetDeadline() {
    SurvivalAction a = fixture().lethalCrystal().placeableObsidianCover().bestAction();
    assertInstanceOf(PlaceCoverAction.class, a);
}

@Test void chestplateSwapCanBeatElytraForExplosion() {
    ActionSimulation sim = fixture().elytraEquipped().netheriteChestplateInHotbar().lethalExplosion().simulateSwap();
    assertTrue(sim.result().survived());
}

@Test void goldenAppleIsRejectedForThreeTickThreat() {
    assertFalse(fixture().goldenApple().threatIn(3).feasibility().feasible());
}

@Test void pearlFallRescueIncludesFiveRawPearlDamage() {
    ActionSimulation sim = fixture().lethalFall().pearlRescue().simulate();
    assertEquals(5f, sim.trace().event("ender_pearl").rawDamage(), 0.0001f);
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.planner.NonTotemActionTest --tests dev.pixelied.survival.action.NonTotemExecutorTest
```

- [ ] **Step 3: Implement bounded candidates and state machines**

Movement must move the accepted server AABB into a safe reachable state; cover must use a legal reachable placement; equipment swaps account for real packet count; effects account for use/flight duration; fall rescues include water placement, legal landing-block use, elytra activation, wind-charge impulse, mace-smash fall reset, and ender-pearl relocation only when prerequisites are visible and server-valid.

- [ ] **Step 4: Run all planner/action tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*' --tests 'dev.pixelied.survival.action.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: execute non-totem survival strategies"
```

---

### Task 19: Add Balanced mode and strictly gated Experimental hurt-cooldown strategies

**Files:**
- Create: `planner/HurtCooldownStrategy.java`, `planner/HurtCooldownCandidate.java`
- Modify: `planner/SurvivalPlanner.java`, `planner/SafetyMode.java`
- Test: `planner/HurtCooldownStrategyTest.java`, `planner/BalancedPolicyTest.java`

**Interfaces:**

```java
public final class HurtCooldownStrategy {
    public java.util.Optional<ActionSimulation> evaluate(HurtCooldownCandidate candidate,
                                                         PredictionContext context,
                                                         ThreatTimeline timeline);
}
```

- [ ] **Step 1: Write anti-folklore tests**

```java
@Test void oneDamageFireTickDoesNotCancelTwentyDamageHit() {
    ActionSimulation sim = iframeFixture().precursor(1f).incoming(20f).simulate();
    assertEquals(19f, sim.trace().incomingAfterCooldown(), 0.0001f);
}

@Test void unknownServerLastHurtRejectsIntentionalDamageStrategy() {
    assertTrue(iframeFixture().unknownLastHurt().evaluate().isEmpty());
}

@Test void experimentalCandidateMustBeatNoActionWorstCase() {
    ActionSimulation sim = iframeFixture().validatedCandidate().evaluate().orElseThrow();
    assertTrue(sim.result().finalHealth() > iframeFixture().noAction().finalHealth());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.planner.HurtCooldownStrategyTest --tests dev.pixelied.survival.planner.BalancedPolicyTest
```

- [ ] **Step 3: Implement policy gates**

Balanced may preserve a totem only when a proven non-totem action is conservatively safe. Experimental may evaluate a deliberate-damage action only if server `lastHurt` is high-confidence, timing is controllable, the complete worst-case sequence survives, it materially beats doing nothing, and the tactic carries a runtime-validation flag. No tactic starts with `runtimeValidated=true`.

- [ ] **Step 4: Run policy tests**

```bash
./gradlew test --tests 'dev.pixelied.survival.planner.*'
```

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: add balanced and experimental survival policy"
```

---

### Task 20: Wire the SurvivalEngine, bounded telemetry, and minimal debug HUD

**Files:**
- Create: `core/SurvivalEngine.java`
- Modify: `PredictiveSurvivalClient.java`
- Create: `debug/DecisionRecord.java`, `debug/DecisionHistory.java`, `debug/SurvivalDebugHud.java`
- Test: `core/SurvivalEngineTest.java`, `debug/DecisionHistoryTest.java`

**Interfaces:**

```java
public final class SurvivalEngine {
    public void tick();
    public java.util.Optional<SurvivalPlan> currentPlan();
}
public final class DecisionHistory {
    public void add(DecisionRecord record);
    public java.util.List<DecisionRecord> snapshot();
}
```

- [ ] **Step 1: Write orchestration and bounded-history tests**

```java
@Test void engineReplansWhenCurrentActionBecomesInfeasible() {
    SurvivalEngineFixture f = engineFixture().lethalThreat().initialShieldPlan();
    f.advanceWithMissedShieldDeadline();
    assertInstanceOf(EquipDeathProtectionAction.class, f.engine().currentPlan().orElseThrow().action());
}

@Test void decisionHistoryRemainsBounded() {
    DecisionHistory h = new DecisionHistory(128);
    for (int i = 0; i < 300; i++) h.add(record(i));
    assertEquals(128, h.snapshot().size());
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./gradlew test --tests dev.pixelied.survival.core.SurvivalEngineTest --tests dev.pixelied.survival.debug.DecisionHistoryTest
```

- [ ] **Step 3: Wire client ticks in the required data-flow order**

`timing -> snapshots -> hurt tracker -> predictors -> timeline -> no-action result -> candidate generation -> planner -> executor -> telemetry`. The HUD shows threat impact windows, raw/final damage range, hurt-state confidence, chosen action/deadline, rejected action reason, inventory transaction state, and predicted-vs-observed result. Normal chat and disk logging stay off by default.

- [ ] **Step 4: Run full tests and a dev-client smoke launch**

```bash
./gradlew test runClient
```

Expected: tests PASS; client reaches title screen/world without mixin or initialization errors.

- [ ] **Step 5: Commit**

```bash
git add projects/predictive-survival-26-1-2/src
git commit -m "feat: integrate predictive survival engine"
```

---

### Task 21: Add exact-runtime 26.1.2 validation scenarios and final acceptance gate

**Files:**
- Modify: `build.gradle` to add a non-production `gametest` source set/run configuration whose output is excluded from the production jar
- Create: `src/gametest/java/dev/pixelied/survival/validation/SurvivalValidationTestMod.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/DamageValidationScenarios.java`
- Create: `VALIDATION.md`
- Modify: `.github/workflows/predictive-survival-26-1-2-ci.yml`

**Interfaces:**
- Validation records must label each scenario `SOURCE_CONFIRMED`, `RUNTIME_CONFIRMED`, or `EXPERIMENTAL`.
- Production planner may consume only `RUNTIME_CONFIRMED` exploit-like tactics outside normal vanilla mechanics.

- [ ] **Step 1: Add a validation scenario that compares predicted and actual damage**

```java
public record ValidationResult(String id, float predictedHealth, float actualHealth,
                               ValidationStatus status, float tolerance) {
    public boolean passes() { return Math.abs(predictedHealth - actualHealth) <= tolerance; }
}
```

The first scenarios must include normal melee, armor + Resistance + Protection, shield at 4 vs 5 use ticks, a two-hit hurt-cooldown sequence, one death-protection pop, and one crystal/TNT exposure case.

- [ ] **Step 2: Run unit tests first**

```bash
./gradlew clean test
```

Expected: PASS before any runtime conclusions are recorded.

- [ ] **Step 3: Run the exact-runtime validation matrix**

Use the dedicated development validation run configuration created in `build.gradle`, for example:

```bash
./gradlew runGametest
```

Expand the matrix to arrows/tridents, bed-or-anchor/crystal/TNT cover, fall/wind-charge/mace/pearl, lava/fire/drowning/freezing/Wither-like ticks, repeated threats after a pop, and any Experimental hurt-cooldown candidate. A scenario is promoted to `RUNTIME_CONFIRMED` only after actual server health/absorption agrees with the simulator inside its declared tolerance.

- [ ] **Step 4: Run repository acceptance checks**

From the mod project:

```bash
./gradlew clean test build
```

From the Agents repository root:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

Expected: all PASS. Inspect the production jar and confirm it contains no `dev/pixelied/survival/validation/` classes.

- [ ] **Step 5: Document limitations and commit**

`VALIDATION.md` must list every supported threat family, runtime-confirmed cases, prediction tolerances, unobservable instant damage, disabled experimental tactics, and any remaining discrepancy. Do not mark a tactic validated because it merely compiled or looked plausible in source.

```bash
git add projects/predictive-survival-26-1-2 .github/workflows/predictive-survival-26-1-2-ci.yml
git commit -m "test: validate survival engine against 26.1.2 runtime"
```

---

## Final Verification Checklist

Before declaring the implementation complete, the executing agent must verify all of these in one fresh checkout:

```bash
cd projects/predictive-survival-26-1-2
./gradlew clean test build
cd ../..
python -m unittest discover -s tests -v
python agentctl.py validate
```

Then verify behavior, not only compilation:

- A lethal observable threat places a valid death-protection item in either server-recognized hand before the conservative deadline when available.
- Mainhand routing can preserve an already-active offhand shield when that is the safer combined defense.
- Shield is never credited before its five server-tick warmup plus packet-arrival margin.
- Unknown server `lastHurt` never grants iframe credit.
- Small precursor damage does not falsely cancel a larger hit.
- `BYPASSES_INVULNERABILITY` never scores a totem/death-protection pop as successful.
- Explosion cover is evaluated at the entity-damage phase before block destruction.
- Event ordering and multi-hit sequences can consume a pop and still kill the player if a later threat remains.
- Inventory state-id mismatch is reconciled as applied-click-plus-full-resync when the menu/click is otherwise valid.
- No executor claims success solely from local client state.
- Every observable vanilla damage family is either predicted or explicitly documented as lacking a useful precursor.
- Experimental deliberate-damage tactics remain disabled unless exact-runtime validation proves them beneficial and server-valid.
- Debug history is bounded and optional; normal gameplay is not spammed.
- The production jar contains no test-only validation helpers.
