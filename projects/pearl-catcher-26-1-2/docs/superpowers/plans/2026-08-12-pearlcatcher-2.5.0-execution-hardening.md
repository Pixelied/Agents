# Pearl Catcher 2.5.0 Execution Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Harden the existing single-solver Pearl Catcher so Fast and Legit execution both work with main hand/offhand layouts, real vanilla key mappings, elytra/motion changes, overlapping attempts, and timing-safe re-solving.

**Architecture:** Keep exactly one `GeneralCatchSolver`. Add only execution-state machinery around it: a small legit-input lease/scheduler in `PearlCatchMode`, explicit item-location resolution, and a minimal silent-use bridge/mixin so Legit+Silent can still use Minecraft's real Use key path without leaking a pre-use rotation movement packet. Add one solver constraint, `minimumWindDelayTicks`, so execution latency is a solved constraint rather than a planner mode.

**Tech Stack:** Minecraft 26.1.2 decompiled source, Fabric Loader 0.19.3+, Fabric API 0.153.0+26.1.2, Java 25 target, Gradle/Loom project metadata, Java 21 local verification harness with final classfile-major patching to 69 where required.

## Global Constraints

- Minecraft version remains exactly `26.1.2`.
- Fabric Loader floor remains `0.19.3`.
- Fabric API floor remains `0.153.0+26.1.2`.
- Java target remains 25 / classfile major 69 in the distributed jar.
- Product identity remains Pixelied Studio; Java namespace remains `studio.pixelied.pearlcatch`.
- Exactly one physics planner: `GeneralCatchSolver`; do not add predictive/reactive/elytra/offhand/legit planner classes.
- Fast and Legit must use the same physics model, collision authority, clearance ranking, pearl-age semantics, and re-solving rules.
- Legit must use configured vanilla key mappings for hotbar, Use, and Swap Item With Offhand; no direct use-item shortcut in Legit mode.
- Silent execution must not send a standalone pre-use movement/rotation packet.
- Source bundle has no `.git`; each task ends with a verification checkpoint rather than an impossible commit.

---

### Task 1: Make execution latency a solver constraint

**Files:**
- Modify: `src/main/java/studio/pixelied/pearlcatch/core/GeneralCatchSolver.java`
- Modify: `src/test/java/studio/pixelied/pearlcatch/core/GeneralCatchSolverSelfTest.java`
- Modify call sites in: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`

**Interfaces:**
- Consumes: existing `GeneralCatchSolver.Request` and delay search.
- Produces: `Request.minimumWindDelayTicks()`; `evaluateWindDelays` never returns a plan with a smaller relative wind delay.

- [x] **Step 1: Add a failing core regression**

Add a test that solves the same known physical setup twice, once with `minimumWindDelayTicks=0` and once with `minimumWindDelayTicks=3`, and asserts the second plan is either null or has `windDelayTicksFromNow() >= 3`. Add a validation regression for a negative minimum delay.

- [x] **Step 2: Run `scripts/run-core-tests.sh` and verify RED**

Expected: compile failure because `Request` does not yet expose `minimumWindDelayTicks`.

- [x] **Step 3: Add the request field and enforce it in the existing delay loop**

Change the request record tail to:

```java
public record Request(
        Vec3d pearlLaunchPosition,
        Vec3d pearlInheritedMotion,
        Vec3d knownPearlLaunchVelocity,
        int completedPearlTicks,
        Vec3d currentEyePosition,
        Vec3d currentInheritedMotion,
        Rotation targetRotation,
        int maxPredictionTicks,
        double maxSearchDistance,
        double maxCrosshairDistance,
        double preferredCatchDistance,
        int spreadSamples,
        int minimumWindDelayTicks
) {}
```

Validation requires `minimumWindDelayTicks >= 0`. Delay evaluation starts at `Math.max(0, request.minimumWindDelayTicks())` and retains the current maximum-delay logic. This is a timing constraint inside the same solver, not a second planner.

- [x] **Step 4: Update every `Request` constructor and rerun core tests**

Existing Fast behavior passes `0`. Legit callers pass the currently executable minimum delay.

- [x] **Step 5: Checkpoint**

Run `scripts/run-core-tests.sh` and `scripts/test-single-solver-architecture.sh`; both must pass.

---

### Task 2: Add independent Item Switching configuration

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfig.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchConfigScreen.java`
- Create: `scripts/test-execution-settings.sh`

**Interfaces:**
- Produces: `PearlCatchConfig.ItemSwitchMode { FAST, LEGIT }`, persisted as `itemSwitchMode` with default `FAST`.
- Existing `RotationMode` remains independent.

- [x] **Step 1: Write structural regression first**

The script must fail unless all of these exist:

```text
ItemSwitchMode
FAST
LEGIT
itemSwitchMode
Item switching:
Rotation:
```

It also verifies there is no class/file containing `ElytraPlanner`, `LegitPlanner`, `FastPlanner`, or `OffhandPlanner`.

- [x] **Step 2: Run the new script and verify RED**

Expected: missing `ItemSwitchMode`.

- [x] **Step 3: Implement config enum/default/sanitize/reset and UI button**

Add a button adjacent to Rotation that cycles `Fast` / `Legit`. Keep the screen compact by moving the existing first-row controls down rather than creating a new settings page.

- [x] **Step 4: Run structural regression and identity guard**

Run `scripts/test-execution-settings.sh` and `scripts/test-pixelied-studio-identity.sh`.

- [x] **Step 5: Checkpoint**

Verify `fabric.mod.json` is unchanged apart from later release version expansion.

---

### Task 3: Add hand/item resolution and Fast offhand support

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Create: `scripts/test-offhand-fast-path.sh`

**Interfaces:**
- Produces nested `ItemLocation` with `InteractionHand hand`, hotbar slot where applicable, and helpers that resolve the required item at execution time.
- Generalize item use to `useHandAtRotation(..., InteractionHand hand, ...)`.

- [x] **Step 1: Write failing source-level regression**

Require all of:

```text
InteractionHand.OFF_HAND
getOffhandItem
resolveItemLocation
useHandAtRotation
```

and forbid the launch gate text `NO_ENDER_PEARL_IN_HOTBAR` / `NO_WIND_CHARGE_IN_HOTBAR` as the only inventory criterion.

- [x] **Step 2: Verify RED**

Run `scripts/test-offhand-fast-path.sh`.

- [x] **Step 3: Implement dynamic resolver**

Resolution priority is exactly:

```text
offhand -> selected main hand -> another hotbar slot -> unavailable
```

Fast use behavior:

```java
if (location.hand() == InteractionHand.OFF_HAND) {
    useHandAtRotation(mc, player, InteractionHand.OFF_HAND, rotation, config.rotationMode);
} else {
    player.getInventory().setSelectedSlot(location.slot());
    useHandAtRotation(mc, player, InteractionHand.MAIN_HAND, rotation, config.rotationMode);
}
```

Check item cooldown immediately before each use with the currently resolved stack. Do not store a wind slot from launch time; re-resolve wind when it is actually needed.

- [x] **Step 4: Make restoration non-destructive**

Only restore a selected slot if the executor still sees the slot it selected. If the player manually changed away, do not overwrite that manual choice.

- [x] **Step 5: Verify Fast behavior structurally and core physics**

Run the new offhand test, core solver test, single-solver guard, and identity guard.

---

### Task 4: Implement Legit vanilla-key execution with one small scheduler

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchClient.java`
- Create: `src/client/java/studio/pixelied/pearlcatch/LegitSilentUseBridge.java`
- Create: `src/client/java/studio/pixelied/pearlcatch/mixin/MinecraftUseMixin.java`
- Create: `src/main/resources/pearlcatch.mixins.json`
- Modify: `src/main/resources/fabric.mod.json`
- Create: `scripts/test-legit-vanilla-input.sh`

**Interfaces:**
- Legit hotbar: `KeyMapping.click(KeyMappingHelper.getBoundKeyOf(mc.options.keyHotbarSlots[slot]))`.
- Legit Use: same mechanism with `mc.options.keyUse`.
- Legit offhand swap: same mechanism with `mc.options.keySwapOffhand`.
- Only one outstanding input lease at a time; this serializes key mutations without serializing projectile lifetimes.

- [x] **Step 1: Add RED structural regression**

Require references to:

```text
KeyMapping.click
KeyMappingHelper.getBoundKeyOf
keyHotbarSlots
keyUse
keySwapOffhand
pearlcatch.mixins.json
LegitSilentUseBridge
```

Forbid direct `gameMode.useItem` from the Legit branch.

- [x] **Step 2: Verify RED**

Run `scripts/test-legit-vanilla-input.sh`.

- [x] **Step 3: Add one global Legit input lease**

Use one nested lease record/state in `PearlCatchMode`, not separate planners. A lease records owner attempt id, input kind (`HOTBAR`, `SWAP_OFFHAND`, `USE`), requested client tick, and expected state. No other Legit attempt queues a conflicting key until the lease is consumed/confirmed.

A queued hotbar click is confirmed by selected slot. A queued swap is confirmed by required item arriving in selected main hand. A queued Use is considered consumed after a normal gameplay tick has passed; actual success is confirmed separately by observing the expected projectile.

- [x] **Step 4: Add pre-pearl Legit attempt state**

A `LegitPearlLaunch` owns target, prior slot, launch entity-id snapshots, required rotation, and offhand-swap state. It prepares pearl via real key clicks, recomputes a fresh solver plan just before queueing Use, and starts pearl age only after a real pearl is observed.

Initial solver minimum wind delay is derived from the exact 26.1.2 key-processing order:

```text
required wind already confirmed in selected main hand: 0 ticks
one outstanding hotbar or swap-hands preparation: 1 tick minimum
unavailable: do not throw pearl
```

A Use click queued at END_CLIENT_TICK is consumed by the next normal `handleKeybinds()` pass before projectile entity advancement, so a fully prepared Legit wind can execute a solver delay of zero. These are execution lower bounds, not timing presets; every preparation confirmation triggers fresh solving, and real pearl observation remains authoritative.

- [x] **Step 5: Add Legit wind preparation to existing pending catch lifecycle**

For a known real pearl, re-run `GeneralCatchSolver` every relevant tick with `minimumWindDelayTicks` equal to the execution latency still required. Start hotbar/offhand preparation early. Queue the Use key only when the item is confirmed ready and the current plan says its relative delay is executable on the next vanilla keybind tick. If readiness slips, re-solve rather than firing late.

- [x] **Step 6: Implement Legit offhand swap/restore**

If the required item is in offhand, use the real swap-hands key and wait for the selected main hand to contain the projectile. Restore the hand arrangement only when the selected slot is still the slot used for the swap and no newer input lease owns it; otherwise leave manual user state untouched.

- [x] **Step 7: Implement Legit+Silent without a pre-use movement packet**

`MinecraftUseMixin` injects around private `Minecraft.startUseItem()` only when `LegitSilentUseBridge` is armed by Pearl Catcher. HEAD temporarily applies the solved yaw/pitch; RETURN restores the camera immediately. The Use key still invokes vanilla `startUseItem`, and no standalone move/rotation packet is emitted.

Visible arms the solved camera visibly before Use. Current never changes rotation.

- [x] **Step 8: Verify key-path source rules**

Run `scripts/test-legit-vanilla-input.sh` and `scripts/test-silent-use-preserves-movement.sh`.

---

### Task 5: Harden lifecycle, overlapping attempts, and stale-plan behavior

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Create: `scripts/test-execution-lifecycle.sh`

**Interfaces:**
- New attempts get monotonically increasing attempt IDs.
- Projectile identity remains per-attempt; input actions are serialized only while a key mutation is outstanding.

- [x] **Step 1: Write lifecycle regression first**

The script requires explicit checks for:

```text
screen != null / overlay != null before Legit key synthesis
cooldown before use
world/player identity reset
manual selected-slot mismatch
old wind hazard rejection
multiple attempt IDs
```

and verifies there is no single `busy` flag that blocks all G/H attempts while old projectiles live.

- [x] **Step 2: Verify RED where coverage is missing**

Run the script and record the missing guards.

- [x] **Step 3: Add reset/expiry semantics**

On player identity/world loss/death, clear pending Legit input, pending launches, pending catches, and camera bridge state. Existing active shot tracking may finish only while its world/player is still valid.

- [x] **Step 4: Revalidate state immediately before every action**

Re-resolve the item, cooldown, selected slot/hand, camera constraint, current inherited movement, old-wind hazard, and current plan. Refuse stale actions and wait/re-solve.

- [x] **Step 5: Preserve overlapping projectile attempts while serializing only key input**

G/H may append new attempts even if previous pearls/winds remain. A timing-critical older wind action has priority over starting another Legit pearl action, but a waiting older projectile does not globally lock new attempts.

- [x] **Step 6: Verify lifecycle matrix**

Run execution lifecycle, offhand, Legit input, core physics, and single-solver scripts.

---

### Task 6: Diagnostics, version 2.5.0, strict packaging, and final verification

**Files:**
- Modify: `src/client/java/studio/pixelied/pearlcatch/PearlCatchMode.java`
- Modify: `README.md`
- Modify: `ROOT_CAUSE_ANALYSIS.md`
- Modify: `gradle.properties`
- Modify: `scripts/verify-manual-abi.sh`
- Add/modify strict compile/startup smoke harness files as needed under `/mnt/data` only; do not package stubs.

**Interfaces:**
- Debug trace records item switching mode, resolved hand, execution stage, minimum executable wind delay, and whether use was Fast-direct or Legit-key.

- [x] **Step 1: Extend trace fields**

Add enough execution state to diagnose a miss without adding planner output: switch mode, requested/confirmed hand or slot, use-request tick, projectile-observed tick, minimum delay constraint, and abort/wait reason.

- [x] **Step 2: Bump release metadata to 2.5.0 and document controls**

README must state:

```text
G = catch
H = vertical catch
B = debug sweep
Item Switching = Fast / Legit
Rotation = Silent / Visible / Current
```

and explain offhand support and the no-100%-guarantee physical boundary.

- [x] **Step 3: Reconstruct strict compile signatures from the exact 26.1.2 source / Fabric primary source**

The harness must include exact class-vs-interface shape for Fabric `Event`, exact KeyMapping/Options fields used by Legit mode, and Mixin annotation signatures. Compile all production source with `--release 21` in this environment, then patch only final mod classfile major headers from 65 to 69 for release packaging.

- [x] **Step 4: Extend ABI audit**

Require correct references for `KeyMapping.click`, Fabric `KeyMappingHelper.getBoundKeyOf`, key mappings, offhand stack access, explicit `InteractionHand.OFF_HAND`, Event.register as class `Methodref`, and the existing packet/particle/UI descriptors. Forbid stub classes in the final jar.

- [x] **Step 5: Package exact final artifacts**

Create:

```text
/mnt/data/Pixelied-Studio-Pearl-Catcher-2.5.0.jar
/mnt/data/Pixelied-Studio-Pearl-Catcher-2.5.0-source.zip
```

Include only production mod classes/resources in the jar and project source/docs/scripts in the source zip.

- [x] **Step 6: Run final evidence gate**

Run every script under `scripts/`, strict production compile, class-major scan, namespace/identity scan, legacy planner scan, exact final-jar ABI audit, and packaged startup linkage smoke. Do not claim completion if any check fails.

- [x] **Step 7: Publish hashes**

Compute SHA-256 for both artifacts and report the Java-21/manual-packaging caveat explicitly.
