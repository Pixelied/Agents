# Pearl Catcher 2.4.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve real catch reliability by making the one general solver optimize exact geometric collision clearance, while adding overlapping G/H shots and moving the debug sweep to B.

**Architecture:** Extend AABB geometry with an exact segment-interior clearance calculation. Feed that metric into `GeneralCatchSolver` ranking and traces. Replace singular runtime tracking with per-attempt collections; keep one `GeneralCatchSolver` for every normal, vertical, delayed, and replanned shot.

**Tech Stack:** Java 25 target, Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.153.0+26.1.2, Fabric Loom 1.17.17.

## Global Constraints

- Keep `GeneralCatchSolver` as the only catch planner/solver.
- Preserve exact pearl-segment -> wind AABB collision direction and entry semantics.
- Use ordinary vanilla item use/rotation packets only.
- `G` normal catch; `H` vertical `-90°` catch; `B` debug sweep.
- Manual G/H presses may start new attempts while older attempts are still tracked.
- Do not reintroduce wind-timing modes, predictive/reactive policy classes, or best-effort planners.
- Preserve the current manual Java-25 packaging/ABI verification caveat when a real Loom/JDK25 build is unavailable.

---

### Task 1: Exact collision-clearance geometry

**Files:**
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/Aabb3d.java`
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java`

**Interfaces:**
- Produces: `double Aabb3d.segmentInteriorClearance(Vec3d from, Vec3d to)`.

- [ ] Add failing tests: corner/face graze returns ~0; center crossing returns the expected half-extent; off-box segment returns 0.
- [ ] Run `./scripts/run-core-tests.sh` and confirm RED because the method is absent.
- [ ] Implement the exact piecewise-linear maximum by evaluating t=0/1 and all pairwise intersections of the six affine face-distance functions.
- [ ] Run core tests and confirm GREEN.

### Task 2: Clearance-aware single-solver ranking

**Files:**
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/GeneralCatchSolver.java`
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java`

**Interfaces:**
- `Plan` adds `double collisionClearance`.
- Candidate scoring uses clearance before sampled reliability.

- [ ] Add a failing regression that constructs/searches a target where an exact-distance graze competes with a slightly off-distance deep crossing and asserts the deep crossing wins.
- [ ] Add assertions that returned plans expose positive clearance for representative 12b level/upward catches.
- [ ] Run core tests and confirm RED.
- [ ] Compute clearance from the effective AABB on every exact candidate and add a strong low-clearance penalty. Keep distance/crosshair/timing terms as secondary objectives.
- [ ] Demote finite spread sampling to a finalist sanity penalty and never allow a nominal zero-clearance candidate to receive a misleading perfect final score.
- [ ] Run the full core suite and confirm GREEN, including target-distance, vertical, known-pearl, extreme-motion, far-target, and performance tests.

### Task 3: Debug/export clearance visibility

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- `PlanTrace` adds `collisionClearance`.
- Chat/overlay may show clearance compactly.

- [ ] Add/update architecture/text assertions so trace serialization requires `collisionClearance`.
- [ ] Update `PlanTrace.from`, text export, and overlay/announcement to include clearance.
- [ ] Compile against the API stubs and confirm no signature regression.

### Task 4: G/H/B controls

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchClient.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java`
- Modify: `src/main/resources/assets/pearlcatch/lang/en_us.json`
- Modify: `scripts/test-single-solver-architecture.sh`

**Interfaces:**
- `triggerVerticalPearlCatch(Minecraft, PearlCatchConfig)` targets current yaw + `-90°` pitch.
- Debug sweep key becomes B.

- [ ] Add RED architecture checks for G normal, H vertical, B debug and updated settings help text.
- [ ] Implement the key mappings and vertical trigger through the same `launchJointShot`/`GeneralCatchSolver` path.
- [ ] Run architecture checks and API-signature compile GREEN.

### Task 5: Overlapping attempt state

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `scripts/test-single-solver-architecture.sh`

**Interfaces:**
- Replace singular `activeShot` and `pendingCatch` ownership with collections of `TrackingShot`/`PendingCatch`.
- Every attempt retains independent entity-id snapshots and trace state.

- [ ] Add RED architecture checks forbidding the old manual "already being tracked" gate and singular state declarations.
- [ ] Refactor tick/update/entity-load/finish paths to iterate attempts safely and remove only the completed attempt.
- [ ] Make G and H start another attempt without waiting for older projectiles to disappear, subject only to vanilla item cooldown/use success.
- [ ] Keep debug sweep sequential for readable traces, but do not let unrelated manual tracked shots corrupt its state.
- [ ] Add active attempt count to diagnostics.
- [ ] Run architecture and API-signature compile GREEN.

### Task 6: Existing-wind collision safety

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Test: `src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java` if helper geometry is moved into core.

**Interfaces:**
- Before executing a newly planned pearl, reject plans whose nominal pearl path would hit an already-observed active wind charge before the intended paired catch.

- [ ] Add a deterministic RED regression/helper test for an old wind crossing the new pearl path.
- [ ] Implement the safety check using observed wind position/velocity and exact wind AABB collision semantics.
- [ ] Confirm the check does not block unrelated distant active wind charges.
- [ ] Run core/runtime checks GREEN.

### Task 7: Release verification and 2.4.0 artifacts

**Files:**
- Modify: `gradle.properties`
- Modify: `README.md`
- Modify: `ROOT_CAUSE_ANALYSIS.md`
- Package: `pearlcatcher-2.4.0.jar`
- Package: `pearlcatcher-2.4.0-source.zip`

**Interfaces:** none.

- [ ] Set `mod_version=2.4.0` and update docs with the collision-clearance root cause and new controls.
- [ ] Run fresh core tests.
- [ ] Run single-solver/runtime architecture checks.
- [ ] Compile all production sources against the strict 26.1.2/Fabric signature stubs.
- [ ] Run `scripts/verify-manual-abi.sh` on compiled production classes.
- [ ] Package only real mod classes/resources, patch class major to 69, and verify no stubs/legacy solver classes leak.
- [ ] Smoke-load exact packaged classes on the temporary Java-21-header copy.
- [ ] Verify metadata/version/source archive integrity and compute SHA-256 hashes.
