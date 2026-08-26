# Predictive Survival 26.1.2 — Instant-Burst and Vanilla-Damage Hardening Design

Date: 2026-08-25
Status: Approved architecture; implementation not started
Target: Minecraft Java 26.1.2, Fabric client-side
Baseline: canonical `project/predictive-survival-26-1-2` project tree
Implementation branch: `fix/predictive-survival-instant-burst-26-1-2`

## 1. Problem

Predictive Survival is strongest when a damaging event already exists and exposes enough lead time to finish a server-valid rescue action. That model fails for a different class of lethal events: the final damage can be created or triggered on the server within the same processing window as the last client observation that still looked safe.

Examples include an End Crystal that is placed and immediately destroyed, a charged Respawn Anchor or explosive Bed that is interacted with, a TNT minecart that explodes from collision/fall/fire-arrow paths without a useful visible fuse, and a player who enters lethal melee/mace/spear range before a death-protection transaction can become authoritative. Treating these as ordinary `impact == 0` threats after their final trigger is observed is only best effort; by then the hostile packet may already have reached the server first.

The fix must therefore predict not only damaging events, but also *lethal opportunities*: observable world/player states from which vanilla permits lethal damage to be created before a new rescue action can safely become server-authoritative.

This is a general timing/authority problem, not an End Crystal or Respawn Anchor special case.

## 2. Design standard

The project will use three certainty classes and never invent precision:

1. **Exact observable state** — reproduce vanilla 26.1.2 behavior exactly from synchronized/client-observable inputs.
2. **Bounded observable uncertainty** — compute an interval that contains every vanilla outcome consistent with network age, tick scheduling, motion, or synchronized-but-aged state.
3. **Server-hidden state** — retain explicit uncertainty and apply policy; never silently read a client default field as if it were authoritative server NBT.

“Perfect” means source-faithful where the client has sufficient information, conservative where the information is bounded, and explicit/fail-closed where vanilla does not synchronize the information required to know an exact result.

## 3. Goals

- Protect before first-observable-tick and near-instant lethal events when a source-confirmed precursor is visible.
- Generalize beyond crystals/anchors to every currently supported damage family that can beat the current reaction model.
- Keep ordinary fused/projectile prediction accurate instead of replacing it with crude distance thresholds.
- Make explosion exposure and post-mitigation damage match vanilla 26.1.2 as closely as the client-observable world snapshot permits.
- Keep protection server-authoritative through the entire danger interval; prevent restoration/swap oscillation.
- Preserve existing working behavior and avoid a rewrite of unrelated planners/executors.
- Keep the per-tick cost bounded through broad-phase filtering, caching, dirty-state invalidation, and narrow-phase exact simulation.
- Prove behavior with regression-first unit tests and exact-runtime Minecraft GameTests that reproduce hostile timing sequences rather than cooperative delayed detonations.

## 4. Non-goals

- Client-only invulnerability, packet desync survival, impossible movement, or any protection that the vanilla server does not actually recognize.
- Claiming exact knowledge of custom server NBT that vanilla does not synchronize.
- Holding a totem merely because an arbitrary player or explosive-capable item exists somewhere nearby.
- Replacing all existing threat predictors with one monolithic predictor.
- Broad unrelated refactors.

## 5. Source-confirmed baseline facts

The design is based on the supplied/decompiled Minecraft 26.1.2 source and the canonical Predictive Survival baseline.

### 5.1 Immediate explosion triggers

- `EndCrystal.hurtServer` removes the crystal and invokes a radius-6 explosion immediately when the damage source is not itself an explosion.
- `RespawnAnchorBlock.useWithoutItem` immediately explodes a charged anchor in dimensions/positions where anchors cannot set spawn.
- `BedBlock.useWithoutItem` removes the bed and immediately invokes a radius-5 bad-respawn-point explosion when the active `BedRule` says beds explode.
- `MinecartTNT` can explode on a horizontal collision at sufficient speed, from a burning-arrow hit, and from sufficient fall distance, in addition to its primed-fuse path.

### 5.2 Fused/aged explosion state

- `PrimedTnt.DATA_FUSE_ID` is synchronized, but `explosionPower` is ordinary server-side saved state and is not synchronized; a remote client field value must not be treated as authoritative custom power.
- Creeper swell direction, powered state, and ignited flag are synchronized, while `maxSwell` and `explosionRadius` are saved server-side fields. Client-side swelling advances from synchronized intent and can be observation-aged relative to the server.
- TNT minecart fuse/power fields are ordinary entity fields/NBT, with priming communicated by entity event; exact custom server values are not generally synchronized.
- Wither spawn invulnerability ticks are synchronized and the server creates a radius-7 explosion at eye Y when the countdown reaches zero.

### 5.3 Vanilla living damage order

For player damage, vanilla 26.1.2 performs difficulty scaling before the `LivingEntity.hurtServer` pipeline. The relevant living-damage order is:

1. invulnerability/dead/fire-resistance rejection;
2. clamp negative damage;
3. item blocking;
4. freezing multiplier;
5. helmet damage/reduction;
6. NaN/infinity sanitization;
7. hurt-cooldown comparison/delta handling;
8. armor durability + armor/toughness/armor-effectiveness calculation;
9. Resistance and enchantment protection;
10. absorption;
11. health damage;
12. death-protection consumption unless the damage bypasses invulnerability.

The current `DamageSimulator` already follows this ordering closely and remains the base implementation. Hardening will be differential, not a rewrite.

### 5.4 Vanilla explosion exposure

`ServerExplosion.getSeenPercent` samples the target AABB with vanilla’s exact grid spacing and ray-clips every sample to the explosion center using `Block.COLLIDER`, `Fluid.NONE`. The current Predictive Survival sampling formula matches this structure, but the captured collision geometry collapses each block `VoxelShape` to one AABB envelope and the current explosion occlusion path primarily trusts full collision cubes. Compound/partial shapes can therefore disagree with vanilla.

## 6. Architecture

### 6.1 Existing Actual Threat Timeline

Keep existing predictors responsible for events that already exist: projectiles, current explosions/fuses, environmental hazards, status effects, falls, current melee states, and other present threats.

This layer answers:

> Given what currently exists, what damage events can occur and when?

It must not become responsible for hypothetical attacker setup actions.

### 6.2 New Lethal Opportunity Engine

Add a separate subsystem that creates `LethalOpportunity` records from observable precursor state.

A `LethalOpportunity` contains at minimum:

- stable ID/family;
- source/attacker identity when known;
- earliest and latest server tick at which the damaging event can legally become accepted;
- candidate resulting `ThreatEvent`/damage envelope;
- certainty class;
- prerequisite/action depth;
- evidence used to establish legality;
- invalidation keys for caching;
- whether the opportunity is adversarial, deterministic, or environmental.

This layer answers:

> From the state visible now, can vanilla create lethal damage before a protection action sent now can safely become server-authoritative?

Opportunity prediction must remain distinct from actual-event prediction so “could be created” does not get merged into “already exists.”

### 6.3 Authority Deadline Model

Use `ServerTimingEstimator`/`TimingSnapshot` to compare hostile-event acceptance against rescue-action authority.

For any observation captured at client tick `C`, derive conservative server windows for:

- age of the observed remote state;
- next hostile action processing;
- earliest rescue packet processing;
- multi-packet inventory route completion;
- confirmation/authority when the executor requires acknowledgement or server evidence.

The key predicate is not `distance < N` or `impact <= N`; it is:

```text
earliest legal lethal server event
    <=
latest safe rescue-authority completion
```

If true and the resulting damage can kill the player without death protection, the protection latch must arm before the final damaging entity/event exists.

### 6.4 Vanilla Damage Oracle

Create a narrow interface over the existing damage/explosion simulation so actual threats and opportunities share exactly the same damage semantics.

Responsibilities:

- raw source-specific damage/envelopes;
- difficulty scaling;
- blocking semantics and disable consequences;
- hurt cooldown/`lastHurt` semantics;
- armor/toughness and armor-effectiveness modification;
- Resistance and enchantment protection;
- absorption;
- death protection and post-protection effects;
- source flags/tags and source positions;
- exact or bounded explosion exposure.

The oracle must never have a separate “opportunity damage approximation.” A hypothetical crystal must be simulated by the same explosion path as an already-existing crystal.

### 6.5 Protection Safety Latch

Add a persistent danger lease around death protection.

Arm when either:

- an actual timeline is lethal before the current route can safely complete; or
- a lethal opportunity can create lethal damage before a newly initiated route can safely complete.

While armed:

- death protection must remain in a server-recognized hand;
- restoration controllers may not replace it;
- lower-priority non-totem actions may not transiently remove it unless the planner proves the replacement route remains protected;
- equivalent repeated threats refresh the latch instead of creating swap churn.

Disarm only after:

- no qualifying actual threat remains;
- no qualifying lethal opportunity remains; and
- the state has remained safe through a conservative server-processing/jitter grace window.

This hysteresis prevents `safe → danger → safe` observation noise from making the hand oscillate.

## 7. Opportunity families

The engine is family-based and extensible; it is not a hardcoded list inside `ExplosionPredictor`.

### 7.1 Triggerable explosive opportunities

#### Existing End Crystal

An existing damageable End Crystal that an adversary can legally attack is an immediate trigger opportunity. Existing crystal prediction remains an actual threat; the opportunity layer is used when server ordering means reacting after the entity/detonation observation is insufficient.

#### End Crystal placement → detonation

Before a crystal exists:

1. broad-phase nearby obsidian/bedrock support blocks in the attacker’s actionable region;
2. verify vanilla placement volume/space rules from 26.1.2;
3. verify the attacker can legally interact with the support within conservative block interaction reach;
4. construct the exact crystal explosion center/radius;
5. run the shared explosion/damage oracle;
6. if lethal and the attacker can create/detonate it before rescue authority, arm protection.

Visible held crystal is strong evidence but not a mandatory safety requirement in strict protection logic when an inventory hotbar swap can fit inside the same server window. Balanced policy may weight item evidence without compromising a source-confirmed imminent existing-crystal trigger.

#### Explosive Bed

Handle both an already-placed explosive bed and a legal bed placement followed by interaction. Use the environment `BedRule`, exact bed placement orientation/collision legality, interaction reach, and the radius-5 bad-respawn-point damage source. The bed’s two blocks must be removed from the occlusion snapshot before its own explosion exposure is evaluated, matching vanilla ordering.

#### Respawn Anchor

Represent anchor action depth explicitly:

- charged explosive anchor: one interaction from explosion;
- uncharged explosive anchor: charge then use;
- no anchor placed: placement/setup path only when all required actions are observable/legal within the safety horizon.

Use `RESPAWN_ANCHOR_WORKS`, actual block charge, interaction reach, and radius-5 bad-respawn-point damage. The anchor block is removed before explosion exposure; neighboring/above water handling must mirror the custom anchor explosion damage calculator.

### 7.2 TNT minecart burst opportunities

Cover all source-confirmed paths independently:

- primed fuse;
- high-speed horizontal collision;
- burning-arrow hit;
- fall-distance explosion;
- destruction/ignition paths that convert to a fuse.

Do not require `isPrimed()` before the minecart is considered relevant. For random/custom power that is genuinely unsynchronized, carry explicit uncertainty instead of presenting a client-side default as exact.

### 7.3 Deterministic countdown explosions

These remain actual threats but use authority-aware deadlines:

- Primed TNT;
- Creeper swelling/ignition;
- Wither spawn explosion;
- other source-confirmed deterministic countdowns discovered during the implementation audit.

A client-observed countdown is converted to a server-relative impact window by subtracting conservative observation age. The planner protects against the earliest legal server detonation, not the prettiest local animation tick.

### 7.4 Melee / mace / spear opportunities

Current-range detection is insufficient when an attacker can enter legal attack range before the rescue route becomes authoritative.

Use relative motion and source-confirmed attack reach to derive earliest legal hit time. Reuse existing weapon snapshots and exact damage modeling for ordinary melee, criticals, mace smash, spear behavior, enchantment effects, blocking disable, and causal follow-ups.

The opportunity layer predicts *when a legal hit can be produced*; `MeleePredictor`/damage logic still determines the resulting damage envelope.

### 7.5 Projectile release/point-blank opportunities

During implementation audit, identify currently supported projectile families whose first client-visible projectile entity can appear so close that impact may beat the rescue authority window. For those families, add a precursor only where vanilla exposes enough attacker wind-up/charged-item state to establish a legal launch window. Do not fabricate a launch opportunity from an arbitrary held item when the server-required state is not observable.

Already-existing projectiles remain handled by the normal projectile predictor with observation age included in impact bounds.

### 7.6 Causal follow-ups

Effects that occur only after an accepted hit remain causally linked to that hit. Examples include source-specific enchantment or status follow-ups. The opportunity engine must preserve `requiresAcceptedEventId`/equivalent causality so rejected or blocked prerequisite hits do not create impossible downstream damage.

## 8. Explosion simulation hardening

### 8.1 Exact collision-shape snapshot

Replace the single-envelope `VoxelShape` serialization used for explosion ray occlusion with component geometry sufficient to reproduce `Block.COLLIDER` ray clipping.

For each relevant nearby block, capture the local AABB components of `state.getCollisionShape(level, pos)` (or an equivalent compact representation). Preserve the full-cube fast path.

Do not treat the bounding envelope of disjoint shapes as solid; that over-blocks rays through gaps.

### 8.2 Vanilla sample points and endpoint rules

Keep the current sample-grid math because it mirrors `ServerExplosion.getSeenPercent`, but differential-test floating-point loop/sample behavior against the real runtime. Ray clipping must reproduce vanilla collider behavior for partial/compound shapes, including source blocks removed before bed/anchor explosions.

### 8.3 Explosion center

Use source-accurate centers:

- normal entity/block centers as defined by source mechanics;
- Primed TNT explosion Y uses `getY(0.0625)`;
- Wither spawn uses eye Y;
- bad-respawn explosions use block center;
- End Crystal uses entity position.

### 8.4 Hidden explosion power

Do not access unsynchronized fields through mixins and call them authoritative. Classify source power by observability:

- fixed vanilla constant: exact;
- synchronized: exact/bounded only by observation age when applicable;
- unsynchronized but constrained by vanilla persistence/API: explicit bounded/unknown state;
- server/plugin behavior outside vanilla-observable contracts: policy-level unknown.

Balanced mode must not automatically turn every ordinary TNT/creeper into absurd worst-case custom-NBT damage unless there is evidence for modified values; strict mode may fail closed more aggressively. Both modes must expose why certainty was reduced.

## 9. Performance design

Opportunity scanning must be substantially cheaper than full damage simulation.

### 9.1 Broad phase

Use spatial bounds derived from:

- attacker interaction/attack reach;
- relative motion over the authority horizon;
- maximum harmful radius of the *known vanilla family*;
- nearby block/entity indices already captured by the runtime.

### 9.2 Narrow phase

Only candidates surviving legality and coarse lethality checks receive exact collision-shape exposure + full `DamageSimulator` evaluation.

### 9.3 Cache/invalidation

Cache stable candidate geometry and invalidate from meaningful dirty state:

- nearby block update/chunk section change;
- relevant entity spawn/remove/movement/equipment/held-item data;
- local player movement, health, absorption, armor/effects/blocking/death-protection state;
- environment attribute/dimension change;
- timing estimate changes large enough to cross an authority boundary.

### 9.4 Bounded work

Every family must have explicit per-tick candidate limits and a fail-closed overflow behavior. Overflow may increase protection conservatism but may not silently discard the highest-risk opportunity and declare the player safe.

## 10. Planner/executor integration

Do not create a second rescue planner.

The existing candidate/planner/executor stack remains responsible for choosing and executing server-valid actions. The new opportunity result feeds an earlier deadline and a protection latch requirement into that same planning path.

Required invariants:

- Existing server-authority tracking remains the source of truth for whether a hand/inventory action is actually established.
- A `BEST_EFFORT` action is acceptable only when no earlier observable precursor existed; if a precursor existed but the engine failed to arm, the regression remains unfixed.
- Restoration can never outrank an active protection latch.
- Route changes must preserve item/component fingerprints and current container authority semantics.
- If a totem is already authoritative, do not churn it merely because a different equivalent opportunity becomes the highest-risk reason.

## 11. Testing strategy

Implementation is regression-first.

### 11.1 Unit REDs before production changes

At minimum:

- legal crystal support + attacker reach generates lethal opportunity before crystal entity exists;
- illegal/occluded/out-of-reach crystal placement does not;
- existing charged anchor and charge→use anchor have different action depths;
- explosive bed placement/use follows the environment rule and removes bed blocks before exposure;
- unprimed TNT minecart collision/fall/fire-arrow paths are represented;
- network-aged fused TNT can have an earlier server detonation than the local displayed fuse;
- ignited creeper is captured even before ordinary local swelling assumptions provide a safe deadline;
- approaching lethal mace/spear/melee creates an opportunity before current reach;
- safe/non-lethal opportunities do not latch a totem;
- latch hysteresis prevents restore/re-equip oscillation;
- overflow fails closed.

### 11.2 Damage differential tests

Compare predicted post-damage state with real Minecraft 26.1.2 for matrices covering:

- armor/toughness;
- Protection/Blast Protection and source flags;
- Resistance;
- absorption;
- blocking profiles and source angles;
- hurt cooldown / prior `lastHurt`;
- death protection;
- explosion distance and exposure;
- partial and compound collision shapes (slabs, stairs, fences/walls, trapdoors and representative unusual shapes);
- bed/anchor source-block removal ordering;
- source-accurate explosion centers.

### 11.3 Exact-runtime sequence tests

The decisive regressions are hostile sequences, for example:

```text
no crystal exists
→ lethal legal crystal opportunity is observed
→ protection becomes server-authoritative
→ hostile placement is processed
→ immediate hostile crystal destruction is processed
→ explosion occurs
→ player survives / death protection is consumed as vanilla dictates
```

Equivalent sequences are required for explosive bed, charged anchor, TNT-minecart instant paths, approaching lethal melee/mace/spear, and network-aged fused explosions.

Tests must deliberately remove artificial delays between hostile setup and final trigger. Cooperative “spawn threat, wait several ticks, detonate” coverage is insufficient.

### 11.4 False-positive tests

Protection must not latch for:

- crystal support that cannot be legally used;
- non-explosive bed/anchor dimensions;
- non-lethal explosion after exact mitigation/exposure;
- attacker outside conservative actionable window;
- blocked/invalid causal precursor;
- stale opportunity whose required block/entity state has disappeared.

## 12. Verification gate

Before claiming completion:

1. Java 25 `clean test build` passes.
2. client GameTest compilation passes.
3. exact Minecraft 26.1.2 runtime GameTests pass.
4. existing Predictive Survival regression suite passes unchanged unless a source-confirmed expectation is intentionally corrected.
5. production JAR contains no validation/GameTest classes.
6. CI artifacts are produced and inspected.
7. representative performance capture shows no unbounded candidate explosion or severe tick/FPS regression.
8. a final source audit checks every currently supported damage family for the same “final trigger faster than rescue authority” failure mode and either adds a precursor model or documents why no observable precursor exists.

## 13. Implementation boundaries

Expected production areas once the project lease is available:

- `core`: richer observable snapshots, exact collision-shape components, timing evidence;
- `threat`: new lethal-opportunity types/registry/family evaluators and targeted existing predictor fixes;
- `damage`: differential correctness fixes only where the source/runtime tests reveal mismatches;
- `planner`: authority deadline + latch integration;
- `execution`: latch-aware restoration/authority preservation only where required;
- `test` and `gametest`: regression and differential coverage.

Avoid placing all precursor logic inside `ExplosionPredictor` or `MeleePredictor`. Family evaluators should have narrow, testable responsibilities.

## 14. Concurrency / branch safety

The new implementation branch is intentionally based directly on canonical `project/predictive-survival-26-1-2`, which matches the supplied known-good baseline in the audited critical prediction/damage/timing files.

At design time, task generation 5 has an active lease on `projects/predictive-survival-26-1-2` owned by the existing burst-guard worker. This design document is therefore committed outside that leased project path. Production project files must not be edited on this branch until the active lease is released/expired and coordination state is refreshed/claimed according to the repository protocol.

## 15. Acceptance criteria

The hardening is complete only when all of the following are true:

- a lethal End Crystal placement/detonation sequence can cause protection to be authoritative before the crystal entity exists;
- anchors and beds use the same generalized opportunity machinery rather than one-off timing hacks;
- all source-confirmed instant TNT-minecart paths are covered;
- fused threats use server-relative bounded deadlines rather than blindly trusting a local countdown;
- approaching lethal melee/mace/spear can pre-arm protection before current attack range when required by authority timing;
- explosion exposure is differentially validated against vanilla partial/compound collision geometry;
- the damage simulator matches source-confirmed vanilla ordering and passes runtime differential matrices;
- protection remains latched across the complete danger window and restores safely afterward;
- false positives are bounded by legal-action and lethal-damage checks rather than crude proximity;
- hidden server state is represented honestly instead of masquerading as exact client knowledge;
- the complete existing suite plus new exact-runtime hostile-sequence regressions is green;
- performance remains bounded and usable.
