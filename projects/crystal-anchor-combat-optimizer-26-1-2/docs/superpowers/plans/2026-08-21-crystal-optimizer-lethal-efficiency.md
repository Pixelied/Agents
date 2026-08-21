# Crystal Optimizer Lethal-Efficiency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep V2's fast crystal/anchor combo engine while preventing self-suicide/self-pop, low-value resource spam, incomplete anchor/crystal setup chains, and false rejection of vanilla-replaceable water setup positions.

**Architecture:** Admission becomes a first-class stage before ranking. `ClientDamageMapBuilder` computes exact/pessimistic self outcomes plus target damage, `SelfSurvivalPolicy` and `LethalEfficiencyPolicy` decide whether an opportunity deserves publication, complete setup chains carry atomic resource demand, and the existing reactive engine remains planner-free. Water support placement uses vanilla `BlockState.canBeReplaced()` semantics while `PlaceCrystal` keeps the strict 26.1.2 empty-above rule.

**Tech Stack:** Java 25, Fabric Loader 0.19.3+, Fabric API 0.144.3+26.1, Minecraft 26.1.2, Gradle 9.5.1, JUnit 5, Fabric GameTest.

**Spec:** `projects/crystal-anchor-combat-optimizer-26-1-2/docs/superpowers/specs/2026-08-21-crystal-optimizer-lethal-efficiency-design.md`

## Global Constraints

- Never intentionally kill the local player.
- Never intentionally trigger the local player's totem.
- In `LETHAL_SPEED`, certified lethal/pop/required staircase may exceed the ordinary `maxSelfDamage` comfort cap only if pessimistic local survival remains > 0.5 HP and no local totem triggers.
- Ordinary pressure must pass both `maxSelfDamage` and a 1.25 useful-target/self trade ratio.
- New anchor setup requires a complete currently-resource-backed anchor + glowstone + detonation chain.
- New obsidian setup requires a complete currently-resource-backed obsidian + crystal chain whose terminal crystal passes admission.
- Water is replaceable for support block placement when vanilla says the block state is replaceable; a crystal itself is never placed while water occupies the direct-above block.
- Preserve real server entity-ID spawn→attack→replace behavior and the existing <=1 ms p50 / <=2 ms p95 reactive decision gates.
- Future 2.9 is behavioral reference only; copy no implementation code.

---

### Task 1: Add self-safety, opportunity-intent, and resource-chain contracts

**Files:**
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/OpportunityIntent.java`
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/SelfDamageEstimate.java`
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/ResourceChain.java`
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/SelfSurvivalPolicy.java`
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/LethalEfficiencyPolicy.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/state/ActionApproval.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/strategy/SelfSurvivalPolicyTest.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/strategy/ResourceChainTest.java`

**Interfaces:**
- Produces `OpportunityIntent { PRESSURE, STAIRCASE, POP, LETHAL, PREPARE }`.
- Produces `SelfDamageEstimate(float worstCaseDamage, float worstCaseRemainingHealth, boolean totemTriggered)`.
- Produces immutable `ResourceChain(Map<Item,Integer> demand, double cost)` with `none()`, `of(Map<Item,Integer>, double)`, `count(Item)`, and `isEmpty()`.
- Produces `SelfSurvivalPolicy.evaluate(SelfDamageEstimate, OpportunityIntent, float usefulTargetDamage, OptimizerConfig)` returning `Decision(boolean allowed, Reason reason)`.
- `DamageOpportunity` and `ActionApproval` carry `OpportunityIntent intent`, `SelfDamageEstimate selfDamage`, and `ResourceChain resources` instead of only a flat self-damage float. Keep `worstCaseSelfDamage()` compatibility methods returning `selfDamage.worstCaseDamage()` to minimize call-site churn.

- [ ] **Step 1: Write failing policy tests**

```java
@Test
void damageBelowComfortCapStillRejectsWhenItWouldKillSelf() {
    var self = new SelfDamageEstimate(10.0f, 0.0f, false);
    var decision = SelfSurvivalPolicy.evaluate(
        self, OpportunityIntent.PRESSURE, 14.0f, OptimizerConfig.defaults()
    );
    assertFalse(decision.allowed());
    assertEquals(SelfSurvivalPolicy.Reason.SELF_LETHAL, decision.reason());
}

@Test
void localTotemActivationIsAlwaysRejected() {
    var self = new SelfDamageEstimate(18.0f, 1.0f, true);
    var decision = SelfSurvivalPolicy.evaluate(
        self, OpportunityIntent.LETHAL, 40.0f, OptimizerConfig.defaults()
    );
    assertEquals(SelfSurvivalPolicy.Reason.SELF_TOTEM_POP, decision.reason());
}

@Test
void ordinaryLosingTradeIsRejected() {
    var self = new SelfDamageEstimate(8.0f, 12.0f, false);
    var decision = SelfSurvivalPolicy.evaluate(
        self, OpportunityIntent.PRESSURE, 5.0f, OptimizerConfig.defaults()
    );
    assertEquals(SelfSurvivalPolicy.Reason.BAD_TRADE, decision.reason());
}

@Test
void lethalSpeedMayExceedComfortCapForSafeCertifiedLethal() {
    var config = OptimizerConfig.defaults();
    var self = new SelfDamageEstimate(13.0f, 7.0f, false);
    assertTrue(SelfSurvivalPolicy.evaluate(
        self, OpportunityIntent.LETHAL, 20.0f, config
    ).allowed());
}
```

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*SelfSurvivalPolicyTest' --tests '*ResourceChainTest'`
Expected: FAIL because the new contracts do not exist.

- [ ] **Step 3: Implement contracts and policy**

Core policy logic:

```java
if (self.totemTriggered()) return Decision.reject(Reason.SELF_TOTEM_POP);
if (self.worstCaseRemainingHealth() <= 0.5f) return Decision.reject(Reason.SELF_LETHAL);
boolean privileged = config.strategy() == OptimizerStrategy.LETHAL_SPEED
    && (intent == OpportunityIntent.LETHAL
        || intent == OpportunityIntent.POP
        || intent == OpportunityIntent.STAIRCASE);
if (!privileged && self.worstCaseDamage() > config.maxSelfDamage()) {
    return Decision.reject(Reason.SELF_DAMAGE_LIMIT);
}
if (intent == OpportunityIntent.PRESSURE && self.worstCaseDamage() > 0.0f
    && usefulTargetDamage / self.worstCaseDamage() < 1.25f) {
    return Decision.reject(Reason.BAD_TRADE);
}
return Decision.allow();
```

`ResourceChain` validates positive counts and finite non-negative cost, defensively copies the map, and uses item identity/equality consistently with existing inventory maps.

- [ ] **Step 4: Run GREEN**

Run: `gradle --no-daemon test --tests '*SelfSurvivalPolicyTest' --tests '*ResourceChainTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add lethal-efficiency safety contracts`

---

### Task 2: Match vanilla replaceability for water support placement

**Files:**
- Modify: `src/main/java/dev/adrien/crystaloptimizer/action/ActionChecks.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/action/PlaceObsidian.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/action/PlaceAnchor.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/action/WaterPlacementLegalityTest.java`

**Interfaces:**
- Add package-private `ActionChecks.requireReplaceablePlacementTarget(CombatState state, BlockPos pos)`.
- It allows a target when `blockState.isAir() || blockState.canBeReplaced()` and rejects non-replaceable blocks.
- It does not alter entity collision, reach, or support-face rules.

- [ ] **Step 1: Write RED tests**

Construct a `CombatState` with supported water at the target position and selected obsidian/anchor. Assert `PlaceObsidian.check()` and `PlaceAnchor.check()` are legal. Construct obsidian base with water directly above and assert `PlaceCrystal.check()` remains illegal.

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*WaterPlacementLegalityTest'`
Expected: obsidian/anchor-in-water assertions fail because both actions currently require `isAir()`.

- [ ] **Step 3: Implement vanilla-style replaceability**

```java
static ActionLegality requireReplaceablePlacementTarget(CombatState state, BlockPos pos) {
    var block = state.geometry().getBlockState(pos);
    return block.isAir() || block.canBeReplaced()
        ? ActionLegality.allowed()
        : ActionLegality.denied("placement target is not replaceable");
}
```

Use this in `PlaceObsidian` and `PlaceAnchor`. Keep `PlaceCrystal` unchanged.

- [ ] **Step 4: Run GREEN plus existing action tests**

Run: `gradle --no-daemon test --tests '*WaterPlacementLegalityTest' --tests '*ActionLegalityTest' --tests '*BattlefieldSetupCandidateTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `fix: allow vanilla-replaceable water setup`

---

### Task 3: Reserve whole consumable chains atomically

**Files:**
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedger.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/BurstReceipt.java` only if receipt metadata needs one chain reservation id.
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/execution/PendingItemLedgerTest.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/reactive/BreakReplaceOrderingTest.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/reactive/CoordinatorPendingItemLifecycleTest.java`

**Interfaces:**
- Add `PendingItemLedger.reserveChain(long actionId, ResourceChain chain, ToIntFunction<Item> observedCount)`.
- A reservation becomes `Map<Item,Integer>` rather than a single item/count.
- `reserved(Item)` and `available(Item,int)` sum each outstanding chain's remaining demand.
- Existing `reserve(long,Item,int,int)` remains as a compatibility wrapper around a one-item chain.
- `ReactiveBurstDispatcher` reserves `decision.approval().resources()` once when `startIndex == 0`; continuations reuse that reservation.

- [ ] **Step 1: Write RED atomicity test**

```java
@Test
void chainReservationIsAllOrNothing() {
    var ledger = new PendingItemLedger();
    var chain = ResourceChain.of(Map.of(
        Items.RESPAWN_ANCHOR, 1,
        Items.GLOWSTONE, 1
    ), 2.5);
    assertThrows(IllegalStateException.class,
        () -> ledger.reserveChain(7L, chain,
            item -> item == Items.RESPAWN_ANCHOR ? 1 : 0));
    assertEquals(0, ledger.reservationCount());
}
```

Also test a successful obsidian+crystal chain makes both counts unavailable until reconciliation/release.

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*PendingItemLedgerTest'`
Expected: FAIL because `reserveChain` does not exist.

- [ ] **Step 3: Implement group reservations and dispatcher use**

Preflight every item before mutating `reservations`; only insert after every demand is available. Reconciliation decrements remaining per-item demand from observed count drops. A failed/deferred burst may keep the conservative group reservation until reconciliation/timeout; safety is preferred over double-spending.

- [ ] **Step 4: Run GREEN and ordering/lifecycle regressions**

Run: `gradle --no-daemon test --tests '*PendingItemLedgerTest' --tests '*BreakReplaceOrderingTest' --tests '*CoordinatorPendingItemLifecycleTest'`
Expected: PASS and break→replace action order unchanged.

- [ ] **Step 5: Commit**

Commit message: `feat: reserve complete combat resource chains`

---

### Task 4: Enforce lethal-efficiency admission on direct damage opportunities

**Files:**
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/DamageOpportunity.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelector.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/SelectionContext.java` only if useful-damage classification needs additional context.
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/strategy/FastOpportunitySelectorTest.java`
- Create: `src/test/java/dev/adrien/crystaloptimizer/client/V2LethalEfficiencyArchitectureTest.java`

**Interfaces:**
- Replace `ClientDamageMapBuilder.totalSelfDamage(...)` with `selfDamageEstimate(...)` built from `VanillaDamageSimulator.apply` and `DamageTrace.totemTriggered()`.
- Build `ResourceChain.none()` for direct breaks/detonations and `{END_CRYSTAL:1}` for placements/recycles.
- Classify intent after target damage: `LETHAL`, `POP`, `STAIRCASE`, otherwise `PRESSURE`.
- Call `LethalEfficiencyPolicy` before inserting an opportunity.
- Normal pressure spend floor is `max(config.minDamage(), 6.0f)`; face-place may use 2.0f; direct existing-crystal attack may use 1.0f useful damage.

- [ ] **Step 1: Write RED selector/admission tests**

Add tests proving:
- 3-damage distant crystal placement is absent/rejected even if `minDamage=0`;
- 5 target / 8 self pressure is rejected;
- 16 target / 2 self beats 18 target / 9 self when both are nonlethal and completion times are similar;
- certified lethal still outranks resource cost.

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*FastOpportunitySelectorTest' --tests '*V2LethalEfficiencyArchitectureTest'`
Expected: FAIL on missing admission fields/policy integration.

- [ ] **Step 3: Implement direct admission and ranking**

Selector ordering after priority class:

```java
.thenComparingDouble(opportunity -> usefulLowerRate(opportunity, context))
.thenComparingDouble(opportunity -> usefulExpectedRate(opportunity, context))
.thenComparingDouble(opportunity -> -opportunity.resources().cost())
.thenComparingDouble(opportunity -> -opportunity.selfDamage().worstCaseDamage())
```

The policy must not mutate target/self damage numbers.

- [ ] **Step 4: Run GREEN**

Run: `gradle --no-daemon test --tests '*FastOpportunitySelectorTest' --tests '*DamageMapTest' --tests '*V2LethalEfficiencyArchitectureTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: admit only lethal-efficient direct damage`

---

### Task 5: Replace single-step setup with complete viable preparation chains

**Files:**
- Create: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/PreparationSequence.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicPreparationPlanner.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicPreparationPlannerTest.java`
- Modify: `src/test/java/dev/adrien/crystaloptimizer/client/V2ColdStartPreparationArchitectureTest.java`

**Interfaces:**
- `PreparationSequence` contains `List<CombatAction> actions`, `ExplosionContext terminalExplosion`, `ResourceChain resources`, and `Set<BlockPos> geometryDependencies`.
- `StrategicPreparationPlanner.plan(CombatState, OptimizerConfig)` returns bounded complete sequences, not a zero-damage single setup action.
- Crystal setup sequence must end in `PlaceCrystal`; anchor setup sequence must end in `DetonateAnchor`.
- The planner simulates each selection/setup step with `SimulationServices.defaults()` and discards the sequence immediately if a step is impossible.
- `ClientDamageMapBuilder` evaluates the terminal explosion with the exact same target/self admission policy as a direct action.

- [ ] **Step 1: Write RED chain tests**

Required assertions:
- anchor available but no glowstone => no anchor preparation sequence;
- glowstone available but no anchor => no new-anchor sequence;
- anchor + glowstone + a non-glowstone detonation hand route => sequence ends `DetonateAnchor`;
- obsidian but no end crystal => no support placement sequence;
- obsidian + end crystal => sequence ends `PlaceCrystal`;
- when a strong existing base is available, near-equal new support is not preferred.

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*StrategicPreparationPlannerTest' --tests '*V2ColdStartPreparationArchitectureTest'`
Expected: current planner returns single setup actions and fails prerequisite assertions.

- [ ] **Step 3: Implement bounded complete-chain planning**

Keep enumeration target-local and bounded. Do not reintroduce beam search. For each candidate support/anchor position, attempt only the minimal chain needed to reach one explosion. Include hotbar selection actions when required by real-hand legality. Publish no `prepare:` opportunity until terminal damage passes `LethalEfficiencyPolicy`.

- [ ] **Step 4: Run GREEN plus cold-start regressions**

Run: `gradle --no-daemon test --tests '*StrategicPreparationPlannerTest' --tests '*V2ColdStartPreparationArchitectureTest' --tests '*CoordinatorKickstartTest'`
Expected: PASS; cold start remains functional but no resource is spent without a terminal damaging action.

- [ ] **Step 5: Commit**

Commit message: `feat: require viable lethal preparation chains`

---

### Task 6: Add final live self-survival and chain-resource arbitration

**Files:**
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/execution/LiveCombatView.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiter.java`
- Modify: `src/main/java/dev/adrien/crystaloptimizer/v2/execution/ArbitrationResult.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java`
- Test: `src/test/java/dev/adrien/crystaloptimizer/v2/execution/ActionArbiterTest.java`
- Modify fake `LiveCombatView` implementations in reactive/coordinator tests.

**Interfaces:**
- Add `float selfEffectiveHealth()` and `boolean selfTotemAvailable()` to `LiveCombatView`.
- `ClientLiveCombatView.selfEffectiveHealth()` returns current local `health + absorption`.
- `selfTotemAvailable()` checks the current main/off hand for `Items.TOTEM_OF_UNDYING`.
- Arbiter rejects `SELF_LETHAL` when `selfEffectiveHealth - approval.selfDamage().worstCaseDamage() <= 0.5f`.
- Arbiter rejects `SELF_TOTEM_POP` whenever `approval.selfDamage().totemTriggered()`; current totem presence can only make the check more conservative, never authorize a previously unsafe action.
- Arbiter validates the whole `approval.resources()` against `PendingItemLedger.available(...)` before action-by-action legality.

- [ ] **Step 1: Write RED live-health regression**

Create approval at scan-time self damage 10 with otherwise valid placement; fake live view reports 8 effective HP. Assert arbiter rejects `SELF_LETHAL` even though config `maxSelfDamage` is 12.

- [ ] **Step 2: Run RED**

Run: `gradle --no-daemon test --tests '*ActionArbiterTest'`
Expected: FAIL because live self health/totem methods and reasons do not exist.

- [ ] **Step 3: Implement fast final guard**

The hot path performs only scalar comparisons and item-count lookups; it never recomputes explosion exposure or runs planner code.

- [ ] **Step 4: Run GREEN plus reactive gates**

Run: `gradle --no-daemon test --tests '*ActionArbiterTest' --tests '*ReactiveCombatEngineTest' --tests '*ReactiveLatencyGateTest' --tests '*BreakReplaceOrderingTest'`
Expected: PASS and latency assertions remain unchanged.

- [ ] **Step 5: Commit**

Commit message: `feat: enforce live local survival before dispatch`

---

### Task 7: Diagnostics, version 0.2.1, vanilla survival tests, and full release gate

**Files:**
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java`
- Modify: `src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java`
- Modify: `gradle.properties`
- Modify: `src/gametest/java/dev/adrien/crystaloptimizer/gametest/ExplosionDifferentialGameTests.java`
- Add/update relevant architecture tests under `src/test/java/dev/adrien/crystaloptimizer/client/`.

**Interfaces:**
- Diagnostics surface the rejection reasons `SELF_LETHAL`, `SELF_TOTEM_POP`, `BAD_TRADE`, `LOW_VALUE_SPEND`, `MISSING_CHAIN_RESOURCE`, `NO_GLOWSTONE`, and `WATER_ABOVE_CRYSTAL_BASE` where available.
- Selected opportunity debug line includes intent, target damage, self worst-case damage, p90 completion time, and resource demand.
- `mod_version=0.2.1`.

- [ ] **Step 1: Add GameTest/local-simulator regressions**

Add survival cases that compare modeled local outcomes with vanilla for low health, absorption, armor, Resistance, local totem, crystals, and anchors. Assert the admission layer rejects any case that would trigger the local totem or leave <=0.5 HP.

- [ ] **Step 2: Run focused GameTests**

Run: `gradle --no-daemon test build runGameTest --stacktrace`
Expected: all unit tests and GameTests PASS.

- [ ] **Step 3: Run the full clean release gate**

Run: `gradle --no-daemon clean test build runGameTest --stacktrace`
Expected: exit 0; distribution contains remapped `0.2.1` JAR and sources.

- [ ] **Step 4: Re-run the full CI workflow on the exact head**

Expected GitHub Actions jobs:
- `Validate agent workspace`: success
- `Crystal Anchor Optimizer 26.1.2 CI`: success
- package/upload steps: success

- [ ] **Step 5: Verify artifact bytes**

Download the exact successful workflow artifact, verify its internal SHA-256 file, inspect `fabric.mod.json`, confirm only one `crystaloptimizer` mod JAR is being delivered, and calculate a delivery SHA-256 for the final 0.2.1 JAR.

- [ ] **Step 6: Commit**

Commit message: `release: prepare lethal-efficiency 0.2.1 test build`
