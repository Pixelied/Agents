# Crystal Anchor Combat Optimizer V2 Design

Date: 2026-08-19
Status: Approved design direction; implementation not started
Target: Minecraft Java 26.1.2, Fabric client-side
Primary objective: absolute lethal speed

## 1. Purpose

V2 redesigns the combat runtime around one principle:

> Deliver the maximum useful lethal damage in the minimum real server time using only legitimate, observable Minecraft state.

V1 proved several important mechanics and correctness properties, but its runtime is too planner-centric. The current execution path performs too much work between an actionable combat event and the outgoing vanilla interaction. V2 keeps the verified mechanics core and replaces the orchestration around it with an event-driven reactive engine inspired by the practical simplicity of mature Crystal Aura implementations such as Future Client.

Future Client is reference material only. The supplied archive is decompiled/semi-deobfuscated and no reusable license has been established. V2 may reproduce observed behavior or architecture ideas independently, but must not copy Future source unless a compatible license is later verified.

## 2. Goals

1. Minimize event-to-packet latency for already-approved combat actions.
2. Prioritize lethal throughput over global move optimality.
3. Make crystal break/place recycling a first-class combat loop.
4. Exploit vanilla 26.1.2 progressive hurt-window behavior with useful damage staircases instead of wasting equal-or-weaker hits.
5. React to server-observed crystal spawns, removals, block changes, equipment changes, and totem pops immediately.
6. Make damage predictions materially more trustworthy by representing uncertainty explicitly rather than emitting a single overconfident number.
7. Learn timing from real event classes instead of collapsing all network behavior into one RTT-like estimate.
8. Keep every executed action legal under vanilla client-observable state: no fabricated entity IDs, hidden inventory, impossible state, silent/server-only rotations, fake crits, or fake server RNG knowledge.
9. Add Mod Menu integration with a small practical configuration surface and a deeper read-only developer diagnostics view.
10. Reduce runtime coupling so targeting, damage, timing, strategy, execution, inventory, and diagnostics can be tested independently.

## 3. Non-goals

- No automatic movement in V2.
- No packet exploits requiring invented or impossible server state.
- No prediction of server-assigned crystal entity IDs.
- No assumption that multiple crystals can simultaneously occupy the same placement volume.
- No fake exact damage when the vanilla client does not know required state.
- No giant user-facing tuning matrix for internal planner constants.
- No attempt to predict exact server-side explosion terrain RNG before block updates arrive.
- No requirement that the globally optimal theoretical sequence be found before a strong immediate hit can execute.

## 4. Design philosophy

The strategic system prepares opportunities; the reactive system kills.

The expensive side of the mod should continuously answer questions such as:

- Which target is worth attacking?
- Which existing crystal is best to break?
- Which bases are best to place or recycle?
- Which charged anchor is the best immediate finisher?
- Which next explosion produces the most useful marginal damage during the current hurt window?
- Which actions are safe enough for the configured strategy?

Once those answers are available, a hot combat event must not rerun the strategic planner. The reactive lane reads precomputed approvals, performs a tiny current-state legality/safety/duplicate check, and dispatches the vanilla action.

A good immediate action wins over a theoretically prettier delayed sequence unless the immediate action materially worsens the kill race or violates safety/legality.

## 5. High-level architecture

```text
World + packet observations
          |
          v
+----------------------+       +----------------------+
|   Combat Observer    |       |    Timing Observer   |
+----------------------+       +----------------------+
          |                           |
          +-------------+-------------+
                        v
               +------------------+
               | CombatBlackboard |
               +------------------+
                  |             |
                  v             v
       +------------------+   +-----------------------+
       | StrategicScanner |   | ReactiveCombatEngine  |
       +------------------+   +-----------------------+
                  |             |
                  +------+-+----+
                         v v
                    +-------------+
                    | ActionArbiter|
                    +-------------+
                         |
                         v
              +-------------------------+
              | VanillaInteraction...   |
              +-------------------------+
```

Supporting components:

- `TargetManager`
- `DamageEngine`
- `TimingEngine`
- `InventoryManager`
- `CombatDiagnostics`
- `OptimizerConfig`

`CrystalOptimizerClient` becomes bootstrap-only. `CombatCoordinator` owns enable/disable and subsystem ordering. No single runtime class should simultaneously own target selection, movement history, planner budgets, timing, inventory, execution, diagnostics, and config.

## 6. CombatBlackboard

`CombatBlackboard` is a compact, versioned current-state exchange between strategic and reactive components. It is not a second world simulation.

It should expose immutable snapshots or atomically replaceable records containing at least:

- selected target and target generation/version;
- latest observed target position/velocity envelope;
- current world/geometry revision;
- approved existing-crystal break;
- approved immediate crystal placement;
- approved recycle base;
- approved immediate anchor action;
- approved totem-pop finisher;
- approved next hurt-window staircase action;
- damage intervals and provenance for approvals;
- self-damage worst-case bounds;
- required hand/hotbar state;
- packet/server-feedback dependency information;
- timing estimates for relevant action transitions;
- expiration/invalidation conditions;
- last action token used for duplicate suppression.

Approvals are short-lived. A target move, relevant block change, entity removal, inventory change, or strategy/config change may invalidate only the affected approval instead of rebuilding all combat state.

## 7. Reactive fast lane

### 7.1 Core rule

The reactive fast lane must never run a full candidate search or beam search. Its hot path should be bounded to small lookups, state/version validation, legality/safety checks, duplicate suppression, and dispatch.

### 7.2 Event sources

At minimum:

- crystal entity spawn observed;
- crystal entity removal/destruction observed;
- relevant block state change/ack observed;
- totem activation observed;
- relevant equipment/hand update observed;
- inventory/selected-slot change;
- target movement update that invalidates a prepared approval;
- configured toggle/strategy change.

### 7.3 Crystal base lifecycle

For each actively used base, V2 tracks a small lifecycle such as:

```text
EMPTY
  -> PLACE_SENT
  -> LIVE(entityId)
  -> BREAK_SENT
  -> EMPTY or INVALID
```

`LIVE(entityId)` requires a real server-observed entity ID. V2 never predicts a new ID.

### 7.4 Break -> replace loop

For an already-known live crystal on an approved recycle base, V2 may dispatch:

```text
AttackKnownCrystal(realEntityId)
PlaceCrystal(approvedBase)
```

back-to-back when both interactions are individually legal under current client state and the placement is safe under the approved predicted transition.

The next attack cannot occur until the new crystal's real spawn/entity ID is observed:

```text
known #381 -> attack -> place -> wait only for spawn ID -> observed #402 -> attack -> place
```

This is the intended high-throughput same-base reuse pattern. V2 does not attempt to place multiple simultaneous crystals into the same occupied volume.

If the placement is rejected, the base becomes blocked, another entity occupies the volume, inventory is exhausted, target geometry becomes bad, or reconciliation disproves an assumption, the recycle loop stops immediately and returns control to refreshed approvals.

### 7.5 No artificial fast-lane CPS cap

The `Lethal Speed` strategy does not impose a stopwatch-based attack/place CPS limit on the reactive lane. Throughput is constrained by:

- real vanilla legality;
- server-observed entity IDs;
- required server feedback boundaries;
- current hand/inventory state;
- rotation mode and alignment;
- duplicate-action suppression;
- self-survival policy;
- relevant server/state transitions.

Strategic/non-critical preparation may still be paced to avoid needless interaction spam.

### 7.6 Priority order

Reactive decisions use this ordering:

1. high-confidence immediate lethal action or zero-feedback lethal burst;
2. totem-pop finisher;
3. useful hurt-window damage-staircase continuation;
4. approved high-throughput crystal recycle;
5. highest useful immediate break/place damage;
6. preparation action supplied by the strategic scanner.

A newly observed totem pop may preempt a recycle loop if a prepared finisher remains legal and has better lethal time.

## 8. Damage Engine V2

### 8.1 Preserve exact 26.1.2 mechanics

V2 reuses and validates the existing mechanics work for:

- crystal and anchor explosion constants;
- explosion distance/exposure formula;
- difficulty scaling;
- blocking/shield geometry;
- armor and toughness;
- armor durability ordering before mitigation for the same accepted damage event;
- Protection/Blast Protection and Resistance handling;
- absorption ordering;
- totem transition;
- progressive hurt-window behavior;
- correct modern crystal placement legality;
- anchor charge/detonation legality based on environment rules.

### 8.2 Damage intervals, not fake precision

Every live damage estimate becomes a `DamageEstimate` with at least:

- `lowerBound`;
- `expected`;
- `upperBound`;
- `confidence`;
- `provenance`/uncertainty reasons;
- `geometryRevision`;
- relevant combat-state revision.

When all necessary inputs are exact and observable, the interval may collapse to one value.

Examples of uncertainty reasons include:

- unknown remote protected-window `lastHurt` threshold;
- remote absorption consumption not exactly synchronized;
- predicted target position envelope;
- unobserved post-explosion terrain destruction;
- stale or changed armor/effect state;
- pending server acceptance of a predicted block interaction.

Target damage must be conservative when claiming lethality. Self-damage must use a pessimistic/worst-case view when uncertainty exists.

### 8.3 Damage trace

Every explosion candidate and executed explosion can produce a trace containing:

- source/action identity;
- explosion center/type;
- target box/position hypothesis;
- exposure;
- raw incoming damage;
- difficulty-scaled damage;
- blocking result;
- hurt-window decision/range;
- post-armor damage;
- post-effects/enchantment damage;
- absorption estimate/consumption range;
- predicted health loss range;
- self-damage range;
- geometry revision;
- prediction confidence.

### 8.4 Calibration and mismatch classification

V2 correlates executed explosions with later server-observable outcomes when attribution is sufficiently unambiguous. The goal is diagnosis, not arbitrary numerical correction.

Mismatch classes include:

- `EXPOSURE_MISMATCH`
- `STALE_GEOMETRY`
- `HURT_THRESHOLD_UNKNOWN`
- `ABSORPTION_UNCERTAINTY`
- `EFFECT_STATE_CHANGED`
- `TARGET_MOVED`
- `ARMOR_STATE_CHANGED`
- `ACTION_NOT_SERVER_ACCEPTED`
- `INTERFERENCE`
- `UNKNOWN`

V2 must not learn a global `damageMultiplier`, fudge factor, or unexplained offset to hide simulator defects.

### 8.5 Vanilla exposure differential verification

Where Minecraft 26.1.2 exposes a usable authoritative live exposure helper, V2 should prefer the game's own current-world exposure result for live exact-current-state checks and retain the project implementation for deterministic simulation/testing.

Regardless of live implementation choice, GameTests/differential tests must compare project exposure against vanilla across representative collision geometry: full blocks, slabs, stairs, corners, partial cover, edge positions, different target boxes, and multiple explosion offsets.

### 8.6 Terrain uncertainty

Server explosion block destruction may depend on server-side RNG unavailable to the client. A follow-up action whose lethality depends on unobserved destruction cannot be certified exact before the corresponding block updates.

However, if the follow-up is already good under the pessimistic current terrain, V2 may still execute immediately. Uncertainty blocks false certainty, not necessarily useful pressure.

### 8.7 Damage map

The strategic scanner maintains a small target-local damage map for:

- currently known crystals;
- legal crystal bases;
- relevant respawn anchors;
- rare support/setup opportunities.

Entries are invalidated incrementally by target movement, nearby block changes, relevant equipment/effect changes, entity lifecycle events, or geometry revision changes.

The reactive lane reads the current approved entries and does not rebuild the entire region.

## 9. Hurt-window damage staircase

Repeated equal or weaker explosions are often useless while `invulnerableTime > 10`. V2 therefore distinguishes total explosion damage from useful marginal damage.

If the current/possible `lastHurt` threshold is `H` and an incoming explosion is `D`, then during the protected portion of the window:

- `D <= H`: reject/no useful health damage from that event;
- `D > H`: only the progressive delta above the threshold proceeds through the relevant path, and the threshold advances.

When the exact remote threshold is unknown, V2 evaluates a threshold interval/distribution rather than fabricating a value.

Fast-lane staircase ranking should prefer the strongest legal useful marginal hit per expected real time, not simply raw damage or nominal CPS.

A high-damage action with a long hard server-feedback boundary may lose to a slightly lower-damage zero-feedback action when the latter has materially better expected lethal time or totem-refill denial probability.

## 10. Timing Engine V2

### 10.1 Replace generic timing with typed transitions

The current generic block-ack median/jitter heuristic is insufficient for lethal sequencing. V2 tracks distinct rolling distributions for observable transitions such as:

- block interaction sent -> block ack/state confirmation;
- crystal place sent -> matching crystal spawn observed;
- crystal attack sent -> crystal removal/explosion observation;
- totem pop observed -> visible equipment/refill change;
- relevant server update -> next relevant server update.

Each distribution should expose at least sample count, freshness, p50, p90, jitter/dispersion, and confidence.

### 10.2 Sequence-aware timing

Timing is evaluated for the actual action/dependency graph.

Examples:

- known crystal -> charged anchor can be a zero-hard-feedback burst if both actions are already legal and known;
- place crystal -> attack new crystal contains a hard `place -> server-spawn/entity-id` boundary;
- break known crystal -> place replacement may be dispatched together, but the next break still waits for the real spawn.

The engine should estimate:

- expected completion time;
- pessimistic/p90 completion time;
- number and type of hard feedback boundaries;
- probability that a refill/defensive response can occur before the next lethal event;
- expected useful damage per millisecond for nonlethal pressure choices.

It must not claim that two packets sent in one client tick are guaranteed to execute in one server tick.

## 11. StrategicScanner

The strategic scanner prepares opportunities but does not own immediate execution.

It should maintain, at minimum:

- best existing crystal break;
- best immediate crystal placement;
- best recycle base;
- best charged/prepared anchor action;
- best totem-pop finisher;
- best hurt-window staircase continuation;
- safe fallback pressure;
- preparation opportunities.

The scanner may use simplified bounded search, the existing beam planner in a demoted role, or a cheaper Future-style max-damage selector depending on the task. Immediate break/place ranking should strongly favor cheap max-useful-damage decisions rather than expensive multi-step search.

A deep planner is allowed to improve preparation but cannot block an already-approved strong immediate action merely because a better theoretical line may exist later.

## 12. ActionArbiter

Every reactive or strategic proposal passes a final cheap arbiter before dispatch.

Checks include:

- approval revision still current;
- target still valid;
- action still legal and within reach;
- required entity ID still known/live;
- required block state still plausible/current;
- required hand/hotbar stack actually present;
- rotation requirement satisfied or can be satisfied under configured mode;
- self-risk within configured strategy;
- duplicate token not already sent;
- no higher-priority emergency inventory reservation conflicts.

The arbiter does not rescore the battlefield or run a planner.

## 13. Inventory and rotations

V2 preserves truthful hotbar/inventory handling and vanilla interaction paths.

- No hidden inventory knowledge.
- No offhand theft from future emergency AutoTotem ownership.
- Restocking is kept out of timing-critical committed/reactive bursts where possible.
- Main-hand item requirements are explicit.
- Rotation modes remain real/visible: `ADAPTIVE`, `INSTANT`, `SMOOTH`.
- `Lethal Speed + ADAPTIVE` may instantly perform the real rotation for a high-confidence lethal/critical reactive action, while ordinary setup may rotate smoothly.
- No silent/server-only rotation state.

## 14. Configuration and Mod Menu

### 14.1 Main user surface

Mod Menu becomes the primary config entry point. The O key remains a quick enable/disable toggle.

Normal settings:

- Enabled
- Strategy: `LETHAL_SPEED`, `AGGRESSIVE`, `SAFE`
- Target Range
- Min Damage
- Max Self Damage
- Face Place HP
- Crystals On/Off
- Anchors On/Off
- Auto Restock On/Off
- Rotation: Adaptive / Instant / Smooth
- HUD On/Off

Avoid exposing internal search widths, confidence formulas, packet penalties, or dozens of scheduler constants as normal settings.

### 14.2 Lethal Speed semantics

`LETHAL_SPEED` means:

- immediate useful damage has priority over global theoretical optimality;
- reactive lane has no artificial stopwatch-based CPS cap;
- spawn -> break, break -> replace, and pop -> finisher are event-driven;
- high-confidence lethal/self-survival checks remain enforced;
- false exact claims from terrain/state uncertainty remain forbidden;
- hard feedback boundaries are explicitly considered in lethal-time scoring;
- deeper strategic planning cannot stall a strong immediate approved hit solely to wait for a prettier sequence.

### 14.3 Advanced/developer diagnostics

Mostly read-only:

Timing:
- place->spawn p50/p90/confidence;
- break->observation p50/p90;
- block ack p50/p90;
- estimated server cadence/jitter.

Damage:
- lower/expected/upper target damage;
- self worst-case;
- exposure;
- uncertainty reasons;
- predicted vs observed mismatch classification;
- geometry revision.

Execution:
- reactive state;
- approved break/place/recycle/finisher;
- last event->decision time;
- last decision->packet time;
- duplicate guard status;
- last abort/reconcile reason.

Strategic:
- last scan duration;
- candidate counts;
- current prepared opportunities.

### 14.4 Config ownership

Use a central `OptimizerConfig` value/snapshot. UI edits a copy and atomically applies/saves the validated result. Subsystems consume config snapshots; they must not own UI widgets or independent mutable copies of the same setting.

## 15. Diagnostics and performance metrics

The most important new metric is `TIME_TO_DAMAGE`.

For traceable actions record:

```text
event observed
-> reactive decision complete
-> packet/interact dispatch
-> server-visible result observed
```

Useful metrics include:

- crystal spawn -> attack dispatch;
- crystal removal -> replacement place dispatch;
- totem pop -> finisher dispatch;
- place -> server crystal spawn;
- attack -> server removal/explosion;
- predicted vs observed damage compatibility;
- stale-approval rejection count;
- duplicate suppression count.

Diagnostics must be cached/read-only at render time; HUD rendering must not perform world scans or planner work.

## 16. V1 migration

### 16.1 Keep/reuse

- damage mechanics and mitigation pipeline;
- hurt-window processor/model;
- armor durability behavior;
- totem transition model;
- modern crystal/anchor legality rules;
- exposure implementation as simulation/test reference;
- packet observation infrastructure;
- opponent intelligence evidence model;
- target movement prediction math where useful;
- vanilla interaction dispatcher concepts;
- real rotation controller/math;
- inventory truthfulness/restocking primitives;
- reconciliation primitives;
- packet dependency classification;
- useful unit tests and GameTests.

### 16.2 Replace or heavily rewrite

- `ClientCombatRuntime` as the orchestration god class;
- beam planner as primary owner of combat execution;
- current generic `sameTickProbability` timing heuristic as the main timing decision;
- full combat snapshot rebuild on every reactive action;
- planner-owned commit/execution flow for hot actions;
- scattered hardcoded runtime tuning constants;
- target/planner/execution coupling.

### 16.3 Demote, do not necessarily delete

The existing beam planner may survive behind `StrategicScanner` for setup/preparation or comparison experiments. It must not sit on the critical spawn/pop/recycle execution path.

## 17. Testing strategy

### 17.1 Damage differential/GameTests

Compare simulated predictions against real vanilla outcomes for:

- no armor / armor / toughness;
- armor break on the same accepted hit;
- Protection and Blast Protection;
- Resistance;
- absorption;
- shields/blocking geometry;
- Easy/Normal/Hard difficulty;
- crystal and anchor explosions;
- full and partial exposure;
- slabs/stairs/corners/holes;
- target bounding-box edge positions;
- active protected hurt windows;
- stronger follow-up progressive damage;
- totem pop then follow-up;
- terrain-independent and terrain-uncertain sequences.

Acceptance rule: with exact observable inputs, predicted result matches vanilla within appropriate floating-point tolerance. Where client state is inherently incomplete, the real result must fall inside the documented estimate range for attributable events.

### 17.2 Reactive latency tests

Direct event replays verify:

- approved crystal spawn -> break without strategic scan;
- known break -> replacement place without strategic scan;
- pop -> prepared finisher preempts recycle;
- stale approval -> no dispatch;
- duplicate event -> no duplicate action;
- missing hand item -> no illegal dispatch;
- target invalidation -> immediate stop.

The hot path receives a strict CPU budget and should consist primarily of constant/small bounded work. Exact thresholds should be benchmarked on CI/dev hardware and chosen from measured data rather than guessed in this design.

### 17.3 Recycle state-machine tests

Cover:

- successful repeated break/place/spawn cycles;
- late spawn;
- rejected placement;
- other-player interference;
- blocked base;
- target leaving useful geometry;
- stack exhaustion;
- conflicting inventory reservation;
- world revision mismatch;
- entity removed before attack dispatch.

### 17.4 Hurt-window staircase tests

Given known and unknown threshold states, verify selection uses useful marginal damage and expected lethal time.

A higher raw-damage action with a large hard feedback delay must be allowed to lose to a slightly lower zero-feedback action when the latter has better lethal-time/refill-denial expectation.

### 17.5 Timing replay tests

Recorded traces are replayed offline through the timing engine to test decisions under:

- low ping / stable cadence;
- medium ping;
- high ping;
- bursty jitter;
- degraded TPS/cadence;
- sparse/stale samples.

Unknown timing state must degrade confidence rather than fabricate certainty.

### 17.6 Regression suite

Before V2 replaces V1, run the full Java 25 unit test, build, and Minecraft GameTest suite. Preserve existing legality, inventory, simulation, reconciliation, and architecture protections unless deliberately superseded by stronger V2 tests.

## 18. Acceptance gates

V2 replaces the current runtime only when all are true:

1. Damage predictions are at least as accurate as V1; exact-observable cases match vanilla tests and uncertain live cases expose honest ranges.
2. Crystal spawn -> approved break and totem pop -> prepared finisher paths are materially faster than V1 in measured event-to-dispatch benchmarks.
3. Crystal recycle works without duplicate spam, invented IDs, stale loops, or illegal placements.
4. Timing decisions use typed event distributions and actual sequence dependencies rather than a one-size-fits-all one-action estimate.
5. `LETHAL_SPEED` never delays a strong immediate approved action solely for a theoretical future improvement.
6. Self-survival and vanilla legality checks remain enforced according to strategy.
7. Mod Menu configuration works without making Mod Menu a hard runtime requirement unless dependency constraints for the target version make that unavoidable; packaging must document the final dependency behavior.
8. Full Java 25 unit/build/GameTest verification passes.
9. Diagnostics can explain common slow/weak outcomes as timing, state, damage, geometry, rotation, inventory, server rejection, or interference rather than leaving an opaque failure.

## 19. Versioning and rollout

Target version: `0.2.0`.

Develop V2 alongside the current runtime until the acceptance gates pass. Do not delete the current V1 implementation prematurely. Once V2 is verified, switch the client bootstrap to V2 and remove or isolate superseded V1 orchestration in a dedicated cleanup change.

The existing `work/crystal-anchor-combat-optimizer-26-1-2` branch is not to be deleted as part of this redesign.

## 20. Final invariant

All optimization is subordinate to this invariant:

> Be faster by doing less work between real observable state and a legal vanilla action, not by inventing state the client does not know.

V2 should feel like an extremely aggressive Crystal Aura first and a research planner second: Future-style immediacy, modern 26.1.2 mechanics, explicit uncertainty, and event-driven lethal execution.