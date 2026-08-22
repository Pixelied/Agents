# Pearl Catcher 26.1.2 Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the reviewed Pearl Catcher 2.5.1 source into a conservative, vanilla-grounded 26.1.2 build that refuses unsafe catches, cannot deadlock its Legit input protocol, owns runtime state precisely, and remains structurally simple.

**Architecture:** Preserve `GeneralCatchSolver` as the single physics planner. Add hard plan acceptance, isolate runtime path/timing safety and projectile attribution from input execution, then split the oversized executor into `CatchCoordinator`, `VanillaInputExecutor`, and `ProjectileTracker` only after regression coverage protects behavior. Unsupported or uncertain runtime states fail closed instead of adding planner modes or heuristic retries.

**Tech Stack:** Java 25, Minecraft Java 26.1.2, Fabric Loader 0.19.3, Fabric API 0.153.0+26.1.2, Loom 1.17.17, Gradle 9.6.1 in CI, shell regression scripts, pure-Java solver tests.

**Spec:** `docs/superpowers/specs/2026-08-22-pearl-catcher-26-1-2-hardening-design.md`

## Global Constraints

- The supplied decompiled Minecraft Java 26.1.2 source is the runtime source of truth.
- Import the exact reviewed `Pixelied-Studio-Pearl-Catcher-2.5.1-source.zip` before functional changes.
- Keep exactly one physics planner: `GeneralCatchSolver`.
- Hard execution floor: `robustHitFraction >= 0.80` across the existing 64 runtime spread samples.
- Hard geometric floor: `collisionClearance >= 0.03` blocks.
- Do not lower robustness sample count to make performance tests pass.
- Do not simulate unsupported water/bubble/passenger dynamics approximately; fail closed.
- Legit synthetic item use is armed only when the current vanilla `hitResult` is `MISS`.
- Manual player state always wins over stale restoration.
- All attempt-owned asynchronous state must be cancellable through one idempotent owner cleanup path.
- No unrelated feature work or broad physics rewrite.

---

### Task 1: Import the reviewed 2.5.1 project and establish the baseline

**Files:**
- Create: `projects/pearl-catcher-26-1-2/**` from the reviewed archive, excluding the archive's repository-level `.github/workflows/pearlcatch-build.yml`
- Create: `.github/workflows/pearl-catcher-26-1-2-ci.yml`
- Preserve: `projects/pearl-catcher-26-1-2/src/**`, `scripts/**`, `build.gradle`, `gradle.properties`, `settings.gradle`, `README.md`, `ROOT_CAUSE_ANALYSIS.md`, `LICENSE`, historical project docs

**Interfaces:**
- Consumes: exact reviewed source archive.
- Produces: a first-class project at `projects/pearl-catcher-26-1-2` whose source checksum/content matches the reviewed archive before later commits.

- [ ] **Step 1: Import the source without functional edits**

Keep the original Java/resources/scripts byte-for-byte. Relocate the archive's workflow into the workspace workflow namespace instead of nesting `.github` inside the project.

- [ ] **Step 2: Add workspace-aware CI**

Create `.github/workflows/pearl-catcher-26-1-2-ci.yml` with Java 25 and Gradle 9.6.1, `working-directory: projects/pearl-catcher-26-1-2`, then run:

```bash
./scripts/run-core-tests.sh
for script in scripts/test-*.sh; do bash "$script"; done
gradle clean build --stacktrace
```

Run post-build scripts with the actual built jar/classes arguments required by each script rather than invoking them bare.

- [ ] **Step 3: Verify the baseline**

Run locally when supported:

```bash
cd projects/pearl-catcher-26-1-2
./scripts/run-core-tests.sh
for script in scripts/test-*.sh; do bash "$script"; done
```

Record any build limitation as environment/toolchain evidence, not as a code defect.

- [ ] **Step 4: Commit**

```bash
git add projects/pearl-catcher-26-1-2 .github/workflows/pearl-catcher-26-1-2-ci.yml
git commit -m "chore: import reviewed Pearl Catcher 2.5.1 baseline"
```

---

### Task 2: Make plan safety an invariant, not a ranking preference

**Files:**
- Modify: `projects/pearl-catcher-26-1-2/src/main/java/studio/pixelied/pearlcatch/core/GeneralCatchSolver.java`
- Modify: `projects/pearl-catcher-26-1-2/src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java`

**Interfaces:**
- Consumes: `GeneralCatchSolver.Plan#robustHitFraction()` and `Plan#collisionClearance()`.
- Produces: `GeneralCatchSolver.isExecutable(Plan)` and `MIN_ROBUST_HIT_FRACTION = 0.80`, with `MIN_GEOMETRIC_CLEARANCE = 0.03` used as a hard floor.

- [ ] **Step 1: Write red tests**

Add deterministic assertions equivalent to:

```java
check(!GeneralCatchSolver.isExecutable(planWithReliability(0.00, 0.05)), "0% reliability must be rejected");
check(!GeneralCatchSolver.isExecutable(planWithReliability(0.79, 0.05)), "sub-80% reliability must be rejected");
check(!GeneralCatchSolver.isExecutable(planWithReliability(0.90, 0.029)), "sub-0.03 clearance must be rejected");
check(GeneralCatchSolver.isExecutable(planWithReliability(0.90, 0.04)), "robust clear plan must be accepted");
```

Also add a real solver request reproducing a reviewed zero-percent case and assert `solveExecutable(request) == null` while a known robust nominal request still returns a plan.

- [ ] **Step 2: Run the core test and verify red**

```bash
./scripts/run-core-tests.sh
```

Expected: new acceptance assertions fail against 2.5.1.

- [ ] **Step 3: Implement the minimal hard gate**

Expose internal constants and add:

```java
static final double MIN_ROBUST_HIT_FRACTION = 0.80;
static final double MIN_GEOMETRIC_CLEARANCE = 0.03;

public static boolean isExecutable(Plan plan) {
    return plan != null
            && plan.robustHitFraction() >= MIN_ROBUST_HIT_FRACTION
            && plan.collisionClearance() >= MIN_GEOMETRIC_CLEARANCE;
}

public static Plan solveExecutable(Request request) {
    Plan plan = solve(request);
    return isExecutable(plan) ? plan : null;
}
```

Route execution callers to `solveExecutable`; diagnostics may still call `solve` when they explicitly need to display why a candidate was rejected.

- [ ] **Step 4: Run the core test and all solver architecture scripts**

```bash
./scripts/run-core-tests.sh
bash scripts/test-single-solver-architecture.sh
bash scripts/test-current-camera-targets.sh
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/studio/pixelied/pearlcatch/core/GeneralCatchSolver.java src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java
git commit -m "fix: reject unsafe Pearl Catcher plans"
```

---

### Task 3: Bound Legit input leases and preserve user authority

**Files:**
- Create: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Modify/Create tests/scripts: `projects/pearl-catcher-26-1-2/scripts/test-legit-vanilla-input.sh`, `scripts/test-legit-key-conflicts.sh`, `scripts/test-execution-lifecycle.sh`

**Interfaces:**
- Produces: bounded `LegitInputLease` with `deadlineClientTick`, owner-aware slot/offhand restore, and `cancelOwner(long ownerAttemptId)`.
- `CatchExecutor` initially delegates only input ownership behavior; full coordinator split happens later.

- [ ] **Step 1: Add red behavioral checks**

Require all HOTBAR/SWAP/USE leases to contain an explicit deadline; require timeout handling to clear the global lease and cancel `LegitSilentUseBridge`; require restore guards to compare current state with the exact mod-owned state before writing.

- [ ] **Step 2: Run affected scripts and verify red**

```bash
bash scripts/test-legit-vanilla-input.sh
bash scripts/test-legit-key-conflicts.sh
bash scripts/test-execution-lifecycle.sh
```

- [ ] **Step 3: Implement bounded lease state**

Use a small internal constant such as `LEGIT_CONFIRM_TIMEOUT_TICKS = 4`. On `clientTick > deadlineClientTick`, clear the lease, cancel any armed silent-use bridge, and tell the owner attempt to recompute or abort. Move only this protocol into `VanillaInputExecutor`; do not move solver logic.

- [ ] **Step 4: Make restorations conditional**

For slot/offhand restore, persist `beforeState` and `appliedState`; restore only when the live state still equals `appliedState`. If the user changed it, drop ownership without writing.

- [ ] **Step 5: Verify green and commit**

Run all affected scripts plus `./scripts/run-core-tests.sh`, then commit as `fix: bound Pearl Catcher input ownership`.

---

### Task 4: Make projectile attribution exact and centralize tracking

**Files:**
- Create: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/ProjectileTracker.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchAttemptTracker.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Add: `projects/pearl-catcher-26-1-2/scripts/test-projectile-ownership.sh`

**Interfaces:**
- Produces: `findOwnedPearl(...)`, `findOwnedWind(...)`, and foreign-projectile interference queries.
- Ownership condition: `projectile.getOwner() == localPlayer` before local attempt association.

- [ ] **Step 1: Add red ownership tests**

Require a nearby foreign pearl/wind to be rejected for attempt association while remaining visible to interference checks.

- [ ] **Step 2: Verify red**

```bash
bash scripts/test-projectile-ownership.sh
```

- [ ] **Step 3: Implement owner-filtered tracking**

Remove proximity/fresh-ID as proof of ownership. Keep proximity only as a tie-break/sanity check among local-owned candidates.

- [ ] **Step 4: Route `onEntityLoaded`, delayed catch observation, and debug tracking through `ProjectileTracker`**

No caller may directly claim a projectile without the owner check.

- [ ] **Step 5: Verify and commit**

Run ownership, runtime-decomposition, execution-trace, and core tests; commit as `fix: track only local Pearl Catcher projectiles`.

---

### Task 5: Replace block-only preflight with runtime path safety

**Files:**
- Create: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/RuntimePathSafety.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/PearlCatchDebug.java`
- Add: `projects/pearl-catcher-26-1-2/scripts/test-runtime-path-safety.sh`

**Interfaces:**
- Produces: `PathSafetyResult checkPearl(...)` and `checkWind(...)` with explicit safe/rejection reason.
- Uses source-confirmed 26.1.2 block/world-border clip plus entity interception semantics and refuses unsupported fluid dynamics.

- [ ] **Step 1: Add red checks for entity interception, world border, water, and bubble columns**

The test must also prove a clear-air trajectory stays eligible.

- [ ] **Step 2: Verify red**

```bash
bash scripts/test-runtime-path-safety.sh
```

- [ ] **Step 3: Implement vanilla-faithful path checks**

Use vanilla collision APIs rather than a hand-maintained interactable/entity list. Reject if pearl or wind enters water/bubble-column dynamics before the intended collision.

- [ ] **Step 4: Replace all `firstBlockObstruction(...)` execution gates with `RuntimePathSafety`**

Keep `PearlCatchDebug` rendering/trace formatting separate from safety authority.

- [ ] **Step 5: Verify and commit**

Run path-safety, execution-lifecycle, execution-trace, and core tests; commit as `fix: validate Pearl Catcher runtime paths`.

---

### Task 6: Model delayed execution as a conservative server-age interval

**Files:**
- Create: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/ServerTimingWindow.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Add: `projects/pearl-catcher-26-1-2/scripts/test-server-timing-window.sh`

**Interfaces:**
- Produces: immutable timing window derived from connection latency and tick quantization.
- Delayed execution is allowed only when the same accepted catch remains safe over the whole interval.

- [ ] **Step 1: Add red low-latency, delayed-age, and excessive-uncertainty tests**
- [ ] **Step 2: Verify red with `bash scripts/test-server-timing-window.sh`**
- [ ] **Step 3: Implement RTT-derived interval without arbitrary fixed delay offsets**

Convert current ping to ticks conservatively, include at least one tick of quantization uncertainty, and fail closed when latency is unavailable/stale or the interval exceeds the proven collision window.

- [ ] **Step 4: Re-solve/validate observed pearls against that interval**
- [ ] **Step 5: Verify and commit as `fix: account for server-age uncertainty`**

---

### Task 7: Fix silent rotation lifecycle and Legit interaction safety

**Files:**
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/LegitSilentUseBridge.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/mixin/MinecraftUseMixin.java`
- Modify: `projects/pearl-catcher-26-1-2/scripts/test-silent-use-preserves-movement.sh`
- Modify: `projects/pearl-catcher-26-1-2/scripts/test-legit-vanilla-input.sh`

**Interfaces:**
- Produces: attempt-owned `PendingServerRotationRestore` scheduled only after the final projectile use.
- Legit use precondition: `mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.MISS`.

- [ ] **Step 1: Add red ordering/cleanup/interaction checks**

Require: no restore between pearl and delayed wind; one final restore after the final action; manual rotation cancels stale restore; cancel/disconnect clears it; block/entity hit prevents synthetic Legit use.

- [ ] **Step 2: Verify red**
- [ ] **Step 3: Implement the minimal lifecycle**

Keep the existing rule that a standalone rotation packet must not be sent immediately before projectile spawn when it would zero server-known movement. Restore only after the sequence's final use.

- [ ] **Step 4: Verify and commit as `fix: own Pearl Catcher rotation lifecycle`**

---

### Task 8: Unify cancellation and reject unsupported movement

**Files:**
- Create: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `projects/pearl-catcher-26-1-2/scripts/test-execution-lifecycle.sh`
- Add: `projects/pearl-catcher-26-1-2/scripts/test-owner-cleanup.sh`

**Interfaces:**
- Produces: `cancelOwner(long ownerId, CancelReason reason)` as the single idempotent cleanup route.
- Coordinator rejects `player.isPassenger()` before solver execution until passenger semantics are modeled.

- [ ] **Step 1: Add red cleanup tests**

Cover disable, disconnect/world change, debug sweep cancellation, supersession, repeated cancellation, and passenger rejection.

- [ ] **Step 2: Verify red**
- [ ] **Step 3: Implement owner-token cleanup across input, tracking, delayed wind, restores, and debug state**
- [ ] **Step 4: Route normal completion/abort through the same path**
- [ ] **Step 5: Verify and commit as `fix: centralize Pearl Catcher attempt cleanup`**

---

### Task 9: Finish the executor decomposition without changing behavior

**Files:**
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchCoordinator.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/VanillaInputExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/ProjectileTracker.java`
- Reduce: `projects/pearl-catcher-26-1-2/src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Modify: `projects/pearl-catcher-26-1-2/scripts/test-runtime-decomposition.sh`

**Interfaces:**
- `CatchCoordinator`: lifecycle, solving, acceptance, timing, high-level cleanup.
- `VanillaInputExecutor`: Fast/Legit input, leases, temporary inventory state, rotation lifecycle, interaction safety.
- `ProjectileTracker`: owned projectile acquisition and interference observation.

- [ ] **Step 1: Strengthen the decomposition regression before moving remaining methods**
- [ ] **Step 2: Move methods in small compilable groups without changing public behavior**
- [ ] **Step 3: Delete dead helpers such as unused `targetZoneTolerance()` once references prove none remain**
- [ ] **Step 4: Run every project regression script and core test**
- [ ] **Step 5: Commit as `refactor: simplify Pearl Catcher runtime ownership`**

---

### Task 10: Prune solver hot-path work without weakening correctness

**Files:**
- Modify: `projects/pearl-catcher-26-1-2/src/main/java/studio/pixelied/pearlcatch/core/GeneralCatchSolver.java`
- Modify: `projects/pearl-catcher-26-1-2/src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java`
- Add: `projects/pearl-catcher-26-1-2/scripts/benchmark-core-solver.sh`

**Interfaces:**
- Must preserve accepted-plan geometry and the 64-sample execution gate.

- [ ] **Step 1: Add a repeatable microbenchmark and record baseline median/max separately from correctness tests**
- [ ] **Step 2: Profile candidate counts and robustness evaluations**
- [ ] **Step 3: Implement only measured wins: deduplicate candidates, cheap-bound pruning, per-solve pure caches, and early robustness failure once 80% becomes mathematically unreachable**

For 64 samples, a candidate needing 80% can stop once misses exceed `64 - ceil(0.80 * 64) = 12`; the 13th miss makes acceptance impossible.

- [ ] **Step 4: Prove plan outputs/correctness tests remain green**
- [ ] **Step 5: Commit as `perf: prune Pearl Catcher solver hot path`**

---

### Task 11: Final CI, artifact, workspace, and PR verification

**Files:**
- Modify as needed: `.github/workflows/pearl-catcher-26-1-2-ci.yml`
- Modify: `projects/pearl-catcher-26-1-2/README.md`
- Add evidence: `tasks/harden-pearl-catcher-26-1-2/artifacts/verification.md`

**Interfaces:**
- Produces: production jar evidence and a complete acceptance-criterion audit.

- [ ] **Step 1: Run the complete project verification**

```bash
cd projects/pearl-catcher-26-1-2
./scripts/run-core-tests.sh
for script in scripts/test-*.sh; do bash "$script"; done
gradle clean build --stacktrace
```

- [ ] **Step 2: Run ABI/post-build scripts against the built output**

Use each script's documented jar/classes argument. Any mismatch is a failure, not a warning.

- [ ] **Step 3: Audit the production artifact**

Confirm Java 25 classfile compatibility, required Fabric metadata/mixins, correct Pixelied Studio identity/version, and absence of source/debug/archive junk.

- [ ] **Step 4: Run workspace verification**

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

- [ ] **Step 5: Compare `main...fix/pearl-catcher-26-1-2-hardening` and inspect every changed file for unrelated edits**
- [ ] **Step 6: Record verification evidence, update PR #29 from draft, and merge only when fresh GitHub Actions checks are green**
