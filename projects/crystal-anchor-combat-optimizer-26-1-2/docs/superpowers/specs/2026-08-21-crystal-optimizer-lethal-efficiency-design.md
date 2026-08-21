# Crystal Anchor Combat Optimizer V2 — Lethal-Efficiency Redesign

Date: 2026-08-21
Status: approved for direct implementation
Branch: `feat/crystal-optimizer-v2-lethal-efficiency`
Base: quad-checked V2 head `da79f1bd861c4f37ecdffab0aaecc0f2492e7223`

## Goal

Preserve the parts of V2 that are already strong — real server entity-ID crystal recycling, fast break→replace chains, pop finishers, smart reuse of existing obsidian, and event-driven execution — while making the optimizer much more selective about what is allowed into the fast lane.

The optimizer must stop behaving like “if an explosion is possible, spend a resource.” It should behave like:

> Is this move safe for the local player, is the complete resource chain actually executable, and does it materially reduce expected time-to-kill? If yes, execute immediately. Otherwise wait for a better move.

Future 2.9 AutoCrystal is used only as a behavioral reference. Its useful ideas are the target-damage/self-damage trade check and the hard local-survival check. No Future implementation code is copied.

## Non-negotiable invariants

1. **Never intentionally kill the local player.**
   - Every explosion action must pass a current-state local survival simulation immediately before approval and again at arbitration time.
   - Use pessimistic/worst-case local damage, not expected damage.
   - A target kill does not override local survival.

2. **Do not intentionally consume the local player’s totem.**
   - If the modeled action would require the local totem to activate, reject it by default.
   - This is stricter than merely checking `maxSelfDamage`.

3. **Do not make losing trades for ordinary pressure.**
   - For nonlethal/non-pop/non-required-staircase hits, useful target damage must be meaningfully greater than worst-case self damage.
   - A default trade ratio of target useful damage / self damage >= 1.25 is used when self damage is nonzero.
   - Certified lethal, certified totem-pop, and required hurt-window staircase actions may bypass the ratio, but never the local-survival/no-self-pop rules.

4. **Never spend a setup resource without a viable complete chain.**
   - `PlaceAnchor` requires a known path to `ChargeAnchor` and `DetonateAnchor` using currently available/reservable glowstone and a detonating hand/item state.
   - `PlaceObsidian` for crystal setup requires a known path to `PlaceCrystal` with a currently available/reservable crystal and a projected explosion that passes the same admission policy.
   - Selection actions are part of the chain and must be modeled/reserved rather than treated as free magic.

5. **No impossible water crystal attempts.**
   - Minecraft 26.1.2 `EndCrystalItem` requires the block directly above obsidian/bedrock to be empty. A crystal cannot be placed directly inside water.
   - Water itself is replaceable for normal block placement in 26.1.2. Obsidian/respawn-anchor support placement into a water block is valid when vanilla placement support/reach/entity-space rules permit it.
   - V2 must therefore support underwater/water replacement setup, then place a crystal only after its above-base space is actually empty.

## Architecture

Keep the existing split:

- `ClientDamageMapBuilder` — produces bounded target-local opportunities.
- `FastOpportunitySelector` — ranks admitted opportunities by kill pressure and real completion time.
- `StrategicPreparationPlanner` — produces setup chains only when the chain can terminate in an admitted damaging action.
- `ReactiveCombatEngine` — remains tiny and event-driven.
- `ActionArbiter` — final live-state safety/legality/resource gate.
- `PendingItemLedger` — extended from per-action reservation to whole-chain demand.

Add three explicit policy/value components:

### 1. `SelfSurvivalPolicy`

Inputs:
- current local combatant state from the snapshot/live view;
- modeled local damage result/range;
- configured max self damage;
- whether the action would consume a local totem.

Output:
- admitted/rejected;
- worst-case post-hit effective health;
- reason (`SELF_LETHAL`, `SELF_TOTEM_POP`, `SELF_DAMAGE_LIMIT`, `BAD_TRADE`).

Rules:
- reject if worst-case post-hit health <= 0.5 HP;
- reject if the simulator predicts local totem activation;
- reject if worst-case self damage exceeds `maxSelfDamage`;
- ordinary pressure additionally uses the target/self trade ratio.

This policy is evaluated first while building opportunities and again from live state in the arbiter. The second check prevents a previously safe approval from becoming suicidal after the player takes damage between scan and dispatch.

### 2. `ResourceChain`

Each opportunity declares its whole consumable demand, for example:

- direct crystal break: `{}`
- crystal placement: `{END_CRYSTAL: 1}`
- obsidian setup→crystal: `{OBSIDIAN: 1, END_CRYSTAL: 1}`
- anchor setup→detonate: `{RESPAWN_ANCHOR: 1, GLOWSTONE: 1}`

The chain also carries the action sequence needed to reach damage. The pending ledger checks/reserves the entire demand atomically before the first action is dispatched. If the chain cannot be completed with observed inventory minus pending reservations, it is not published.

### 3. `LethalEfficiencyPolicy`

Admission happens before ranking.

An opportunity is admitted only if it is one of:

1. certified lethal;
2. certified/very-high-confidence totem pop;
3. useful hurt-window staircase progress;
4. immediately valuable direct damage above the dynamic spend floor;
5. setup whose modeled follow-up satisfies one of 1–4.

The dynamic spend floor prevents distant chip spam:

- normal pressure: useful expected target damage >= `max(config.minDamage, 6.0)`;
- face-place target (`target effective health <= facePlaceHealth`): floor may drop to 2.0 if the hit is safe and increases pop/kill pressure;
- direct existing-crystal attacks may use a lower 1.0 useful-damage floor because no crystal item is consumed by the break itself;
- zero-useful-damage actions are never admitted except a prerequisite inside a complete chain whose terminal damage is admitted.

These floors are semantic defaults, not arbitrary correction multipliers; the underlying Minecraft damage value remains unchanged.

## Ranking and kill pressure

After admission, preserve V2’s “useful damage per real server time” idea but make the priority classes stricter:

1. certified lethal with local survival;
2. immediate/high-confidence totem pop with a prepared safe finisher;
3. safe prepared finisher after observed pop;
4. useful hurt-window staircase hit;
5. highest useful-damage/time direct/recycle opportunity;
6. viable preparation chain ranked by terminal damage/time/resource cost.

Tie-breakers:
- higher useful lower-bound damage;
- fewer hard feedback boundaries;
- lower p90 completion time;
- lower resource cost;
- lower self damage;
- reuse an existing valid base/anchor before creating new support when damage/time is comparable.

A move that is only slightly stronger but consumes extra obsidian/anchor/glowstone and waits on more feedback should lose to a nearly-as-damaging immediate move.

## Resource economy

Resource cost is not allowed to override a certified kill, but it matters strongly for nonlethal pressure.

Suggested normalized costs for ranking only:
- attack existing crystal: 0.0
- use existing obsidian + crystal: 1.0
- place obsidian + crystal: 2.0
- detonate existing charged anchor: 0.25
- charge existing anchor + detonate: 1.25
- place anchor + glowstone + detonate: 2.5

These are ordering weights only; they do not alter damage calculations.

The scanner must not repeatedly spend support on multiple equivalent bases. Preparation gets a short per-target/per-geometry suppression key so the same low-value setup is not reissued while the previous predicted placement is unresolved.

## Water-aware placement

Current bug: `PlaceObsidian.check()` requires `state.geometry().getBlockState(pos).isAir()`, which rejects vanilla-replaceable water.

Replace this with a conservative vanilla-replaceability predicate:

- air: allowed;
- water/liquid block marked replaceable: allowed;
- other blocks only if the actual 26.1.2 block state is replaceable for the intended block-place context;
- entity collision, interaction reach, and adjacent placement support remain mandatory.

`PlaceAnchor` receives the same replaceability rule.

`PlaceCrystal` remains strict: obsidian/bedrock base, direct-above block empty, and no entity in the modern 1×2 AABB. Water directly above the base is not empty and remains rejected.

Water setup tests must include:
- obsidian replaces a water source block when supported;
- anchor replaces a water source block when supported;
- crystal is rejected while water remains directly above the base;
- after replacing/lowering the water such that above-base is empty, crystal placement becomes legal;
- no attempt is made to place a crystal directly into water.

## Self-safety data path

`ClientDamageMapBuilder` already calculates target damage and local damage from the same vanilla simulator. Extend it to keep a structured local damage result instead of flattening it to one float.

`DamageOpportunity` gains:
- local damage lower/expected/upper or an equivalent `SelfDamageEstimate`;
- worst-case post-hit health;
- local-totem-pop flag;
- resource chain/cost.

`LiveCombatView` gains the local effective-health and local-totem-visible data required for a final fast guard. If a full live damage recomputation is too expensive for the packet hot path, the arbiter uses the approval’s pessimistic damage plus current effective health; any drop in health makes the action safer-to-reject, never safer-to-send.

## Anchor behavior

Anchors are only considered when the dimension/environment allows explosion behavior and one of these is true:

- an already charged anchor can detonate now;
- an existing anchor can be charged and then safely detonated with glowstone available;
- a new anchor can be placed, charged, and detonated with both anchor and glowstone available and the terminal explosion passes admission.

No glowstone => no new anchor placement.
No detonating hand/item route => no new anchor placement.
No worthwhile terminal damage => no new anchor placement.

## Crystal behavior

Crystals retain the current strong fast paths:

- attack known existing crystal;
- break→replace same base;
- server spawn→attack exact real entity ID→replace;
- reuse a strong existing obsidian/bedrock base;
- pop→prepared finisher preemption.

Changes:
- do not place a crystal merely because a base is legal;
- projected useful target damage must pass admission;
- ordinary nonlethal placement must pass the target/self trade rule;
- prefer the best existing base when close in lethal time to a support-consuming setup;
- do not recycle a base whose new crystal is predicted to be bad under the current target position/self state.

## Totem kill behavior

When target totem is available, score the race as time-to-pop plus time-to-finisher, not raw DPS alone.

- Prefer a high-confidence pop now over multiple smaller hits that do not reach the protected-window threshold.
- On observed pop, prepared safe finisher overrides recycle/pressure.
- If no immediate finisher is safe/viable, continue with the highest useful staircase hit rather than random placements.

## Diagnostics

Expose concise reasons in HUD/developer diagnostics:

- `SELF_LETHAL`
- `SELF_TOTEM_POP`
- `BAD_TRADE`
- `LOW_VALUE_SPEND`
- `MISSING_CHAIN_RESOURCE`
- `NO_GLOWSTONE`
- `WATER_ABOVE_CRYSTAL_BASE`
- `WAITING_SETUP_ACK`
- `STALE_DAMAGE`

Also show selected opportunity as:

`type | target useful damage | self worst | terminal time | resource chain`

This makes real-game reports actionable instead of “it did nothing” or “it spammed.”

## Tests and acceptance gates

### Unit tests

- self damage below maxSelfDamage but above current health is rejected;
- any predicted local totem activation is rejected;
- ordinary 5 target / 8 self trade is rejected;
- certified enemy lethal can bypass trade ratio but not local survival;
- anchor setup without glowstone is absent;
- obsidian setup without a crystal is absent;
- resource chain reservations are atomic;
- existing-base crystal beats nearly equal obsidian+crystal setup;
- distant low-value crystal placement is absent;
- face-place can lower the spend floor safely;
- water support placement legality matches vanilla replaceability;
- direct crystal-in-water remains illegal.

### Integration tests

- wrong-hotbar cold start still progresses through a complete viable crystal chain;
- wrong-hotbar anchor setup only starts when the full anchor+glowstone chain exists;
- strategic place→server spawn→attack exact ID→replacement still works;
- pop→finisher remains higher priority than recycling;
- health dropping after approval causes arbiter rejection before dispatch;
- repeated low-value setup is suppressed while unresolved.

### Vanilla differential/GameTests

Retain all existing damage differential tests and add local-player survival cases around:
- low health;
- absorption;
- armor break;
- Resistance;
- local totem available;
- crystal and anchor explosions.

### Performance

Do not regress the packet fast-lane budget:
- event→decision p50 <= 1 ms;
- event→decision p95 <= 2 ms;
- spawn-specific recycle remains constant-size and planner-free.

## Versioning

Bump the test build to `0.2.1` so it is visually distinguishable from the earlier 0.2.0 artifacts.

## Success criteria

A successful build should feel *less spammy but more dangerous*:

- it may wait instead of spending a crystal for trivial damage;
- it never knowingly suicides or intentionally pops the local totem;
- it does not place anchors without the glowstone/detonation chain;
- it can build support in replaceable water where vanilla permits it;
- it still executes good recycle/pop/finisher combos immediately;
- when it spends a crystal, obsidian, anchor, or glowstone, the spend has a modeled reason tied to faster lethal progress.
