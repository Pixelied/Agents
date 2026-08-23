# Pearl Catcher 2.2.0 Reactive/Hybrid Solver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build 2.2.0 so Auto uses immediate predictive catches only when they are robust and near the requested range, otherwise throws the pearl first and re-solves the wind from the real spawned pearl velocity and the player's current launch state.

**Architecture:** Add pure core units for pearl-only trajectory planning and reactive wind solving. Keep Minecraft/Fabric state-machine work in `PearlCatchMode`: pearl-only use -> observe/reconstruct -> reactive wind solve/use -> authoritative reconstructed tracking. Preserve the exact one-way pearl-segment-to-wind-AABB collision rule and the 2.1.0 predictive solver as a fast, gated fallback.

**Tech Stack:** Java 25 target bytecode, Minecraft 26.1.2, Fabric Loader 0.19.3+, Fabric API 0.153.0+26.1.2+, Mod Menu, Gson, pure Java self-tests.

## Global Constraints
- Client-side only.
- Vanilla item-use and movement/rotation packets only.
- No fake entities or projectile velocity mutation.
- Ender pearl movement segment entering WindCharge AABB remains collision authority.
- Auto predictive eligibility requires >=0.80 robustness and <= `max(1.0, targetDistance*0.15)` range error.
- Reactive wind eligibility requires >=0.80 wind-spread robustness.
- Reactive retries are capped at 8 client ticks.
- Preserve the 2.0.3/2.1.0 anti-freeze pruning and interactive performance.

---

### Task 1: Actual pearl state reconstruction

**Files:**
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/VanillaProjectilePhysics.java`
- Test: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`

**Interfaces:**
- Produces `pearlVelocityBeforeTick(Vec3d)` and `reconstructPearlLaunchVelocity(Vec3d observedVelocity, int completedTicks)`.

- [ ] **Step 1:** Add tests that advance known launch velocities for 1, 2, 8, and 20 air ticks and reconstruct the exact original velocity within 1e-9.
- [ ] **Step 2:** Run `./scripts/run-core-tests.sh` and verify RED because reconstruction helpers do not exist.
- [ ] **Step 3:** Implement inverse air physics with `vPrev = vAfter / 0.99 + (0, 0.03, 0)` repeated `completedTicks` times; reject negative tick counts.
- [ ] **Step 4:** Run the core suite and verify GREEN.

### Task 2: Pearl-only target trajectory planner

**Files:**
- Create: `src/main/java/studio/pixelied/pearlcatch/core/PearlTrajectoryPlanner.java`
- Test: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`

**Interfaces:**
- Consumes eye position, inherited motion, target rotation, horizon, crosshair radius, target distance, search cap.
- Produces `PearlTrajectoryPlanner.Plan(rotation, closestPoint, closestTick, crosshairDistance, crosshairRange, targetDistanceError, path)`.

- [ ] **Step 1:** Add tests for target distances 4/12/20 and strong ascent/fall/horizontal inherited motion; chosen pearl path must approach the requested ray/range when physically reachable.
- [ ] **Step 2:** Verify RED because planner is absent.
- [ ] **Step 3:** Implement yaw ±3° / pitch -90..90° path search with exact segment-to-ray geometry and dominant target-distance scoring; reuse vanilla pearl physics.
- [ ] **Step 4:** Add a warmed performance assertion below 100 ms in the pure harness.
- [ ] **Step 5:** Run the full core suite GREEN.

### Task 3: Reactive wind solver from real pearl velocity

**Files:**
- Create: `src/main/java/studio/pixelied/pearlcatch/core/ReactiveWindSolver.java`
- Test: `src/test/java/studio/pixelied/pearlcatch/core/JointInterceptSolverSelfTest.java`

**Interfaces:**
- Consumes exact pearl launch position/velocity, target ray origin/rotation, wind-use eye position and inherited motion, first pearl segment where wind exists, prediction horizon, search cap, crosshair radius, target distance, minimum robustness.
- Produces `ReactiveWindSolver.Plan(windRotation, pearlCatchTick, windCompletedTicksAtCatch, interceptPoint, windPositionAtCatch, crosshairDistance, crosshairRange, targetDistanceError, firstCollisionTick, robustHitFraction, score)`.

- [ ] **Step 1:** Add failing tests showing a perturbed actual pearl velocity can still get a robust ~12-block reactive catch, target 4 vs 16 changes range, and no accepted plan collides earlier than intended.
- [ ] **Step 2:** Add strong XYZ momentum cases, including ascent +3, fall -3, fall -10, and combined `(4,-6,-3)` for wind-use inherited motion.
- [ ] **Step 3:** Verify RED.
- [ ] **Step 4:** Implement fixed-pearl-path wind search, exact AABB entry, no-earlier-hit rejection, target-distance ranking, and wind-only perturbation robustness.
- [ ] **Step 5:** Add warmed reactive-solve performance ceiling of 100 ms.
- [ ] **Step 6:** Run the full core suite GREEN.

### Task 4: Hybrid client state machine

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java`

**Interfaces:**
- Auto chooses predictive only when robustness/range gate passes, else creates a pending reactive shot.
- Pending reactive stages: PEARL_THROWN -> PEARL_OBSERVED -> WIND_THROWN/TRACKING -> FINISHED.

- [ ] **Step 1:** Add small pure strategy-gate helper tests: robust+near -> predictive; fragile or far -> reactive.
- [ ] **Step 2:** Verify RED.
- [ ] **Step 3:** Split item use so pearl and wind can be used independently while preserving silent/visible/current-camera rotation semantics and slot restore.
- [ ] **Step 4:** On reactive start, capture original target ray, pearl spawn origin, old pearl/wind IDs, previous slot/camera, and throw only pearl using `PearlTrajectoryPlanner`.
- [ ] **Step 5:** On first observed pearl, reconstruct launch velocity from velocity+tickCount; reject water/non-air exactness cases.
- [ ] **Step 6:** Each waiting tick, solve wind from current player eye/current inherited motion and `firstWindPearlSegment = observedPearlTick + 1`; throw immediately once a >=0.80 plan exists, up to 8 client ticks.
- [ ] **Step 7:** Preserve manual predictive lead modes as debug-only paths.
- [ ] **Step 8:** Run core and API-signature compile checks.

### Task 5: Replace misleading live collision diagnostics

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- Debug exports distinguish `clientDisplayPath` from `reconstructedPearlPath` and never call an interpolated position snap a vanilla collision.

- [ ] **Step 1:** Remove `observedVanillaClip` as a success criterion and rename any retained segment clip to `clientInterpolatedClipDiagnostic`.
- [ ] **Step 2:** Export reconstructed launch velocity, strategy, pearl age at wind use, wind origin/motion at use, solve duration, and reconstructed first-collision tick.
- [ ] **Step 3:** Classify disappearance using planned/reconstructed consistency and player teleport proximity; use neutral `LIKELY_CATCH_*` wording where server collision is not directly observable.
- [ ] **Step 4:** Keep visualization with predicted/reconstructed and display paths visually distinct.

### Task 6: Config/UI/docs/version

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java`
- Modify: `README.md`
- Modify: `ROOT_CAUSE_ANALYSIS.md`
- Modify: `gradle.properties`

**Interfaces:**
- AUTO label becomes `Auto (hybrid)`; Reset remains; target/horizon semantics unchanged.

- [ ] **Step 1:** Add/migrate internal 0.80 robustness and 8-tick reactive wait defaults, sanitize them, and include them in reset/export.
- [ ] **Step 2:** Update Auto UI label and explanatory copy without cluttering normal controls.
- [ ] **Step 3:** Update README/root-cause docs with reactive actual-velocity solving and debugger authority correction.
- [ ] **Step 4:** Set mod version to 2.2.0.

### Task 7: Final verification and packaging

**Files:**
- All modified sources/resources.

**Interfaces:**
- Produces `pearlcatcher-2.2.0.jar` and `pearlcatcher-2.2.0-source.zip`.

- [ ] **Step 1:** Run `./scripts/run-core-tests.sh` fresh.
- [ ] **Step 2:** Run `./scripts/verify-manual-abi.sh` fresh and audit every newly referenced Minecraft/Fabric member against uploaded 26.1.2 source/current API contract.
- [ ] **Step 3:** Package only mod classes/resources as Java-major-69 classfiles; expand metadata to 2.2.0; verify zero stub leakage.
- [ ] **Step 4:** Smoke-load the exact packaged classes using the existing linkage harness.
- [ ] **Step 5:** Verify hashes, metadata, required classes, class count, and source archive integrity before delivery.
