# Predictive Survival Instant-Burst Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Predictive Survival pre-arm server-authoritative death protection for every source-confirmed first-observable/near-instant lethal opportunity in Minecraft Java 26.1.2, while hardening explosion exposure and damage/timing semantics against vanilla source and exact-runtime behavior.

**Architecture:** Keep actual threats and hypothetical hostile setup opportunities as separate data. A new `LethalOpportunityRegistry` projects only source-confirmed, legally reachable precursor states into a planning-only risk timeline; the existing planner/executors remain responsible for selecting and establishing server-valid protection. Shared explosion/damage primitives, conservative authority timing, exact collision-shape occlusion, and a lightweight protection-continuity latch prevent special-case drift.

**Tech Stack:** Java 25, Fabric Loader 0.19.3, Fabric API 0.155.2+26.1.2, Minecraft Java 26.1.2 official mappings/no Yarn, JUnit 5, Fabric client GameTests, Gradle 9.5.1, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-25-predictive-survival-instant-burst-design.md`

## Global Constraints

- Start implementation from `fix/predictive-survival-instant-burst-26-1-2`, which is based directly on canonical `project/predictive-survival-26-1-2`.
- Before the first production edit, refresh `main` coordination state and confirm `projects/predictive-survival-26-1-2` is not actively leased by another agent; never overwrite generation-5 work or silently import PR #40.
- Minecraft target is exactly Java 26.1.2 on Java 25 with the repository's pinned Fabric dependencies and checked-in Gradle 9.5.1 wrapper.
- Do not use client-only invulnerability, packet desync, impossible movement, or any action the vanilla server would not recognize.
- Preserve exact client-observable vanilla state; bound network-aged state; represent genuinely unsynchronized server NBT honestly instead of reading client defaults as authoritative.
- `SAFE` may fail closed over hidden server state; `BALANCED` uses source-confirmed vanilla defaults while retaining uncertainty metadata; `EXPERIMENTAL` must not become less source-faithful than `BALANCED`.
- Keep actual `ThreatEvent`s separate from `LethalOpportunity`s; opportunity events may enter only the planning risk timeline, never the actual-observation timeline.
- Use the same explosion raw-damage and living-damage semantics for actual threats and opportunity projections.
- No crude `distance < N => totem` rule. Distance/reach is broad-phase only; final decisions require legal-action geometry plus post-mitigation lethality.
- No monolithic expansion of `ExplosionPredictor` or `MeleePredictor` with every precursor family.
- Every production change starts with a focused failing regression, then the smallest source-faithful implementation, then focused + full verification.
- Preserve current item/component fingerprints, server-authority tracking, inventory transaction semantics, and existing restoration behavior unless a test proves a targeted change is required.
- All exact-runtime hostile sequence tests must remove artificial setup delays between the final precursor actions.

---

## File Map

### New opportunity domain

- `src/client/java/dev/pixelied/survival/threat/opportunity/OpportunityFamily.java` — stable family enum.
- `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunity.java` — immutable precursor + projected damage contract.
- `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunityPredictor.java` — family predictor interface.
- `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunityRegistry.java` — merge/order/cap opportunity results.
- `src/client/java/dev/pixelied/survival/threat/opportunity/OpportunityTimelineAssembler.java` — build planning-only risk timeline without mutating actual observations.
- `src/client/java/dev/pixelied/survival/threat/opportunity/CrystalOpportunityPredictor.java` — legal place/detonate precursor.
- `src/client/java/dev/pixelied/survival/threat/opportunity/BedOpportunityPredictor.java` — explosive bed place/use precursor.
- `src/client/java/dev/pixelied/survival/threat/opportunity/RespawnAnchorOpportunityPredictor.java` — charged and charge/use precursor.
- `src/client/java/dev/pixelied/survival/threat/opportunity/TntMinecartOpportunityPredictor.java` — collision/fall/burning-arrow immediate paths.
- `src/client/java/dev/pixelied/survival/threat/opportunity/MeleeApproachOpportunityPredictor.java` — relative-motion entry into lethal melee/mace/spear reach.
- `src/client/java/dev/pixelied/survival/threat/opportunity/ProtectionContinuity.java` — reject actions that transiently remove the only authoritative death protection while latched.

### Shared explosion/damage primitives

- `src/client/java/dev/pixelied/survival/threat/ExplosionSpec.java` — source-accurate center/radius/source metadata.
- `src/client/java/dev/pixelied/survival/threat/ExplosionThreatFactory.java` — one shared raw explosion → `ThreatEvent` path.
- `src/client/java/dev/pixelied/survival/threat/SnapshotOcclusionView.java` — exact captured collision-box ray blocking and source-removal filtering.
- `src/client/java/dev/pixelied/survival/damage/VanillaDamageOracle.java` — narrow shared post-mitigation/timeline lethality facade.

### Existing files expected to change

- `core/EngineLimits.java` — add bounded opportunity cap with 4-arg compatibility constructor.
- `core/PredictionContext.java` — carry `SafetyMode` with 4-arg compatibility constructor.
- `core/WorldSnapshot.java` — block collision-box components with 4-arg compatibility constructor.
- `core/MinecraftCollisionShapeSnapshot.java` — capture every `VoxelShape` component AABB, preserve current envelope metadata for existing consumers.
- `core/MinecraftNearbyBlockSnapshotFactory.java` — attach exact collision components.
- `core/MinecraftWorldSnapshotFactory.java` — relevant entities, source-accurate explosion metadata, held/offhand evidence.
- `core/MinecraftMeleeSnapshotAdapter.java` — block interaction reach/offhand/environment evidence needed by opportunity predictors.
- `core/MinecraftTriggerableExplosionSnapshotFactory.java` — expose anchor charge state even at zero charge; retain actual triggerable metadata only when charged.
- `core/MinecraftSurvivalRuntime.java` — run actual predictors and opportunity predictors separately; generate candidates against planning risk timeline.
- `core/SurvivalEngine.java` — carry actual/planning risk separately and maintain protection latch/continuity.
- `timing/TimingSnapshot.java` and `timing/ServerTimingEstimator.java` — conservative observation-age window.
- `threat/ExplosionPredictor.java` — refactor through shared factory; age countdowns; source-accurate centers.
- `threat/MeleePredictor.java` — expose package-level source-faithful direct-hit builder or helper so approach opportunities reuse existing damage math instead of cloning it.
- `planner/SurvivalPlanner.java` — deadline semantics for opportunity projections and same-window `BEST_EFFORT` only when no earlier guarantee exists.
- `execution/DeathProtectionRestorationController.java` — no redesign; only accept stronger latch input if needed by focused regression.
- GameTest registration/resources only for newly added validation scenarios.

---

### Task 1: Add the Opportunity Domain Without Changing Behavior

**Files:**
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/OpportunityFamily.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunity.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunityPredictor.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/LethalOpportunityRegistry.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/OpportunityTimelineAssembler.java`
- Modify: `src/client/java/dev/pixelied/survival/core/EngineLimits.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/LethalOpportunityRegistryTest.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/OpportunityTimelineAssemblerTest.java`

**Interfaces:**
- Produces: `LethalOpportunity(String id, OpportunityFamily family, ThreatEvent projectedThreat, Confidence confidence, int actionDepth, Map<String,String> evidence)`.
- Produces: `LethalOpportunityPredictor#predict(PredictionContext) -> List<LethalOpportunity>`.
- Produces: `LethalOpportunityRegistry#predictAll(PredictionContext) -> List<LethalOpportunity>`.
- Produces: `OpportunityTimelineAssembler#assemble(ThreatTimeline actual, List<LethalOpportunity> opportunities, int maxThreats) -> ThreatTimeline`.
- `projectedThreat.id()` must start with `opportunity:`; actual threat IDs must never be overwritten by opportunity IDs.

- [ ] **Step 1: Write the registry RED**

```java
@Test
void registryOrdersByEarliestImpactThenWorstDamageAndCapsFailClosed() {
    EngineLimits limits = new EngineLimits(4, 32, 80, 128, 2);
    PredictionContext context = Fixtures.context(limits);
    LethalOpportunity late = Fixtures.opportunity("opportunity:test:late", 3, 30f);
    LethalOpportunity earlyLow = Fixtures.opportunity("opportunity:test:early-low", 1, 5f);
    LethalOpportunity earlyHigh = Fixtures.opportunity("opportunity:test:early-high", 1, 20f);

    LethalOpportunityRegistry registry = new LethalOpportunityRegistry(List.of(
        ignored -> List.of(late, earlyLow, earlyHigh)
    ));

    List<LethalOpportunity> result = registry.predictAll(context);
    assertEquals(2, result.size());
    assertEquals("opportunity:test:early-high", result.getFirst().id());
    assertEquals("opportunity:test:early-low", result.get(1).id());
}
```

- [ ] **Step 2: Run the focused RED**

Run from `projects/predictive-survival-26-1-2`:

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.LethalOpportunityRegistryTest'
```

Expected: compilation failure because the opportunity types do not exist.

- [ ] **Step 3: Implement the immutable domain and bounded registry**

```java
public record LethalOpportunity(
    String id,
    OpportunityFamily family,
    ThreatEvent projectedThreat,
    Confidence confidence,
    int actionDepth,
    Map<String, String> evidence
) {
    public LethalOpportunity {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(family, "family");
        Objects.requireNonNull(projectedThreat, "projectedThreat");
        Objects.requireNonNull(confidence, "confidence");
        evidence = Map.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (!id.startsWith("opportunity:")) throw new IllegalArgumentException("opportunity id prefix required");
        if (!projectedThreat.id().equals(id)) throw new IllegalArgumentException("projected threat id must equal opportunity id");
        if (actionDepth < 0) throw new IllegalArgumentException("actionDepth must be non-negative");
    }
}
```

Add `maxOpportunities` as the fifth `EngineLimits` component and preserve all current callers:

```java
public EngineLimits(int maxThreats, int maxPlannerCandidates, int maxProjectileHorizonTicks, int maxDecisionHistory) {
    this(maxThreats, maxPlannerCandidates, maxProjectileHorizonTicks, maxDecisionHistory, 128);
}
```

- [ ] **Step 4: Write and implement planning-timeline separation**

```java
@Test
void opportunityProjectionDoesNotReplaceActualThreatWithSameSourceFamily() {
    ThreatTimeline actual = new ThreatTimeline(List.of(Fixtures.threat("explosion:7", 2, 10f)));
    LethalOpportunity opportunity = Fixtures.opportunity("opportunity:crystal:7", 0, 40f);

    ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 8);

    assertEquals(1, actual.events().size());
    assertEquals(2, planning.events().size());
    assertTrue(planning.events().stream().anyMatch(e -> e.id().equals("explosion:7")));
    assertTrue(planning.events().stream().anyMatch(e -> e.id().equals("opportunity:crystal:7")));
}
```

Assembler ordering must match risk order: earliest impact, highest `rawDamage.max`, stable ID; use `ThreatOverflowCondenser.cap` for the final planning timeline so overflow remains fail-closed.

- [ ] **Step 5: Run both focused tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/pixelied/survival/threat/opportunity src/client/java/dev/pixelied/survival/core/EngineLimits.java src/test/java/dev/pixelied/survival/threat/opportunity
git commit -m 'feat: add lethal opportunity domain'
```

---

### Task 2: Plumb Actual vs Planning Risk Through Runtime and Engine

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/PredictionContext.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Modify: `src/client/java/dev/pixelied/survival/core/SurvivalEngine.java`
- Test: `src/test/java/dev/pixelied/survival/core/SurvivalEngineOpportunityFrameTest.java`
- Test: `src/test/java/dev/pixelied/survival/core/MinecraftSurvivalRuntimeOpportunityContractTest.java`

**Interfaces:**
- `PredictionContext` gains `SafetyMode safetyMode`; retain existing 4-arg constructor delegating to `SafetyMode.BALANCED`.
- `EngineFrame` becomes `(PredictionContext context, ThreatTimeline actualTimeline, List<LethalOpportunity> opportunities, ThreatTimeline planningTimeline, List<SurvivalAction> candidates)` and retains the old `(context, timeline, candidates)` constructor with `actualTimeline == planningTimeline` and no opportunities.
- Engine planning, `lethalWithoutDeathProtection`, danger fingerprints, candidate simulation, and restoration safety use `planningTimeline`; debug/reporting may include both actual and opportunity IDs.

- [ ] **Step 1: Write the frame-separation RED**

```java
@Test
void opportunityCanMakePlanningLethalWithoutPollutingActualTimeline() {
    ThreatTimeline actual = new ThreatTimeline(List.of());
    LethalOpportunity opportunity = Fixtures.lethalOpportunityAtTick("opportunity:test:burst", 1);
    ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 128);
    SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(
        Fixtures.context(), actual, List.of(opportunity), planning, List.of()
    );

    assertTrue(frame.actualTimeline().events().isEmpty());
    assertEquals(1, frame.opportunities().size());
    assertEquals("opportunity:test:burst", frame.planningTimeline().events().getFirst().id());
}
```

- [ ] **Step 2: Run the RED**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.core.SurvivalEngineOpportunityFrameTest'
```

Expected: compilation failure on the new `EngineFrame` shape.

- [ ] **Step 3: Implement compatibility constructors and engine timeline selection**

```java
public EngineFrame(PredictionContext context, ThreatTimeline timeline, List<SurvivalAction> candidates) {
    this(context, timeline, List.of(), timeline, candidates);
}
```

Replace every safety-critical `frame.timeline()` use with `frame.planningTimeline()`. Do not change existing runtime behavior yet: instantiate an empty `LethalOpportunityRegistry` and planning timeline must equal actual timeline byte-for-byte when no opportunities exist.

- [ ] **Step 4: Pass `SafetyMode` into capture**

Extend `RuntimeAdapter` without breaking test adapters:

```java
default EngineFrame capture(RescuePolicy policy, SafetyMode safetyMode) {
    Objects.requireNonNull(safetyMode, "safetyMode");
    return capture(policy);
}
```

`SurvivalEngine.tick()` calls `runtime.capture(liveConfig.rescuePolicy(), liveConfig.safetyMode())`; `MinecraftSurvivalRuntime` constructs `PredictionContext(..., safetyMode)`.

- [ ] **Step 5: Verify behavior-preserving plumbing**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.core.*' --tests 'dev.pixelied.survival.planner.*'
```

Expected: PASS with no opportunity predictors registered.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/pixelied/survival/core src/test/java/dev/pixelied/survival/core
git commit -m 'refactor: separate actual and planning risk timelines'
```

---

### Task 3: Capture Exact Vanilla Collision Components for Explosion Rays

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/WorldSnapshot.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftCollisionShapeSnapshot.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftNearbyBlockSnapshotFactory.java`
- Create: `src/client/java/dev/pixelied/survival/threat/SnapshotOcclusionView.java`
- Modify: `src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java`
- Test: `src/test/java/dev/pixelied/survival/core/MinecraftCollisionShapeComponentsTest.java`
- Test: `src/test/java/dev/pixelied/survival/threat/ExplosionCompoundShapeExposureTest.java`

**Interfaces:**
- `WorldSnapshot.BlockSnapshot` gains `List<AabbSnapshot> collisionBoxes` in world coordinates.
- Preserve the 4-arg block constructor by delegating `collisionBoxes = List.of()`.
- `MinecraftCollisionShapeSnapshot.capture(VoxelShape shape, BlockPos pos) -> List<AabbSnapshot>` captures each `VoxelShape#forAllBoxes` component exactly; current envelope properties remain for existing fall/projectile code.
- `SnapshotOcclusionView` blocks rays against exact component boxes first; only legacy snapshots with no components may fall back to confirmed `full_collision_cube`.

- [ ] **Step 1: Write a compound-shape RED where the envelope would be wrong**

```java
@Test
void rayThroughGapBetweenTwoCollisionComponentsRemainsVisible() {
    WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
        new Vec3Snapshot(1.5, 0.5, 0.5),
        "minecraft:test_split",
        true,
        List.of(
            new AabbSnapshot(1.0, 0.0, 0.0, 1.25, 1.0, 1.0),
            new AabbSnapshot(1.75, 0.0, 0.0, 2.0, 1.0, 1.0)
        ),
        Map.of("collision_min_x", "0", "collision_max_x", "1")
    );
    SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(split));

    assertFalse(view.blocksExplosionRay(new Vec3Snapshot(0.5, 0.5, 0.5), new Vec3Snapshot(2.5, 0.5, 0.5)));
}
```

- [ ] **Step 2: Run the focused RED**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.ExplosionCompoundShapeExposureTest'
```

Expected: compilation failure on collision components / `SnapshotOcclusionView`.

- [ ] **Step 3: Capture exact component AABBs**

```java
static List<AabbSnapshot> capture(VoxelShape shape, BlockPos pos) {
    List<AabbSnapshot> boxes = new ArrayList<>();
    shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AabbSnapshot(
        pos.getX() + minX, pos.getY() + minY, pos.getZ() + minZ,
        pos.getX() + maxX, pos.getY() + maxY, pos.getZ() + maxZ
    )));
    return List.copyOf(boxes);
}
```

Keep `MinecraftCollisionShapeSnapshot.write(...)` so old collision-envelope consumers remain stable; `MinecraftNearbyBlockSnapshotFactory.snapshot(...)` writes both the legacy properties and the exact component list.

- [ ] **Step 4: Extract occlusion from `ExplosionPredictor`**

Move `SnapshotOcclusionView` out of the predictor. Reuse the current slab intersection semantics, but test every exact collision component. Source-removal group filtering remains supported by constructing a view without matching blocks.

- [ ] **Step 5: Add runtime-shape unit cases**

Add representative captured-shape tests for a slab, stair, fence/wall, and trapdoor. Assert component counts and that disjoint gaps are not filled by an envelope.

- [ ] **Step 6: Run focused + existing explosion tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.core.MinecraftCollisionShapeComponentsTest' --tests 'dev.pixelied.survival.threat.Explosion*Test'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/dev/pixelied/survival/core/WorldSnapshot.java src/client/java/dev/pixelied/survival/core/MinecraftCollisionShapeSnapshot.java src/client/java/dev/pixelied/survival/core/MinecraftNearbyBlockSnapshotFactory.java src/client/java/dev/pixelied/survival/threat/SnapshotOcclusionView.java src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java src/test/java/dev/pixelied/survival/core src/test/java/dev/pixelied/survival/threat
git commit -m 'fix: preserve exact explosion collision geometry'
```

---

### Task 4: Factor One Shared Explosion/Damage Path

**Files:**
- Create: `src/client/java/dev/pixelied/survival/threat/ExplosionSpec.java`
- Create: `src/client/java/dev/pixelied/survival/threat/ExplosionThreatFactory.java`
- Create: `src/client/java/dev/pixelied/survival/damage/VanillaDamageOracle.java`
- Modify: `src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java`
- Test: `src/test/java/dev/pixelied/survival/threat/ExplosionThreatFactoryParityTest.java`
- Test: `src/test/java/dev/pixelied/survival/damage/VanillaDamageOracleTest.java`

**Interfaces:**
- `ExplosionSpec(Vec3Snapshot center, float radiusMin, float radiusMax, String sourceKey, boolean scalesWithDifficulty, boolean blockable, boolean removesSourceBeforeExplosion)`.
- `ExplosionThreatFactory#create(String id, TickWindow impact, Confidence confidence, ExplosionSpec spec, PredictionContext context, OcclusionView world) -> Optional<ThreatEvent>`.
- `VanillaDamageOracle#simulate(PlayerSnapshot, ThreatTimeline) -> TimelineResult`.
- `VanillaDamageOracle#lethalWithoutDeathProtection(PlayerSnapshot, ThreatTimeline) -> boolean` removes hand death protection only for the simulation and preserves every other player field.

- [ ] **Step 1: Capture current explosion behavior as parity tests**

```java
@Test
void sharedFactoryMatchesLegacyCrystalRawDamageAndFlags() {
    PredictionContext context = Fixtures.contextAt(new Vec3Snapshot(0.3, 0, 0.3));
    ExplosionSpec spec = new ExplosionSpec(new Vec3Snapshot(3, 0, 0), 6f, 6f,
        "minecraft:explosion", true, true, false);

    ThreatEvent event = new ExplosionThreatFactory().create(
        "opportunity:test:crystal", new TickWindow(0, 2), Confidence.POTENTIAL,
        spec, context, new SnapshotOcclusionView(context.world().blocks())
    ).orElseThrow();

    assertTrue(event.damage().flags().contains(DamageFlag.IS_EXPLOSION));
    assertEquals("minecraft:explosion", event.damage().sourceKey());
    assertTrue(event.damage().rawDamage().max() > 0f);
}
```

Also freeze parity for bed/anchor source keys, moving triggerable source/player projection, and removed-source exposure.

- [ ] **Step 2: Run the parity RED**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.ExplosionThreatFactoryParityTest'
```

Expected: compilation failure because shared factory/spec do not exist.

- [ ] **Step 3: Move raw explosion math into `ExplosionThreatFactory`**

The factory owns `ExplosionExposure`, radius envelope handling, player/source projection across the supplied impact window when requested, `DamageFlag.IS_EXPLOSION`, source position, and `ThreatEvent` construction. `ExplosionPredictor` becomes parsing/identification + factory calls; do not duplicate `rawEntityDamage` or exposure math in opportunity classes.

- [ ] **Step 4: Add the post-mitigation oracle**

```java
public boolean lethalWithoutDeathProtection(PlayerSnapshot player, ThreatTimeline timeline) {
    PlayerSnapshot unprotected = PlayerSnapshots.withDeathProtection(player, DeathProtectionSnapshot.none());
    return !timelineSimulator.simulate(unprotected, timeline).survived();
}
```

If no `PlayerSnapshots` helper exists, keep the exact reconstruction private inside `VanillaDamageOracle`; do not create a generic copy utility unless at least two callers need it.

- [ ] **Step 5: Verify current mitigation order remains unchanged**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.damage.*' --tests 'dev.pixelied.survival.threat.Explosion*Test'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/pixelied/survival/threat src/client/java/dev/pixelied/survival/damage src/test/java/dev/pixelied/survival/threat src/test/java/dev/pixelied/survival/damage
git commit -m 'refactor: share vanilla explosion and lethality simulation'
```

---

### Task 5: Make Countdown Timing Server-Relative and Hidden-State Honest

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/timing/TimingSnapshot.java`
- Modify: `src/client/java/dev/pixelied/survival/timing/ServerTimingEstimator.java`
- Modify: `src/client/java/dev/pixelied/survival/core/PredictionContext.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java`
- Modify: `src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java`
- Delete only if no longer referenced: `src/client/java/dev/pixelied/survival/mixin/PrimedTntAccessor.java`
- Modify only if accessor deleted: `src/main/resources/predictive_survival.client.mixins.json`
- Test: `src/test/java/dev/pixelied/survival/timing/ObservationAgeWindowTest.java`
- Test: `src/test/java/dev/pixelied/survival/threat/NetworkAgedExplosionDeadlineTest.java`
- Test: `src/test/java/dev/pixelied/survival/core/HiddenExplosionStatePolicyTest.java`

**Interfaces:**
- `TimingSnapshot#observationAgeWindow() -> TickWindow` returns relative server-tick age, never negative.
- Countdown impact bounds use `earliest = max(0, observedFuse - age.latest)` and `latest = max(0, observedFuse - age.earliest)`.
- `PredictionContext.safetyMode()` selects hidden-NBT policy.

- [ ] **Step 1: Write the timing RED**

```java
@Test
void observedFuseIsAgedBeforePlannerDeadline() {
    TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));
    TickWindow age = timing.observationAgeWindow();
    TickWindow serverFuse = ExplosionTiming.ageCountdown(5, age);

    assertTrue(serverFuse.earliest() < 5);
    assertEquals(5 - age.earliest(), serverFuse.latest());
}
```

- [ ] **Step 2: Implement observation age from RTT/jitter**

Use the same 50 ms server-tick basis as `ServerTimingEstimator`, but model inbound state age rather than outbound action arrival:

```java
public TickWindow observationAgeWindow() {
    double center = rttMs / 2d;
    long earliest = floorServerTicks(Math.max(0d, center - jitterMs));
    long latest = ceilServerTicks(center + jitterMs) + 1L;
    return new TickWindow(earliest, latest);
}
```

Keep the `+1` scheduling/tick-phase safety tick. Add private floor helper next to the existing ceil helper.

- [ ] **Step 3: Age synchronized countdowns**

`ExplosionPredictor` uses the timing window for `PrimedTnt.DATA_FUSE_ID`, Wither invulnerability ticks, and any other synchronized countdown. Creeper swelling must remain bounded/potential because `maxSwell` is not synchronized.

- [ ] **Step 4: Remove false authority from unsynchronized TNT power**

Do not read `PrimedTnt.explosionPower` through a mixin and label it exact. Snapshot:

```java
properties.put("explosion_radius_default", "4.0");
properties.put("explosion_radius_hidden_min", "0.0");
properties.put("explosion_radius_hidden_max", "128.0");
properties.put("server_hidden_explosion_power", "true");
```

Resolver behavior:

```java
if (context.safetyMode() == SafetyMode.SAFE) {
    radius = new RadiusRange(hiddenMin, hiddenMax);
    confidence = lessCertain(confidence, Confidence.BOUNDED);
} else {
    radius = RadiusRange.exact(defaultRadius);
    confidence = lessCertain(confidence, Confidence.POTENTIAL);
}
```

Apply the same policy to TNT-minecart power base/speed factor and creeper hidden custom radius. Do not widen BALANCED to maximum custom NBT.

- [ ] **Step 5: Capture source-accurate centers/relevance**

- Primed TNT explosion center uses entity X/Z and source-confirmed Y (`getY(0.0625)` semantics).
- Include ignited creepers even when `getSwellDir() <= 0`.
- Include Wither while invulnerable, with radius 7 and eye-Y center.
- Include every TNT minecart as opportunity-relevant even when unprimed.

- [ ] **Step 6: Run timing/explosion policy tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.timing.*' --tests 'dev.pixelied.survival.threat.NetworkAgedExplosionDeadlineTest' --tests 'dev.pixelied.survival.core.HiddenExplosionStatePolicyTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/dev/pixelied/survival/timing src/client/java/dev/pixelied/survival/core/PredictionContext.java src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java src/client/java/dev/pixelied/survival/mixin/PrimedTntAccessor.java src/main/resources/predictive_survival.client.mixins.json src/test/java/dev/pixelied/survival
git commit -m 'fix: age explosion deadlines and model hidden server state'
```

If `PrimedTntAccessor.java` is already absent at execution time because canonical base advanced, omit it from `git add` and verify the mixin list instead of recreating/deleting blindly.

---

### Task 6: Add Crystal Placement/Detonation Opportunities

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/CrystalOpportunityPredictor.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/CrystalOpportunityPredictorTest.java`
- GameTest later in Task 12: `src/gametest/java/dev/pixelied/survival/validation/CrystalBurstSequenceValidationScenarios.java`

**Interfaces:**
- Remote player properties add `block_interaction_range`, `main_hand_item_key`, `offhand_item_key` while preserving existing `weapon_key`.
- Crystal legality matches `EndCrystalItem.useOn`: support is obsidian/bedrock; block immediately above is empty; the world-space AABB `[x,y,z -> x+1,y+2,z+1]` above support contains no entity; placement center is `(x+0.5, y+1, z+0.5)`.
- Predictor emits at most the configured opportunity cap and uses broad-phase support/reach before exact damage.

- [ ] **Step 1: Write the no-crystal-existing RED**

```java
@Test
void lethalLegalSupportCreatesOpportunityBeforeCrystalEntityExists() {
    PredictionContext context = Fixtures.contextWithRemotePlayerAndBlocks(
        Fixtures.remotePlayer("attacker", new Vec3Snapshot(3.5, 0, 0.5), Map.of(
            "block_interaction_range", "4.5",
            "main_hand_item_key", "minecraft:end_crystal"
        )),
        List.of(Fixtures.fullBlock(2, 0, 0, "minecraft:obsidian"))
    );

    List<LethalOpportunity> result = new CrystalOpportunityPredictor().predict(context);

    assertEquals(1, result.size());
    assertEquals(OpportunityFamily.CRYSTAL, result.getFirst().family());
    assertTrue(result.getFirst().projectedThreat().damage().rawDamage().max() > 0f);
}
```

- [ ] **Step 2: Add false-positive REDs**

Write separate tests for stone support, blocked above, entity occupying the two-block placement AABB, support outside block interaction reach, and non-lethal exact exposure/mitigation. Each must return no qualifying lethal opportunity.

- [ ] **Step 3: Run REDs**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.CrystalOpportunityPredictorTest'
```

Expected: FAIL/compile failure.

- [ ] **Step 4: Implement broad phase + exact legality**

Use block-center support positions to recover integer `BlockPos` coordinates. Do not scan every block for every player: iterate only captured obsidian/bedrock blocks whose support center is within `block_interaction_range + conservative relative-motion allowance` of the attacker AABB.

- [ ] **Step 5: Use shared explosion factory and oracle**

Construct radius-6 `ExplosionSpec`; project through `ExplosionThreatFactory`; discard if `VanillaDamageOracle.lethalWithoutDeathProtection` says the resulting one-event timeline is non-lethal.

Strict item evidence:

```java
boolean visibleCrystal = holds(attacker, "minecraft:end_crystal");
if (!visibleCrystal && context.safetyMode() != SafetyMode.SAFE) continue;
```

SAFE may retain a legal lethal support opportunity without visible held crystal because same-window slot change/place packets can precede the next client observation. Evidence must record `visible_crystal=true/false`.

- [ ] **Step 6: Register predictor and verify planning pre-arm selection**

Add `new CrystalOpportunityPredictor()` to the opportunity registry in `MinecraftSurvivalRuntime`. Add an engine test where actual timeline is empty, planning timeline contains the crystal opportunity, and a one-tick hotbar death-protection route is selected before any `EndCrystal` snapshot exists.

- [ ] **Step 7: Run focused + engine tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.CrystalOpportunityPredictorTest' --tests 'dev.pixelied.survival.core.*Opportunity*Test'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java src/client/java/dev/pixelied/survival/threat/opportunity/CrystalOpportunityPredictor.java src/test/java/dev/pixelied/survival
git commit -m 'feat: pre-arm for lethal crystal placement opportunities'
```

---

### Task 7: Add Bed and Respawn-Anchor Precursor Graphs

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftTriggerableExplosionSnapshotFactory.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/BedOpportunityPredictor.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/RespawnAnchorOpportunityPredictor.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/BedOpportunityPredictorTest.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/RespawnAnchorOpportunityPredictorTest.java`

**Interfaces:**
- Existing anchor snapshots expose `anchor_explodes`, `anchor_charge` for charge 0..4; actual `triggerable=true` only when charge > 0.
- Remote player evidence includes environment bed rule and main/offhand item keys.
- Bed projected explosion removes both bed halves before exposure.
- Anchor projected explosion removes the anchor and applies its source-confirmed water-resistance override to the source position when horizontal flowing/source water or water above would affect the custom `ExplosionDamageCalculator`.

- [ ] **Step 1: Write action-depth REDs**

```java
@Test
void chargedAnchorIsOneInteractionOpportunity() {
    LethalOpportunity opportunity = predictAnchor(charge(4), attackerHolding("minecraft:air")).getFirst();
    assertEquals(1, opportunity.actionDepth());
}

@Test
void unchargedAnchorRequiresChargeThenUse() {
    LethalOpportunity opportunity = predictAnchor(charge(0), attackerHolding("minecraft:glowstone")).getFirst();
    assertEquals(2, opportunity.actionDepth());
}
```

For BALANCED, charge-0 requires visible glowstone evidence. SAFE may conservatively preserve the setup if the remaining hostile packet sequence fits the authority horizon and the anchor is reachable.

- [ ] **Step 2: Write bed source-removal RED**

Place the target behind the bed halves so treating the bed as cover would make the explosion non-lethal. Assert the projected bed explosion removes both halves and remains lethal when vanilla would.

- [ ] **Step 3: Run REDs**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.BedOpportunityPredictorTest' --tests 'dev.pixelied.survival.threat.opportunity.RespawnAnchorOpportunityPredictorTest'
```

Expected: FAIL.

- [ ] **Step 4: Implement bed legality from 26.1.2 source**

Use `BedRule.explodes()`, block interaction reach, source-confirmed two-block bed placement geometry, and head-center radius-5 `bad_respawn_point` explosion. Existing placed explosive bed use is action depth 1; place+use is action depth 2.

- [ ] **Step 5: Implement anchor legality and water semantics**

Use `RESPAWN_ANCHOR_WORKS`, exact charge, visible glowstone evidence, block interaction reach, radius 5, and source-removal ordering. Reproduce `RespawnAnchorBlock.isWaterThatWouldFlow` for neighboring water and water-above handling in the projected explosion occlusion/resistance model; do not treat ordinary water everywhere as anchor source resistance.

- [ ] **Step 6: Register both predictors and verify false positives**

Tests must prove no opportunity in dimensions where bed/anchor works normally, no opportunity out of reach, and no latch when post-mitigation projected damage is non-lethal.

- [ ] **Step 7: Run focused + explosion tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.*Bed*' --tests 'dev.pixelied.survival.threat.opportunity.*Anchor*' --tests 'dev.pixelied.survival.threat.Explosion*Test'
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/client/java/dev/pixelied/survival/core/MinecraftTriggerableExplosionSnapshotFactory.java src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java src/client/java/dev/pixelied/survival/threat/opportunity/BedOpportunityPredictor.java src/client/java/dev/pixelied/survival/threat/opportunity/RespawnAnchorOpportunityPredictor.java src/test/java/dev/pixelied/survival
git commit -m 'feat: model lethal bed and anchor precursors'
```

---

### Task 8: Cover Every Source-Confirmed TNT-Minecart Burst Path

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/TntMinecartOpportunityPredictor.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/TntMinecartOpportunityPredictorTest.java`
- Test: `src/test/java/dev/pixelied/survival/threat/TntMinecartPowerEnvelopeTest.java`

**Interfaces:**
- Unprimed minecarts remain captured.
- Predictor covers: horizontal collision with speed² >= 0.01, burning-arrow direct hit, fall distance >= 3, and destroy-by-igniting-source path that primes a short random fuse.
- Exact immediate explosion power for ordinary vanilla defaults follows `4.0 + 1.0 * random[0,1) * 1.5 * min(sqrt(speed²),5)`; SAFE hidden-NBT bounds use source clamps 0..128 for base/factor, BALANCED uses default base/factor and marks uncertainty where random output remains bounded.

- [ ] **Step 1: Write horizontal-collision RED**

```java
@Test
void unprimedFastMinecartProjectedIntoCollisionCreatesImmediateOpportunity() {
    WorldSnapshot.EntitySnapshot cart = Fixtures.tntMinecart(
        new Vec3Snapshot(1.5, 0, 0.5), new Vec3Snapshot(0.2, 0, 0), false
    );
    PredictionContext context = Fixtures.contextWithEntitiesAndBlocks(
        List.of(cart), List.of(Fixtures.fullBlock(2, 0, 0, "minecraft:stone"))
    );

    LethalOpportunity opportunity = new TntMinecartOpportunityPredictor().predict(context).getFirst();
    assertEquals(OpportunityFamily.TNT_MINECART, opportunity.family());
    assertEquals(0, opportunity.projectedThreat().impact().earliest());
}
```

- [ ] **Step 2: Add fall and burning-arrow REDs**

Snapshot minecart properties must carry observed `fall_distance` and nearby projectile ownership/fire state needed to establish the path. Tests assert no false positive for non-burning arrow and fall distance < 3.

- [ ] **Step 3: Run REDs**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.TntMinecartOpportunityPredictorTest'
```

Expected: FAIL.

- [ ] **Step 4: Implement collision projection using exact captured collision boxes**

Translate the minecart AABB by its observed velocity for the immediate horizon; intersect against exact block collision components. Do not invent collisions from envelope-only partial shapes.

- [ ] **Step 5: Implement bounded raw radius from actual 26.1.2 formula**

For known default base/factor:

```java
double cappedSpeed = Math.min(Math.sqrt(speedSquared), 5.0);
float minRadius = 4.0f;
float maxRadius = (float)(4.0 + 1.0 * 1.5 * cappedSpeed);
```

Because `nextDouble()` is `< 1`, using the closed upper bound is conservative. SAFE hidden-NBT max uses clamped base/factor maxima; BALANCED does not.

- [ ] **Step 6: Register and run focused tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.TntMinecartOpportunityPredictorTest' --tests 'dev.pixelied.survival.threat.TntMinecartPowerEnvelopeTest'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java src/client/java/dev/pixelied/survival/threat/opportunity/TntMinecartOpportunityPredictor.java src/test/java/dev/pixelied/survival
git commit -m 'fix: guard instant tnt minecart explosion paths'
```

---

### Task 9: Predict Entry Into Lethal Melee, Mace, and Spear Range

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/threat/MeleePredictor.java`
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/MeleeApproachOpportunityPredictor.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Test: `src/test/java/dev/pixelied/survival/threat/opportunity/MeleeApproachOpportunityPredictorTest.java`

**Interfaces:**
- Extract a package-visible/source-faithful helper from `MeleePredictor` that builds the damage/source semantics for an attacker snapshot without requiring current range; current actual-threat range gating stays in `MeleePredictor`.
- Approach predictor computes earliest AABB reach intersection from relative motion over the authority horizon and asks the existing melee damage builder for projected damage.
- Do not duplicate mace or spear formulas.

- [ ] **Step 1: Write approaching-mace RED**

```java
@Test
void lethalMaceApproachAppearsBeforeCurrentReach() {
    WorldSnapshot.EntitySnapshot attacker = Fixtures.remoteMacePlayer(
        new AabbSnapshot(5.0, 0, 0, 5.6, 1.8, 0.6),
        new Vec3Snapshot(-1.5, 0, 0),
        Map.of("attack_range", "3.0", "fall_distance_max", "20.0")
    );

    LethalOpportunity opportunity = new MeleeApproachOpportunityPredictor().predict(Fixtures.context(attacker)).getFirst();

    assertTrue(opportunity.projectedThreat().impact().earliest() > 0);
    assertTrue(opportunity.projectedThreat().damage().flags().contains(DamageFlag.IS_MACE_SMASH));
}
```

- [ ] **Step 2: Add spear and ordinary melee false-positive tests**

- approaching kinetic spear uses the existing `SpearSnapshot` damage range;
- moving away does not produce an approach opportunity;
- line-of-sight explicitly false does not produce one;
- projected entry after `maxProjectileHorizonTicks` does not produce one;
- non-lethal post-mitigation melee does not trigger the death-protection opportunity.

- [ ] **Step 3: Run REDs**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.opportunity.MeleeApproachOpportunityPredictorTest'
```

Expected: FAIL.

- [ ] **Step 4: Extract melee damage construction without changing actual-threat behavior**

Refactor `MeleePredictor.buildThreat` into:

```java
Optional<ThreatEvent> buildThreat(PredictionContext context, EntitySnapshot attacker, boolean requireCurrentRange)
```

or an equivalent focused helper. Existing `predict()` always passes `true`; approach predictor passes `false` only after it has independently proven a future legal range-entry window.

- [ ] **Step 5: Solve conservative range-entry timing**

For player attacks, translate attacker and target AABBs by relative observed velocity for each integer tick from 1 through the authority/horizon cap and choose the first tick whose AABB distance <= source-confirmed attack range. Do not assume continuous sub-tick motion is server-accepted; integer server-tick scanning is intentionally conservative and easy to verify.

- [ ] **Step 6: Register and verify**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.threat.Melee*Test' --tests 'dev.pixelied.survival.threat.opportunity.MeleeApproachOpportunityPredictorTest'
```

Expected: PASS with current-range `MeleePredictor` tests unchanged.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/dev/pixelied/survival/threat/MeleePredictor.java src/client/java/dev/pixelied/survival/threat/opportunity/MeleeApproachOpportunityPredictor.java src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java src/test/java/dev/pixelied/survival
git commit -m 'feat: pre-arm before lethal melee range entry'
```

---

### Task 10: Enforce the Protection Safety Latch Without Rewriting Restoration

**Files:**
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/ProtectionContinuity.java`
- Modify: `src/client/java/dev/pixelied/survival/core/SurvivalEngine.java`
- Modify only if focused test requires input shape: `src/client/java/dev/pixelied/survival/execution/DeathProtectionRestorationController.java`
- Test: `src/test/java/dev/pixelied/survival/core/ProtectionSafetyLatchTest.java`
- Test: `src/test/java/dev/pixelied/survival/execution/DeathProtectionRestorationControllerTest.java`

**Interfaces:**
- `ProtectionContinuity#preservesAuthoritativeProtection(PlayerSnapshot player, SurvivalAction action) -> boolean`.
- Latch condition is `VanillaDamageOracle.lethalWithoutDeathProtection(player, frame.planningTimeline())`.
- While latch condition is true, filter out any action that transiently replaces the only protected hand before an equivalent protection is authoritative.
- Existing restoration controller's safe-window hysteresis remains the disarm grace mechanism.

- [ ] **Step 1: Write the continuity RED**

```java
@Test
void activeLatchRejectsShieldRouteThatWouldReplaceOnlyTotemHand() {
    PlayerSnapshot player = Fixtures.playerWithMainHandTotemOnly();
    SurvivalAction.RaiseShield replaceMain = Fixtures.mainHandShieldRoute();

    assertFalse(ProtectionContinuity.preservesAuthoritativeProtection(player, replaceMain));
}
```

Also test: offhand shield while mainhand totem remains is allowed; adding a second totem is allowed; NoAction is allowed.

- [ ] **Step 2: Write restoration anti-oscillation RED**

```java
@Test
void opportunityRefreshKeepsRestorationGraceResetUntilRiskIsGone() {
    // arm checkpoint, present lethal opportunity for several updates, clear it for less than
    // nextPacketProcessingWindow grace, reintroduce it, and assert no SelectHotbar restore command.
}
```

Use the existing controller public `update(...)` API; do not reach into private state.

- [ ] **Step 3: Implement latch-derived candidate filtering**

In `SurvivalEngine.filteredCandidates(frame)`:

```java
boolean latchRequired = damageOracle.lethalWithoutDeathProtection(
    frame.context().player(), frame.planningTimeline()
);
if (latchRequired && !ProtectionContinuity.preservesAuthoritativeProtection(frame.context().player(), candidate)) {
    continue;
}
```

`maintainRestoration` receives this same latch-derived risk signal, replacing any weaker actual-only lethality input.

- [ ] **Step 4: Keep same-window best effort honest**

`SurvivalPlanner` may return `BEST_EFFORT` for an opportunity whose earliest hostile processing ties or beats rescue authority, but the reason must say the protection cannot be guaranteed from the current observation. A precursor that existed earlier but was ignored is not allowed to be relabeled success by a cooperative test.

- [ ] **Step 5: Run engine/restoration/planner tests**

```bash
./gradlew --no-daemon test --tests 'dev.pixelied.survival.core.ProtectionSafetyLatchTest' --tests 'dev.pixelied.survival.execution.DeathProtectionRestorationControllerTest' --tests 'dev.pixelied.survival.planner.*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/client/java/dev/pixelied/survival/threat/opportunity/ProtectionContinuity.java src/client/java/dev/pixelied/survival/core/SurvivalEngine.java src/client/java/dev/pixelied/survival/execution/DeathProtectionRestorationController.java src/client/java/dev/pixelied/survival/planner/SurvivalPlanner.java src/test/java/dev/pixelied/survival
git commit -m 'fix: latch authoritative protection across burst danger'
```

If `DeathProtectionRestorationController.java` remains unchanged because the existing behavior passes the new regression, omit it from the commit. Do not edit it merely to satisfy the plan's expected-file list.

---

### Task 11: Bound Opportunity Cost and Audit Every Supported Damage Family

**Files:**
- Create: `src/client/java/dev/pixelied/survival/threat/opportunity/OpportunityCache.java` only if the measurement in Step 2 shows repeated geometry work exceeds the budget below.
- Modify: family opportunity predictors to use indexed/bounded candidate scans.
- Modify: `src/client/java/dev/pixelied/survival/ThreatDirtyTracker.java` only if additional invalidation categories are required.
- Create: `src/test/java/dev/pixelied/survival/threat/opportunity/OpportunityBudgetTest.java`
- Create: `projects/predictive-survival-26-1-2/INSTANT_BURST_AUDIT.md`

**Interfaces:**
- Maximum opportunities: `EngineLimits.maxOpportunities()`.
- Every family must stop expensive narrow-phase work after a deterministic per-family candidate budget derived from `maxOpportunities`; overflow emits a conservative overflow opportunity instead of silently returning safe.
- Audit document must enumerate every currently supported actual damage family and classify it as `actual-lead-time`, `opportunity-modeled`, or `no-observable-precursor`, with the exact 26.1.2 class/method evidence.

- [ ] **Step 1: Write bounded-work RED**

```java
@Test
void thousandsOfCrystalSupportsDoNotCauseUnboundedExactExposureCalls() {
    CountingExplosionThreatFactory factory = new CountingExplosionThreatFactory();
    CrystalOpportunityPredictor predictor = new CrystalOpportunityPredictor(factory);
    PredictionContext context = Fixtures.contextWithCrystalField(4_000, new EngineLimits(128, 32, 80, 128, 64));

    List<LethalOpportunity> result = predictor.predict(context);

    assertTrue(factory.exactExposureCalls() <= 64);
    assertTrue(result.size() <= 64);
}
```

If dependency injection is inappropriate for the final class, expose a package-private test constructor; do not add production counters.

- [ ] **Step 2: Measure before adding cache complexity**

Run a JUnit timing fixture with 4,000 nearby candidate blocks and 16 remote players for 200 predictor iterations after JVM warm-up. Record median and p95 in the test output. Acceptance for the opportunity layer alone on CI-class hardware: median < 2 ms and p95 < 5 ms per iteration. These are regression guards, not claims about full Minecraft frame time.

- [ ] **Step 3: Add cache only if the measured budget fails**

If needed, cache immutable block candidate geometry keyed by a stable nearby-block fingerprint and invalidate on `ThreatDirtyTracker` block/entity update plus local movement beyond one block cell. Never cache player health/equipment/damage results across frames.

- [ ] **Step 4: Perform the source-completeness audit**

Audit the actual supported entity/damage families already captured by `MinecraftWorldSnapshotFactory` and predictor registries:

- primed TNT, creeper, Wither spawn;
- End Crystal, bed, respawn anchor, TNT minecart;
- player/mob melee, mace, spear;
- arrow/spectral arrow/trident/thrown spear/llama spit;
- fireball/small fireball/dragon fireball/wither skull;
- wind charge/breeze wind charge;
- firework rocket;
- splash/lingering potions and area-effect clouds;
- ender pearl self-damage;
- shulker bullet;
- evoker fangs, guardian beam, Warden sonic boom, lightning;
- fall/falling block/contact/environment/reactive/status families.

For each family, inspect the supplied 26.1.2 source and write one row with: final damage method, first client-observable precursor, whether the precursor provides guaranteed lead time, and chosen handling. Do not add a projectile-release predictor solely because an attacker holds a bow/crossbow; add one only if synchronized remote use/charge state establishes a legal release window tightly enough to improve authority timing without inventing server state.

- [ ] **Step 5: Add any source-proven missing precursor as a separate focused predictor/test inside this task**

The audit itself determines whether the current family set is complete. Any added predictor must follow the same `LethalOpportunityPredictor` interface, get its own JUnit class named `<Family>OpportunityPredictorTest`, and be registered explicitly. The audit row must name that class. If no additional source-proven precursor exists, the audit must state `no additional predictor required` for every reviewed family and the code remains unchanged.

- [ ] **Step 6: Run budget + complete unit suite**

```bash
./gradlew --no-daemon test
```

Expected: PASS and opportunity budget within guardrails.

- [ ] **Step 7: Commit**

```bash
git add src/client/java/dev/pixelied/survival/threat/opportunity src/client/java/dev/pixelied/survival/ThreatDirtyTracker.java src/test/java/dev/pixelied/survival/threat/opportunity INSTANT_BURST_AUDIT.md
git commit -m 'test: bound and audit burst opportunity coverage'
```

Use the actual audit path `projects/predictive-survival-26-1-2/INSTANT_BURST_AUDIT.md` when running `git add` from repository root.

---

### Task 12: Differentially Validate Damage and Run Zero-Delay Hostile GameTests

**Files:**
- Create: `src/gametest/java/dev/pixelied/survival/validation/ExplosionExposureDifferentialValidationScenarios.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/CrystalBurstSequenceValidationScenarios.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/BedAnchorBurstSequenceValidationScenarios.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/TntMinecartBurstSequenceValidationScenarios.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/MeleeBurstSequenceValidationScenarios.java`
- Create: `src/gametest/java/dev/pixelied/survival/validation/NetworkAgedFuseValidationScenarios.java`
- Modify: GameTest suite registration class/resources only as required by the existing project pattern.
- Update: `VALIDATION.md`

**Interfaces:**
- Differential scenarios record predicted raw/post-mitigation result before triggering the exact vanilla server event, then compare health/absorption/death-protection consumption after the server processes it.
- Hostile sequences use consecutive server actions without arbitrary `waitTicks` between setup and final trigger.

- [ ] **Step 1: Add exact explosion-exposure matrix scenarios**

At minimum cover open air plus source-block arrangements containing slab, stair, fence/wall, open/closed trapdoor, and a compound/disjoint shape. For each, compare predicted raw damage to actual vanilla damage within a tight float tolerance justified by exact runtime (`<= 1e-4f` unless runtime evidence requires a larger named tolerance).

Pseudo-pattern using the project's existing validation harness:

```java
float predicted = predictor.predict(context).getFirst().damage().rawDamage().max();
float before = player.getHealth() + player.getAbsorptionAmount();
triggerServerExplosion(level, center, radius);
helper.runAfterDelay(1, () -> {
    float actual = before - (player.getHealth() + player.getAbsorptionAmount());
    assertNear(predicted, actual, 1.0E-4f);
    helper.succeed();
});
```

The one post-event tick is observation/verification, not a pre-detonation delay.

- [ ] **Step 2: Add mitigation matrix**

Runtime scenarios cover armor/toughness, Protection/Blast Protection, Resistance, absorption, shield/blocking where source permits, hurt cooldown/`lastHurt`, and vanilla totem consumption. Expected result is the simulator's final state, not a hand-written magic number except where the vanilla formula is independently trivial.

- [ ] **Step 3: Add crystal zero-delay sequence**

Required order:

```text
no crystal exists
capture precursor / engine chooses protection
confirm protection server-authoritative
server processes attacker crystal placement
server processes immediate attacker crystal damage
verify explosion/death-protection result
```

Do not insert a multi-tick pause between placement and attack. The test passes only if protection was established from the precursor, not from observing the spawned crystal.

- [ ] **Step 4: Add bed/anchor/TNT-minecart/melee zero-delay sequences**

- charged anchor: interact as soon as server-authoritative protection is confirmed;
- charge-then-use anchor: back-to-back legal actions;
- explosive bed: place/use without artificial observation delay;
- TNT minecart: collision and one other immediate path (burning arrow or fall) without priming wait;
- melee/mace/spear: attacker crosses predicted range and attacks at first legal server tick.

- [ ] **Step 5: Add network-aged fuse runtime validation**

Construct a server-side fused threat, capture a deliberately delayed client observation using the validation harness, and assert the predictor's earliest bound is no later than the actual server detonation tick.

- [ ] **Step 6: Compile and run exact-runtime GameTests**

```bash
./gradlew --no-daemon compileGametestJava processGametestResources
xvfb-run -a ./gradlew --no-daemon --console=plain runClientGameTest
```

Expected: PASS.

- [ ] **Step 7: Update validation claims**

`VALIDATION.md` must distinguish:

- runtime-confirmed exact behavior;
- source-confirmed bounded behavior;
- hidden server-NBT limitations;
- BALANCED vs SAFE hidden-state policy;
- no-observable-precursor families from `INSTANT_BURST_AUDIT.md`.

Do not say “perfect” without the observability qualifier from the design spec.

- [ ] **Step 8: Commit**

```bash
git add projects/predictive-survival-26-1-2/src/gametest projects/predictive-survival-26-1-2/VALIDATION.md
git commit -m 'test: validate instant burst survival against vanilla runtime'
```

---

### Task 13: Final Full Verification, CI, Artifact Audit, and Review

**Files:**
- Modify only if verification reveals a source-confirmed mismatch: the smallest responsible production/test file.
- Update: `VALIDATION.md` with final run evidence.

**Interfaces:**
- No new production interfaces in this task.
- Completion requires green local/CI evidence on the exact final head.

- [ ] **Step 1: Run the complete local unit/build gate**

```bash
cd projects/predictive-survival-26-1-2
./gradlew --no-daemon clean test build
./gradlew --no-daemon compileGametestJava processGametestResources
```

Expected: both commands exit 0.

- [ ] **Step 2: Run exact-runtime GameTests**

```bash
xvfb-run -a ./gradlew --no-daemon --console=plain runClientGameTest
```

Expected: exit 0 with every old and new scenario green.

- [ ] **Step 3: Verify production JAR isolation**

```bash
jar_file=$(find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-sources.jar' | head -n 1)
test -n "$jar_file"
! jar tf "$jar_file" | grep -q '^dev/pixelied/survival/validation/'
```

Expected: no validation classes in production JAR.

- [ ] **Step 4: Run repository workspace validation from repository root**

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

Expected: PASS.

- [ ] **Step 5: Push branch and open/refresh PR against `project/predictive-survival-26-1-2`**

The PR body must state that this branch intentionally supersedes the design direction of PR #40 without merging its production changes, enumerate exact-runtime zero-delay regressions, and link the design + implementation plan.

- [ ] **Step 6: Inspect GitHub Actions on the exact head**

Required workflow steps from `.github/workflows/predictive-survival-26-1-2-ci.yml`:

- Gradle 9.5.1 wrapper validation;
- Java 25 `clean test build`;
- client GameTest compilation;
- exact-runtime client GameTests;
- production JAR isolation;
- complete deliverables packaging;
- both artifact uploads.

Do not claim completion from a stale prior run.

- [ ] **Step 7: Download and audit both CI artifacts**

Verify:

```bash
sha256sum -c SHA256SUMS.txt
jar tf predictive-survival-*.jar | grep '^dev/pixelied/survival/validation/' && exit 1 || true
```

Inspect source ZIP for the approved design, this plan, `INSTANT_BURST_AUDIT.md`, updated validation doc, project source, and CI workflow; reject build/run/.gradle/runtime debris.

- [ ] **Step 8: Final source review against the spec**

Explicitly check every acceptance criterion in `docs/superpowers/specs/2026-08-25-predictive-survival-instant-burst-design.md`. In particular verify:

- no-crystal-existing pre-arm sequence;
- bed/anchor generalized opportunities;
- every source-confirmed immediate TNT-minecart path;
- network-aged countdowns;
- approaching melee/mace/spear;
- exact compound collision exposure;
- unchanged/source-faithful damage ordering;
- latch continuity and restoration hysteresis;
- bounded false positives and opportunity cost;
- honest hidden server state;
- complete hazard-family audit.

- [ ] **Step 9: Request code review before merge**

Invoke `superpowers:requesting-code-review`; resolve source-faithful findings, rerun the smallest affected tests, then rerun the full gate before any final completion claim.

- [ ] **Step 10: Final verification commit only if documentation evidence changed**

```bash
git add projects/predictive-survival-26-1-2/VALIDATION.md
git commit -m 'docs: record instant burst hardening verification'
```

If `VALIDATION.md` already contains the exact final-head evidence and there is no diff, do not create an empty commit.

---

## Plan Self-Review Result

- **Spec coverage:** every design section is assigned: opportunity separation (Tasks 1–2), exact collision/explosion/damage semantics (Tasks 3–5), crystal/bed/anchor/minecart/melee families (Tasks 6–9), latch/restoration (Task 10), performance + complete family audit (Task 11), differential/hostile runtime validation (Task 12), full CI/artifact/review gate (Task 13).
- **Placeholder scan:** no `TBD`, `TODO`, “similar to Task N”, or unspecified test steps remain. The only conditional code creation is `OpportunityCache`, explicitly gated by a numeric performance failure; the no-cache passing outcome is fully specified and testable.
- **Type consistency:** `LethalOpportunity`, `OpportunityFamily`, `LethalOpportunityPredictor`, registry, assembler, `EngineFrame`, `PredictionContext.safetyMode`, `WorldSnapshot.BlockSnapshot.collisionBoxes`, and shared explosion factory signatures are defined before downstream use.
- **Concurrency:** production implementation is explicitly blocked until the active project lease is gone and coordination state is refreshed; design/plan commits remain outside the leased project path.
