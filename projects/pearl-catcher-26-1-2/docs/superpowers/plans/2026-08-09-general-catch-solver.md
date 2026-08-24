# General Catch Solver Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the hybrid planner stack with one mathematical, continuously replanning catch solver.

**Architecture:** Add `GeneralCatchSolver` as the sole core planner. It derives candidate launch vectors from catch tick/range/wind delay, verifies exact AABB entry and earlier-collision exclusion, samples vanilla spread, and exposes one plan type for both pre-pearl and known-pearl states. Refactor `PearlCatchMode` to one pending-shot state that calls the same solver until wind use.

**Tech Stack:** Java 25 source target, Minecraft 26.1.2, Fabric Loader 0.19.3, Fabric API 0.153.0+26.1.2.

## Global Constraints
- Fully client-side; only vanilla packets/item-use behavior.
- Preserve one-way collision authority: pearl segment enters wind-charge AABB.
- Full player launch momentum must be represented.
- Exact AABB entry semantics and collision margin must be preserved.
- No normal-user planner/timing modes.

---

### Task 1: Mathematical projectile primitives
- [ ] Add failing tests for closed-form pearl displacement/inversion and wind reachable launch direction.
- [ ] Run tests and confirm RED.
- [ ] Add minimal physics helpers to `VanillaProjectilePhysics`.
- [ ] Run tests and confirm GREEN.

### Task 2: General solver core
- [ ] Add failing tests for 12b flat, steep up/down, delay selection, target-distance movement, no early collision, known-pearl replan, and momentum cases.
- [ ] Run tests and confirm RED.
- [ ] Implement `GeneralCatchSolver` with one candidate space and exact verification.
- [ ] Run tests and confirm GREEN.

### Task 3: Remove policy split from runtime
- [ ] Add/adjust compile-level tests for one pending state and same-solver replanning behavior.
- [ ] Replace hybrid policy/planner branches in `PearlCatchMode` with `PendingCatch` + `GeneralCatchSolver` calls.
- [ ] Remove normal wind-timing/prediction controls from config screen; keep debug horizon internal.
- [ ] Update trace strategy fields to solver/action fields.
- [ ] Run core and API-signature compile.

### Task 4: Performance and regression
- [ ] Benchmark warmed solve across -90..90 pitches and representative target ranges.
- [ ] Verify no duplicate full solver pass per G press.
- [ ] Run extreme player-motion probes.
- [ ] Verify source docs and metadata version 2.3.0.

### Task 5: Package and exact-artifact verification
- [ ] Compile production source against corrected 26.1.2/Fabric ABI stubs.
- [ ] Run manual ABI audit and Gson regression audit.
- [ ] Package only mod classes/resources and patch Java class major to 69.
- [ ] Smoke-load exact packaged classes with only temporary local class-header downgrade.
- [ ] Verify metadata, no leaked stubs, source archive, and SHA-256 hashes.
