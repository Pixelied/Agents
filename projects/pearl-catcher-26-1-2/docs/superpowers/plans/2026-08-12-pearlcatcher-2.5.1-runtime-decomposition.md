# Pearl Catcher 2.5.1 Runtime Decomposition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Split `PearlCatchMode` into focused runtime components without changing catch physics, execution timing, key behavior, offhand behavior, debug semantics, or the one-solver architecture.

**Architecture:** Keep `PearlCatchMode` as the public orchestration facade. Extract item/input execution helpers into `CatchExecutor`, projectile/attempt bookkeeping helpers into `CatchAttemptTracker`, and trace/visualization/export code into `PearlCatchDebug`. Keep `GeneralCatchSolver` untouched as the only physics planner.

**Tech Stack:** Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.153.0+26.1.2, Java 25 target, existing strict signature/manual verification harness.

## Global Constraints

- Zero intentional gameplay behavior changes.
- Exactly one physics planner: `GeneralCatchSolver`.
- No Elytra/Legit/Fast/Offhand planner classes.
- Fast and Legit semantics remain byte-for-byte equivalent at the call boundaries where practical.
- Offhand priority remains: offhand -> selected main hand -> another hotbar slot -> unavailable.
- Legit execution continues through configured vanilla hotbar/Use/swap-offhand key mappings.
- Silent execution must not add a standalone pre-use movement/rotation packet.
- Debug JSON/text field names remain unchanged.
- Product identity remains Pixelied Studio and package `studio.pixelied.pearlcatch`.
- Distributed classfiles remain Java 25 / major 69.

---

### Task 1: Add a decomposition regression

**Files:**
- Create: `scripts/test-runtime-decomposition.sh`

**Interfaces:**
- Requires: `CatchExecutor.java`, `CatchAttemptTracker.java`, `PearlCatchDebug.java`.
- Requires: `PearlCatchMode.java` under 900 lines after extraction.
- Forbids: new planner names.

- [x] Write the structural test first.
- [x] Run it and verify RED because the three components do not exist.
- [x] Do not edit production code until RED is observed.

### Task 2: Extract debug/trace/visualization

**Files:**
- Create: `src/client/java/studio/pixelied/pearlcatch/PearlCatchDebug.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Update source-level debug regressions only to follow the new ownership; do not weaken assertions.

**Interfaces:**
- `PearlCatchDebug` owns trace DTOs/session/export, trajectory visualization, overlay formatting, trajectory prediction helpers, obstruction checks, and debug formatting.
- `PearlCatchMode` calls these methods; it does not duplicate them.

- [x] Move code mechanically.
- [x] Run core + execution + identity regressions.
- [x] Verify debug JSON field names from 2.5.0 still exist exactly once.

### Task 3: Extract item/input executor helpers

**Files:**
- Create: `src/client/java/studio/pixelied/pearlcatch/CatchExecutor.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- `CatchExecutor.ItemLocation` resolves main/offhand inventory location.
- `CatchExecutor` owns vanilla key-click helpers, cooldown/item resolution helpers, fast slot selection, and rotation-aware single-use execution helpers.
- `PearlCatchMode` retains attempt orchestration and timing decisions.

- [x] Move helpers without changing priority or key APIs.
- [x] Run offhand, Legit vanilla-input, key-conflict, silent-movement and lifecycle regressions.

### Task 4: Extract attempt/entity bookkeeping

**Files:**
- Create: `src/client/java/studio/pixelied/pearlcatch/CatchAttemptTracker.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- Owns attempt state DTOs (`PendingCatch`, `TrackingShot`, Legit preparation/restore state) and pure entity lookup/claim/gap helpers.
- Does not solve physics and does not execute keys.

- [x] Move state classes/helpers mechanically.
- [x] Run lifecycle, single-solver, trace and current-camera regressions.

### Task 5: Final behavior-equivalence and package verification

**Files:**
- Modify: `gradle.properties` version to `2.5.1`.
- Modify README only to document the internal decomposition; no behavioral promises changed.

- [x] Run every source-level/core regression.
- [x] Compile production source against the strict 26.1.2/Fabric signature harness.
- [x] Package exact jar and patch/check class major 69.
- [x] Run ABI, Event-class ABI, identity, class/package/planner scans, and packaged startup-linkage smoke.
- [x] Compare public config/resources with 2.5.0 and confirm only version/internal class layout changed.
