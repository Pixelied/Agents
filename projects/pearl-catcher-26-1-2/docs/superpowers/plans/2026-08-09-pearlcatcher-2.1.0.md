# Pearl Catcher 2.1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Build a 2.1.0 catcher that prevents unintended early collisions, targets a real user-selected catch range, executes Auto/manual wind timing correctly, and remains exact under arbitrary finite vanilla launch-time player momentum.

**Architecture:** Keep vanilla physics and collision math in the pure core solver. Extend the request/ranking model with target distance and early-collision validation, make target/no-early validity explicit in core, then keep the client on the source-backed back-to-back vanilla timing path while exposing lead 2/3 only as debug-model overrides. Keep configuration/UI migration and debug export thin wrappers around those core semantics.

**Tech Stack:** Java 25 target bytecode, Minecraft 26.1.2 client, Fabric Loader 0.19.3+, Fabric API 0.153.0+26.1.2+, Mod Menu, Gson, pure Java core self-tests.

## Global Constraints
- Minecraft version: 26.1.2.
- Fabric Loader: >=0.19.3.
- Fabric API: >=0.153.0+26.1.2.
- Java target: 25.
- Client-side only.
- Use vanilla item-use/movement packets; no custom server payloads.
- Do not mutate projectile velocity or create fake entities.
- Exact catch authority remains pearl movement segment -> WindCharge AABB entry.
- Preserve 2.0.3 interactive-search pruning and avoid reintroducing the render-thread one-second stall.

---

### Task 1: Lock early-collision and target-distance semantics in core tests

**Files:**
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/JointInterceptSolver.java`

**Interfaces:**
- Consumes: `JointInterceptSolver.Request` and `JointInterceptSolver.Plan`.
- Produces: `Plan.targetDistanceError()` and candidates guaranteed to have no earlier AABB-entry collision.

- [x] **Step 1: Write failing tests** for a low-upward pitch that previously planned tick 6 but has a valid earlier collision, target distances 4/12/20, and an assertion that changing target distance materially changes chosen range when physically reachable.
- [x] **Step 2: Run `./scripts/run-core-tests.sh`** and verify the new tests fail against 2.0.3 behavior.
- [x] **Step 3: Implement early-collision rejection** by replaying each candidate wind trajectory against pearl segments `1..plannedTick-1`, using `windCompletedTicks=max(0, segmentTick-windLeadTicks)` and the exact vanilla margin for each segment age.
- [x] **Step 4: Implement target-distance ranking** with absolute `crosshairRange-targetCatchDistance` as the dominant ranking term after exact/no-early validity; expose the error on `Plan`.
- [x] **Step 5: Run the core tests** and verify they pass.

### Task 2: Auto timing and executable lead semantics

**Files:**
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/JointInterceptSolver.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Test: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`

**Interfaces:**
- Produces: `WindTimingMode { AUTO, LEAD_1, LEAD_2, LEAD_3 }` in config; Auto maps to source-backed vanilla lead 1; manual lead 2/3 remain explicit debug-model overrides.
- Client keeps the vanilla back-to-back pearl/wind uses; no artificial delay is introduced.

- [x] **Step 1: Write timing-mode tests** requiring Auto to resolve to vanilla lead 1 and manual debug overrides to resolve to 1/2/3.
- [x] **Step 2: Verify RED** with `./scripts/run-core-tests.sh`.
- [x] **Step 3: Add timing-mode resolution** where Auto resolves to source-backed vanilla lead 1 and manual modes pass their explicit model lead.
- [x] **Step 4: Replace `windLeadTicks` config with a migrated timing enum** while accepting legacy numeric config safely.
- [x] **Step 5: Preserve back-to-back vanilla execution** for all modes; manual lead 2/3 alter only the debug solver timing assumption and are clearly labeled as such in UI/trace.
- [x] **Step 6: Run core and signature tests** and fix only failures caused by this task.

### Task 3: Momentum correctness matrix

**Files:**
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`
- Modify only if test proves necessary: `src/main/java/studio/pixelied/pearlcatch/core/VanillaProjectilePhysics.java`

**Interfaces:**
- Consumes: `VanillaProjectilePhysics.inheritedMotion(Vec3d, boolean)` matching vanilla `Projectile#shootFromRotation`.
- Produces: regression coverage for finite XYZ motion vectors.

- [x] **Step 1: Add tests** for standing, sprint-like horizontal, diagonal ±3 b/t, ascent +3 b/t, fall -3 b/t, extreme fall -10 b/t, and combined `(4,-6,-3)` b/t.
- [x] **Step 2: Assert every returned plan has a real nominal AABB entry and no earlier entry**; allow null only for mathematically unreachable target/ray combinations.
- [x] **Step 3: Add direct launch-velocity tests** proving inherited X/Z are always included and inherited Y is included airborne but zeroed on-ground.
- [x] **Step 4: Run all core tests** and preserve exact source-backed motion semantics rather than inventing a different movement source.

### Task 4: Settings UX and reset

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java`

**Interfaces:**
- Produces defaults: target distance 12.0, prediction horizon 40, wind timing AUTO.
- Produces `resetToDefaults()` that copies all runtime fields from clean defaults and saves.

- [x] **Step 1: Add config default/reset test via a small pure helper or deterministic field assertions** where possible without Minecraft runtime.
- [x] **Step 2: Rename UI labels** to `Target catch distance` and `Max prediction horizon`.
- [x] **Step 3: Replace wind lead slider with a cycling Wind timing button** showing Auto / Lead 1 / Lead 2 / Lead 3.
- [x] **Step 4: Add `Reset to defaults` button** and rebuild the screen after reset so every control reflects defaults immediately.
- [x] **Step 5: Save immediately after reset** and sanitize migrated configs.

### Task 5: Debug sweep reliability and observability

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- Exports target distance, timing mode, chosen lead, target-distance error, first actual clip tick/point, and launch-time inherited motion.

- [x] **Step 1: Add a sweep readiness gate** that waits while the selected pearl cannot be used/cooldown is active rather than advancing pitch and recording `PEARL_USE_FAILED`.
- [x] **Step 2: Keep timeout protection** so a sweep cannot stall forever; report `WAITING_FOR_PEARL_READY_TIMEOUT` if the readiness gate exceeds the configured per-pitch timeout.
- [x] **Step 3: Extend JSON/text trace schema** with target catch distance, timing mode, chosen lead, target-distance error, and inherited motion used by the solver.
- [x] **Step 4: Update HUD/chat** to show `target Xb -> planned Yb`, chosen lead, and catch tick.

### Task 6: Performance regression and packaging

**Files:**
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `src/main/resources/fabric.mod.json` only through version expansion/package process as required.

**Interfaces:**
- Produces version 2.1.0 jar/source archive.

- [x] **Step 1: Add warmed Auto-solver performance probe** with a generous interactive ceiling that catches accidental multi-million-candidate regressions.
- [x] **Step 2: Run `./scripts/run-core-tests.sh`** fresh and record pass output.
- [x] **Step 3: Run `./scripts/verify-manual-abi.sh`** fresh and record pass output.
- [x] **Step 4: Update README** explaining Target catch distance, Max prediction horizon, Auto timing, vanilla lead semantics, and momentum limitations for physically unreachable rays.
- [x] **Step 5: Package the final jar/source** with only mod classes/resources, Java class major 69, no stub leakage, and metadata version 2.1.0.
- [x] **Step 6: Inspect the exact packaged jar** for class count, class major, metadata, Gson/Fabric ABI regressions, and required classes before delivery.
