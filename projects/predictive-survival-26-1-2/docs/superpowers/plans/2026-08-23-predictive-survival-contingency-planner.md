# Predictive Survival Contingency Planner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable rescue policies, bounded 1-3 step contingency planning, proactive stacked-death-protection behavior, inventory-aware rescue routing, and continuous in-flight replanning to Predictive Survival 26.1.2.

**Architecture:** Keep the existing vanilla-faithful threat/timeline simulator as the safety oracle. Add an immutable rescue-policy layer before candidate evaluation, a bounded sequence planner that carries simulated player state across conservative activation times, and an engine controller that continuously validates the current step plus remaining contingency against each fresh frame. Mod Menu edits the policy live and the runtime remains fail-closed.

**Tech Stack:** Java 25, Fabric Loader/API for Minecraft Java 26.1.2, Mod Menu, Gradle 9.5.1, JUnit 5, Fabric client GameTest.

**Spec:** `projects/predictive-survival-26-1-2/docs/superpowers/specs/2026-08-23-predictive-survival-contingency-planner-design.md`

## Global Constraints

- Minecraft Java 26.1.2, Fabric, client-side only, Java 25.
- Preserve existing vanilla-faithful damage/timeline semantics and fail-closed behavior.
- Unsupported `Relocate`, `PlaceCover`, and `PearlRescue` production dispatch remain filtered.
- Search depth is at most 3 rescue steps and search/node limits never imply safety when exhausted.
- Full world/threat capture still occurs every client tick.
- All config changes apply live and trigger replanning.
- Exact held stack component identity must remain enforced for use/equipment actions.

---

### Task 1: Versioned rescue policy configuration

**Files:**
- Create: `src/client/java/dev/pixelied/survival/config/RescueProfile.java`
- Create: `src/client/java/dev/pixelied/survival/config/RescuePolicy.java`
- Modify: `src/client/java/dev/pixelied/survival/config/SurvivalConfig.java`
- Modify: `src/client/java/dev/pixelied/survival/config/SurvivalConfigDraft.java`
- Modify: `src/client/java/dev/pixelied/survival/config/SurvivalConfigStore.java`
- Test: `src/test/java/dev/pixelied/survival/config/SurvivalConfigTest.java`
- Test: `src/test/java/dev/pixelied/survival/config/SurvivalConfigDraftTest.java`
- Create: `src/test/java/dev/pixelied/survival/config/SurvivalConfigMigrationTest.java`

**Interfaces:**
- Produces: `RescueProfile { TOTEM_ONLY, TOTEM_AND_SHIELD, CONSERVATIVE_SMART, SMART, CUSTOM }`.
- Produces: immutable `RescuePolicy` with booleans for death protection, shields, consumables, equipment, inventory routing, main-hand takeover, proactive dual protection, and restoration.
- Produces: `SurvivalConfig.rescueProfile()` and `SurvivalConfig.rescuePolicy()`.

- [ ] **Step 1: Write failing config-policy tests**

Add tests asserting profile defaults, CUSTOM round-trip, and that a legacy five-field JSON config migrates without resetting unrelated settings.

```java
assertEquals(RescueProfile.CONSERVATIVE_SMART, SurvivalConfig.defaults().rescueProfile());
assertTrue(SurvivalConfig.defaults().rescuePolicy().deathProtection());
assertTrue(SurvivalConfig.defaults().rescuePolicy().shields());
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `./gradlew --no-daemon test --tests 'dev.pixelied.survival.config.*'`
Expected: compilation/test failure because rescue policy types do not exist.

- [ ] **Step 3: Implement immutable policy + migration-tolerant store**

Use safe per-field fallback parsing. Persist a numeric `schemaVersion` and all new fields. Missing new fields use defaults; malformed known fields fall back individually rather than replacing the whole config.

- [ ] **Step 4: Run focused config tests and confirm GREEN**

Run: `./gradlew --no-daemon test --tests 'dev.pixelied.survival.config.*'`
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: add rescue policy configuration`

---

### Task 2: Policy-aware candidate generation and routable item references

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/planner/SurvivalCandidateGenerator.java`
- Modify: `src/client/java/dev/pixelied/survival/planner/SurvivalAction.java`
- Create: `src/client/java/dev/pixelied/survival/inventory/SurvivalItemRoute.java`
- Create: `src/client/java/dev/pixelied/survival/inventory/SurvivalItemRoutePlanner.java`
- Modify: `src/client/java/dev/pixelied/survival/execution/NonTotemActionExecutor.java`
- Test: `src/test/java/dev/pixelied/survival/planner/SurvivalCandidateGeneratorTest.java`
- Create: `src/test/java/dev/pixelied/survival/inventory/SurvivalItemRoutePlannerTest.java`
- Modify: `src/test/java/dev/pixelied/survival/execution/NonTotemActionExecutorTest.java`

**Interfaces:**
- `SurvivalCandidateGenerator.generate(..., RescuePolicy policy)` filters forbidden families before planning.
- `SurvivalItemRoute` represents `AlreadyHeld`, `HotbarSelect`, or `ContainerSwap` with exact stack key + component fingerprint.
- `SurvivalAction.HeldItemRef` gains enough route information for the executor to reproduce the exact selected stack safely.

- [ ] **Step 1: Add RED policy/routing tests**

Cover TOTEM_ONLY excluding shield/consumable/equipment actions; TOTEM_AND_SHIELD excluding consumables; SMART allowing all enabled safe families; and exact component-fingerprint routing from hotbar/menu inventory.

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `./gradlew --no-daemon test --tests '*SurvivalCandidateGeneratorTest' --tests '*SurvivalItemRoutePlannerTest' --tests '*NonTotemActionExecutorTest'`
Expected: FAIL because generator has no policy and non-held routes are unsupported.

- [ ] **Step 3: Implement route planner and production-safe executor pre-steps**

For non-totem actions, only emit a routed candidate when selection/swap + use/equip can be represented with existing `ExecutionCommand` primitives and confirmed through `ServerAuthorityTracker`/observed inventory state. Never fall back from exact component identity to item-key-only matching.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run the command from Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: route policy-enabled survival items`

---

### Task 3: Proactive stacked death protection

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/planner/SurvivalCandidateGenerator.java`
- Modify: `src/client/java/dev/pixelied/survival/inventory/DeathProtectionRoutePlanner.java`
- Modify: `src/client/java/dev/pixelied/survival/planner/SurvivalPlanner.java`
- Test: `src/test/java/dev/pixelied/survival/inventory/DeathProtectionRoutePlannerTest.java`
- Create: `src/test/java/dev/pixelied/survival/planner/StackedDeathProtectionPlannerTest.java`

**Interfaces:**
- Candidate generation can request protection for a specific free/replaceable hand even when the other hand already contains death protection.
- The planner may arm the second hand only when the simulated timeline requires another protection consumption before a post-pop refill can become authoritative and policy permits proactive dual protection/main-hand takeover.

- [ ] **Step 1: Add RED dual-totem regression**

Construct a timeline where offhand protection is consumed by threat A and threat B is lethal too soon for a later refill. Assert no single existing action survives, but pre-arming main hand with a second protection item does.

- [ ] **Step 2: Run test and confirm RED**

Run: `./gradlew --no-daemon test --tests '*StackedDeathProtectionPlannerTest'`
Expected: FAIL because candidate generation suppresses protection when either hand already has it.

- [ ] **Step 3: Implement hand-targeted protection generation**

Do not blindly dual-wield. Generate the extra action only when the full timeline establishes necessity and configured policy permits it.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run Step 2 plus `*DeathProtectionRoutePlannerTest`.
Expected: PASS.

- [ ] **Step 5: Commit**

Commit message: `feat: prearm stacked death protection`

---

### Task 4: Bounded 1-3 step contingency planner

**Files:**
- Create: `src/client/java/dev/pixelied/survival/planner/PlannedStep.java`
- Create: `src/client/java/dev/pixelied/survival/planner/ContingencyPlan.java`
- Create: `src/client/java/dev/pixelied/survival/planner/ContingencyPlanner.java`
- Modify: `src/client/java/dev/pixelied/survival/planner/SurvivalPlanner.java`
- Create: `src/test/java/dev/pixelied/survival/planner/ContingencyPlannerTest.java`
- Create: `src/test/java/dev/pixelied/survival/planner/ContingencyPlannerLimitTest.java`

**Interfaces:**
- `ContingencyPlanner.plan(PredictionContext, ThreatTimeline, List<SurvivalAction>, RescuePolicy)` returns a `ContingencyPlan` containing 0-3 steps plus final simulation result and guarantee flag.
- `PlannedStep` records `SurvivalAction action`, `long activationTick`, and the post-step simulated player state/result needed by the next expansion.
- Search expansion cap is explicit and a truncated search cannot return `guaranteed=true` unless the chosen sequence itself was fully evaluated and survives every modeled schedule.

- [ ] **Step 1: Add RED arrow-then-mace sequence test**

Model a blockable arrow followed by an unblockable lethal mace. Assert shield alone fails full timeline, totem alone is more costly or insufficient for the intended sequence, and `[RaiseShield, EquipDeathProtection]` guarantees survival.

- [ ] **Step 2: Add RED bounded-search fail-closed test**

Force the expansion budget below the needed sequence and assert no guaranteed plan is returned.

- [ ] **Step 3: Run focused tests and confirm RED**

Run: `./gradlew --no-daemon test --tests '*ContingencyPlanner*'`
Expected: FAIL because contingency types/search do not exist.

- [ ] **Step 4: Implement bounded sequence simulation**

Carry forward the player snapshot after each conservative activation. Reuse `ThreatTimelineSimulator` rather than duplicating damage semantics. Prune dominated prefixes whose resulting state is no safer and no cheaper than an already-seen prefix at the same depth.

- [ ] **Step 5: Run contingency tests and confirm GREEN**

Run Step 3.
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: add bounded contingency planning`

---

### Task 5: Engine execution of contingency steps and live progress-preserving replanning

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/core/SurvivalEngine.java`
- Modify: `src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java`
- Modify: `src/client/java/dev/pixelied/survival/execution/ActionExecutor.java` only if a generic cancellation/progress hook is required.
- Modify: `src/test/java/dev/pixelied/survival/core/SurvivalEngineTest.java`
- Modify: `src/test/java/dev/pixelied/survival/core/SurvivalEngineDangerWindowTest.java`
- Create: `src/test/java/dev/pixelied/survival/core/SurvivalEngineContingencyTest.java`

**Interfaces:**
- Engine stores `Optional<ContingencyPlan> currentContingency` and an active step index rather than treating every decision as an unrelated single plan.
- Revalidation always asks `runtime.remainingServerTicks(active, frame)` even when the absolute threat schedule changed.
- Completed steps advance to the next still-required step; changed threats trigger remaining-plan recomputation from the current authoritative frame.

- [ ] **Step 1: Add RED in-flight schedule-change regression**

Begin an action, change projectile impact timing/position on the next frame, report partial executor progress, and assert revalidation uses the reduced remaining ticks rather than the action's original full duration.

- [ ] **Step 2: Add RED disappearing-threat and new-threat regressions**

Assert an unnecessary future step is removed when its threat vanishes, while a newly appearing mace after an arrow causes a second rescue step to be added without discarding still-useful authoritative shield state.

- [ ] **Step 3: Run focused engine tests and confirm RED**

Run: `./gradlew --no-daemon test --tests '*SurvivalEngine*'`
Expected: FAIL on sequence/progress behavior.

- [ ] **Step 4: Implement contingency execution/revalidation**

Never restore hand state between steps in the same danger window. Only replace an in-flight step when the refreshed plan proves a safer executable alternative.

- [ ] **Step 5: Run focused engine tests and confirm GREEN**

Run Step 3.
Expected: PASS.

- [ ] **Step 6: Commit**

Commit message: `feat: execute and revalidate contingency plans`

---

### Task 6: Threat-dirty acceleration without weakening tick capture

**Files:**
- Create: `src/client/java/dev/pixelied/survival/core/ThreatDirtyTracker.java`
- Modify: `src/client/java/dev/pixelied/survival/PredictiveSurvivalClient.java`
- Modify: relevant existing mixin/network observation hooks only where a safe client-thread signal already exists.
- Create: `src/test/java/dev/pixelied/survival/core/ThreatDirtyTrackerTest.java`
- Modify: `src/test/java/dev/pixelied/survival/UrgentReevaluationContractTest.java`

**Interfaces:**
- `ThreatDirtyTracker.markDirty()` is idempotent and cheap.
- `consumeDirty()` coalesces multiple observations.
- Tick capture remains mandatory; dirty state may request an extra safe re-evaluation point but never causes off-thread world reads.

- [ ] **Step 1: Add RED coalescing/thread-contract tests**
- [ ] **Step 2: Run focused tests and confirm RED**
- [ ] **Step 3: Implement minimal dirty signaling using existing safe lifecycle/network hooks**
- [ ] **Step 4: Run focused tests and confirm GREEN**
- [ ] **Step 5: Commit**

Commit message: `feat: accelerate threat re-evaluation`

---

### Task 7: Mod Menu profile/custom controls

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/config/PredictiveSurvivalConfigScreen.java`
- Modify: `src/client/java/dev/pixelied/survival/config/SurvivalConfigDraft.java`
- Modify: `src/main/resources/assets/predictive_survival/lang/en_us.json`
- Modify: `src/test/java/dev/pixelied/survival/config/ModMenuContractTest.java`
- Modify: `src/test/java/dev/pixelied/survival/config/SurvivalConfigDraftTest.java`

**Interfaces:**
- Profile cycle button always visible.
- CUSTOM-only controls are rebuilt/activated when profile is CUSTOM.
- Save applies one immutable snapshot through `LiveConfigController`.

- [ ] **Step 1: Add RED UI contract tests for profile labels and all custom policy translation keys**
- [ ] **Step 2: Run config/UI tests and confirm RED**
- [ ] **Step 3: Implement compact grouped screen with tooltips and conditional CUSTOM rows**
- [ ] **Step 4: Run config/UI tests and confirm GREEN**
- [ ] **Step 5: Commit**

Commit message: `feat: expand predictive survival Mod Menu controls`

---

### Task 8: Debug contingency diagnostics and restoration safety

**Files:**
- Modify: `src/client/java/dev/pixelied/survival/debug/SurvivalDebugHud.java`
- Modify: `src/client/java/dev/pixelied/survival/execution/DeathProtectionRestorationController.java` if needed to consume a whole-danger-window signal.
- Modify: `src/test/java/dev/pixelied/survival/debug/SurvivalDebugHudTest.java`
- Modify: `src/test/java/dev/pixelied/survival/execution/DeathProtectionRestorationControllerTest.java`

- [ ] **Step 1: Add RED diagnostics/restoration tests**

Assert debug output can show `Arrow T+4 -> Mace T+9 | Shield -> Totem`, and restoration remains suppressed while any remaining predicted lethal sequence still depends on emergency state.

- [ ] **Step 2: Run focused tests and confirm RED**
- [ ] **Step 3: Implement diagnostics and danger-window restoration contract**
- [ ] **Step 4: Run focused tests and confirm GREEN**
- [ ] **Step 5: Commit**

Commit message: `feat: expose contingency diagnostics`

---

### Task 9: Exact-runtime coverage, full regression, and release verification

**Files:**
- Add/modify under: `src/gametest/java/dev/pixelied/survival/validation/`
- Modify: `VALIDATION.md`
- Modify workflow only if a new required gate is impossible with existing commands.

- [ ] **Step 1: Add exact-runtime GameTests for any new inventory select/swap/use route**
- [ ] **Step 2: Run unit/build gate**

Run: `./gradlew --no-daemon clean test build`
Expected: PASS.

- [ ] **Step 3: Run GameTest compile gate**

Run: `./gradlew --no-daemon compileGametestJava processGametestResources`
Expected: PASS.

- [ ] **Step 4: Run exact-runtime client GameTests**

Run: `xvfb-run -a ./gradlew --no-daemon --console=plain runClientGameTest`
Expected: PASS.

- [ ] **Step 5: Verify production JAR isolation**

Assert no `dev/pixelied/survival/validation/` classes are present in the production JAR.

- [ ] **Step 6: Update `VALIDATION.md` with exact scenarios and evidence**
- [ ] **Step 7: Push final feature head, open PR, and require fresh GitHub Actions success**
- [ ] **Step 8: Run workspace validation before merge**

Expected: `python -m unittest discover -s tests -v` and `python agentctl.py validate` both pass on the merge candidate.

- [ ] **Step 9: Merge only after final review + verification, then release the project lease**
