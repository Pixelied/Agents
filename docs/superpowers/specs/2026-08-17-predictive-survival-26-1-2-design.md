# Minecraft 26.1.2 Predictive Survival Engine — Design

**Task:** `design-predictive-survival-26-1-2`  
**Target project:** `projects/predictive-survival-26-1-2/`  
**Target runtime:** Minecraft Java Edition 26.1.2, Fabric client mod, Java 25  
**Design status:** implementation-ready pending human review

## 1. Objective

Build a standalone client-side Fabric mod that predicts observable incoming damage before the server applies it, reproduces Minecraft 26.1.2's server damage semantics closely enough to make life-or-death decisions, and executes the safest server-valid action before the predicted impact.

The project is a **predictive survival engine**, not merely AutoTotem. Totem handling is the reliable death-protection fallback when a death-protection item is available, while the planner can also avoid, block, reduce, relocate away from, or otherwise survive lethal damage when a better action is feasible.

The default objective order is:

1. maximize probability of remaining alive through the complete near-future threat sequence;
2. prefer deterministic/proven actions over speculative tricks;
3. preserve death-protection items when an equally reliable non-consumable action exists;
4. minimize disruption to the player's movement, selected item, offhand, inventory, and ongoing actions.

The engine must never treat a client-only visual/desync state as protection. An action counts only if the server can process the required state before the predicted damage event.

## 2. Chosen approach

Use a **hybrid source-faithful simulation + threat-specific prediction + bounded action search** architecture.

Rejected alternatives:

- **Simple health/radius heuristics:** fast but too inaccurate around armor/toughness, enchantments, Resistance, absorption, hurt cooldown, shields, explosion exposure, mace damage, and multi-hit sequences.
- **Purely reactive damage/packet handling:** accurate after the fact but too late for shield warmup, inventory movement, projectile evasion, cover placement, and many lethal hits.
- **One giant tick-loop AutoTotem:** difficult to test, easy to desynchronize, and incapable of comparing multiple survival strategies coherently.

The chosen design keeps exact-ish vanilla damage math isolated from prediction uncertainty. Threat predictors emit bounded future events; the timeline simulator applies vanilla semantics; the planner compares feasible actions against the same future timeline.

## 3. Platform and build baseline

This project will live directly in the Agents repository under `projects/predictive-survival-26-1-2/`.

Start from FabricMC's official `fabric-example-mod` branch for `26.1.2`, not from an older Yarn-based template. Pin the versions currently published by that branch when implementation begins:

- Minecraft: `26.1.2`
- Java release/toolchain: `25`
- Fabric Loader: `0.19.3`
- Fabric Loom: `1.17-SNAPSHOT`
- Fabric API: `0.155.2+26.1.2`
- Loom plugin id: `net.fabricmc.fabric-loom`

Minecraft 26.1+ is unobfuscated in the relevant Fabric workflow. Do not add Yarn mappings or use the legacy remapping Loom plugin.

The mod is client-only in production. Test-only server helpers/GameTests are allowed in non-production source sets if needed to compare predicted damage against an actual 26.1.2 server.

## 4. Vanilla source is authoritative

The supplied decompiled Minecraft 26.1.2 source under `/mnt/data/mcsrc2612/src/main/java` is the behavioral reference for game mechanics. Implementation must inspect the actual source instead of copying formulas from older clients, wikis, or previous Minecraft versions.

At minimum, implementation must trace and mirror behavior from:

- `net.minecraft.world.entity.player.Player#hurtServer`
- `net.minecraft.world.entity.LivingEntity#hurtServer`
- `LivingEntity#applyItemBlocking`
- `LivingEntity#getDamageAfterArmorAbsorb`
- `LivingEntity#getDamageAfterMagicAbsorb`
- `LivingEntity#actuallyHurt`
- `LivingEntity#checkTotemDeathProtection`
- `LivingEntity#getItemBlockingWith`
- `net.minecraft.world.damagesource.CombatRules`
- `net.minecraft.data.tags.DamageTypeTagsProvider`
- `net.minecraft.world.damagesource.DamageTypes`
- `net.minecraft.world.damagesource.DamageSources`
- `net.minecraft.world.level.ServerExplosion`
- `net.minecraft.world.level.ExplosionDamageCalculator`
- `net.minecraft.world.item.component.BlocksAttacks`
- `net.minecraft.world.item.component.DeathProtection`
- projectile entity movement/collision classes for supported projectile families
- `net.minecraft.world.item.MaceItem`
- `net.minecraft.server.level.ServerPlayer` impulse/fall handling
- `ThrownEnderpearl`
- `AbstractContainerMenu`, `MultiPlayerGameMode`, and `ServerGamePacketListenerImpl` for hand/inventory actions.

Runtime registry tags/components are preferred over hard-coded membership lists. Generated vanilla tag providers are test/reference data, not a replacement for runtime tag lookups.

## 5. Core architecture

### 5.1 `SurvivalEngine`

Single orchestrator invoked from the client tick/network hooks. It owns no vanilla formulas itself. Each tick it:

1. updates server timing and synchronization estimates;
2. captures a compact `PlayerSnapshot` and relevant `WorldSnapshot`;
3. updates the shadow server hurt state;
4. asks registered threat predictors for future `ThreatEvent`s;
5. merges events into a bounded `ThreatTimeline`;
6. evaluates the no-action outcome;
7. if death/critical uncertainty is present, generates feasible `SurvivalAction`s;
8. simulates each action's resulting timeline;
9. chooses the safest action according to mode/risk policy;
10. executes or continues the selected action state machine;
11. records decision telemetry when debugging is enabled.

### 5.2 `DamageSimulator`

Pure, deterministic model of the vanilla player/living-entity damage pipeline. It receives immutable snapshots and never scans the world or sends packets.

Input includes:

- damage source/type holder and relevant tags;
- raw damage or raw-damage interval;
- source position/direct entity/causing entity/weapon when relevant;
- player health, absorption, armor, toughness, equipment and durability;
- active effects;
- enchantment protection inputs;
- blocking item/use duration/head rotation;
- difficulty/gamerule/ability state;
- estimated server `invulnerableTime` and `lastHurt` confidence.

Output includes an audit trail of each stage, resulting health/absorption, updated hurt state, whether damage was blocked/rejected, whether death protection is required/possible, and resulting post-pop state.

### 5.3 `ServerHurtStateTracker`

Maintains a conservative estimate of the server's `invulnerableTime` and raw pre-armor `lastHurt`.

This must not blindly read `LocalPlayer.lastHurt`. In 26.1.2, `LocalPlayer#hurtTo` sets local `lastHurt` from the **actual health delta**, while server `LivingEntity#hurtServer` stores `lastHurt` after blocking/freezing/helmet handling but before armor, Resistance, enchantment protection, absorption, and health reduction. Those values can differ drastically.

The tracker therefore stores:

- estimated server hurt-cooldown ticks remaining;
- estimated raw `lastHurt`;
- confidence: `EXACT`, `MATCHED`, `BOUNDED`, or `UNKNOWN`;
- the predicted/observed damage event that established it;
- server-tick interval in which it was established.

Known intentional actions and successfully matched predicted incoming events can establish high-confidence state. Unexpected damage with unknown raw magnitude downgrades confidence.

**Safety rule:** when `lastHurt` is not reliable, do not credit hurt-cooldown damage cancellation in a lethal decision. Use the conservative no-benefit result until state is known again.

### 5.4 `ThreatPredictor` registry

Each predictor handles one coherent mechanic and emits normalized `ThreatEvent`s rather than directly equipping items.

A `ThreatEvent` contains:

- source/type and tags;
- earliest and latest plausible server impact tick;
- raw damage interval;
- confidence;
- repeat/cadence information;
- source/direct/causing entity references when known;
- impact/explosion position when known;
- assumptions/prerequisites;
- whether the event is avoidable, blockable, or relocatable;
- evidence used to create the event.

### 5.5 `ThreatTimelineSimulator`

Simulates an ordered sequence, not only the single biggest hit. Hurt cooldown means event order matters.

If multiple events can occur in the same uncertain server-tick window, evaluate all materially different plausible orders or a conservative worst-case ordering. A sequence of individually non-lethal hits can be lethal; conversely an earlier larger hit can change later hurt-cooldown behavior.

When a death-protection item pops, the timeline must apply the item's runtime `DEATH_PROTECTION` component, consume the correct hand stack, continue with the resulting health/effects/hurt state, and determine whether another death-protection item must be equipped before a subsequent lethal event.

### 5.6 `SurvivalPlanner`

Produces a small bounded set of actions, asks the timeline simulator for each resulting outcome, and selects one using hard safety constraints before secondary cost preferences.

Each action declares:

- minimum server time required;
- packets/state transitions required;
- preconditions and legality checks;
- predicted outcome interval;
- confidence/reliability class;
- consumable cost;
- inventory/movement disruption;
- rollback/restoration behavior;
- whether it is experimentally validated.

Do not run an unbounded combinatorial search. Generate only actions relevant to the current threat class and keep a strict per-tick candidate budget.

### 5.7 `ActionExecutor`

State-machine executor for the selected action. It owns packet sending and restoration, but not strategy scoring.

It must detect stale plans, inventory desync, missed deadlines, changed threats, and rejected/contradicted state. If an action becomes infeasible, immediately re-plan and escalate to a more reliable strategy when time allows.

## 6. Exact damage pipeline

The simulator must preserve vanilla ordering because changing the order changes lethal results and hurt-cooldown behavior.

### 6.1 Player-level preprocessing

Mirror `Player#hurtServer`:

1. reject damage if the player is invulnerable to the source because of player gamerules/state;
2. honor ability invulnerability unless the source has `BYPASSES_INVULNERABILITY`;
3. reject when dead/dying;
4. apply difficulty scaling when `DamageSource#scalesWithDifficulty()`:
   - Peaceful: zero;
   - Easy: `min(damage / 2 + 1, damage)`;
   - Hard: `damage * 1.5`;
5. reject zero and enter the living-entity pipeline.

### 6.2 Living-entity preprocessing and hurt cooldown

Mirror `LivingEntity#hurtServer` in this order:

1. generic invulnerability rejection;
2. dead/dying rejection;
3. Fire Resistance early-reject for `IS_FIRE`;
4. clamp negative damage to zero;
5. apply item blocking through the active `BLOCKS_ATTACKS` component;
6. apply freezing extra-damage multiplier when applicable;
7. apply `DAMAGES_HELMET` durability/reduction (`damage *= 0.75` with a helmet);
8. sanitize NaN/infinity as vanilla does;
9. apply hurt cooldown:
   - if `invulnerableTime > 10` and source does not have `BYPASSES_COOLDOWN`:
     - if `damage <= lastHurt`, the hit is rejected;
     - otherwise only `damage - lastHurt` enters `actuallyHurt`, then `lastHurt = damage`;
   - otherwise set `lastHurt = damage`, `invulnerableTime = 20`, and process full damage.

A small precursor hit therefore does **not** blanket-cancel a larger lethal hit. If the precursor establishes raw `lastHurt = 1` and a later hit is raw 20 while the strong cooldown is active, the later hit can still send 19 into the mitigation pipeline.

The runtime `BYPASSES_COOLDOWN` tag must be respected even though the supplied vanilla generated tag provider does not populate a vanilla member for it.

### 6.3 Armor, effects, enchantments, absorption

Mirror `actuallyHurt`:

1. armor unless `BYPASSES_ARMOR`;
2. use `CombatRules#getDamageAfterAbsorb`, including armor toughness and weapon-driven `EnchantmentHelper.modifyArmorEffectiveness`;
3. if `BYPASSES_EFFECTS`, skip Resistance and enchantment protection;
4. otherwise apply Resistance unless `BYPASSES_RESISTANCE` using the vanilla 20%-per-level formula;
5. if damage remains positive and the source does not have `BYPASSES_ENCHANTMENTS`, apply `EnchantmentHelper.getDamageProtection` and `CombatRules#getDamageAfterMagicAbsorb`;
6. consume absorption before health;
7. subtract remaining health damage.

Lethal means predicted health reaches **`<= 0`**, matching `isDeadOrDying`; health is clamped at zero by vanilla rather than becoming meaningfully negative.

### 6.4 Death protection

If health reaches zero, mirror `checkTotemDeathProtection`:

- immediately fail for sources tagged `BYPASSES_INVULNERABILITY`;
- check runtime `DEATH_PROTECTION` components in both interaction hands;
- consume one matching item;
- set health to 1;
- apply the component's configured effects;
- continue the threat timeline.

Do not hard-code “totem = offhand.” Vanilla checks both hands. The controller may intentionally put a totem/death-protection item in main hand if that is faster or preserves an active offhand shield.

## 7. Blocking/shield model

The standard 26.1.2 shield uses a `BLOCKS_ATTACKS` component with `block_delay_seconds = 0.25`, which is `5` game ticks via `blockDelayTicks()`.

A shield is protective only after the server has:

1. received/accepted item use;
2. accumulated the required block-delay ticks;
3. still has the item in use at impact;
4. sees a source that does not bypass the component;
5. sees the source inside the configured horizontal block angle;
6. is not handling a piercing arrow that bypasses blocking in `applyItemBlocking`.

The planner must include packet latency and jitter **before** the five-tick warmup. Merely having a shield in inventory/offhand is not protection.

If a currently active offhand shield can block an imminent threat while a totem is needed for a second threat, prefer equipping the totem to main hand when feasible instead of destroying the active block state.

## 8. Threat coverage

“Almost every damage type” means: support every vanilla 26.1.2 family for which the client has enough pre-impact information to produce a useful forecast, and explicitly label truly unobservable instant server-side damage rather than pretending it is predictable.

### 8.1 Explosions

Cover at least:

- primed TNT and minecart TNT;
- creepers;
- end crystals;
- respawn anchors and beds in explosive dimensions;
- fireworks;
- player/mob explosions that expose a predictable entity/fuse/trigger;
- other vanilla events that route through `ServerExplosion` or `BAD_RESPAWN_POINT`.

Use the exact 26.1.2 exposure and damage behavior:

- `ServerExplosion#getSeenPercent` samples the player's AABB and raycasts to the explosion center;
- `ExplosionDamageCalculator#getEntityDamageAmount` uses distance, exposure, explosion radius, and the vanilla formula;
- `ServerExplosion#explode` damages entities **before** `interactWithBlocks` destroys affected blocks.

That ordering makes emergency cover placement a real strategy: if a legal solid block reaches the server before detonation and occludes the exposure rays, it can reduce entity damage even if that same explosion destroys the block afterward.

For end crystals/anchors/beds with no long fuse, predict **potential immediate lethal states** instead of waiting for an explosion packet. Examples include an existing crystal within lethal range plus an opponent able to trigger it, or a legal crystal/anchor/bed interaction location an opponent can execute within the prediction horizon. These events should carry uncertainty and trigger conservative behavior in Safe mode.

### 8.2 Projectiles

Use projectile-family-specific vanilla movement/collision rather than a single linear extrapolator. Cover at minimum:

- arrows/spectral/tipped arrows;
- tridents;
- mob projectiles;
- llama spit where applicable;
- fireballs;
- wither skulls;
- wind charges;
- thrown potion-like projectiles capable of harmful effects;
- fireworks when their explosion can hit the player.

Simulate discrete ticks, drag/gravity/acceleration as defined by the actual class, and swept collision against blocks/entities. If damage contains randomness or unknown enchantment/critical state, emit a bounded range and use the lethal upper bound for protection decisions.

### 8.3 Melee, mace, spear, and mob attacks

A client cannot know another player's exact next input, so melee prediction is intent-uncertain. Emit a potential near-term hit when an attacker can legally reach/attack the player within the server-time horizon.

Use visible/derivable weapon attributes, attack state, effects, enchantments, relative movement, reach rules, and weapon-specific damage hooks.

Special cases:

- **mace:** estimate attacker fall trajectory/fall distance and use the actual `MaceItem` damage bonus/enchantment logic; if exact fall state is uncertain, use a conservative range;
- **spear:** inspect the 26.1.2 spear implementation and model its server-authoritative damage modes rather than treating it as a generic sword;
- **shield-disabling attacks:** account for `getSecondsToDisableBlocking` and resulting blocking cooldown when deciding whether shield is a valid plan.

### 8.4 Falls and movement damage

Predict:

- normal landing fall damage;
- stalagmite landing damage;
- elytra `FLY_INTO_WALL` collisions;
- falling anvils/blocks/stalactites when their future intersection is observable;
- void entry/fall trajectory;
- fall-damage multipliers/zero-damage landing surfaces from actual block mechanics.

Landing prediction must use collision geometry and current velocity rather than `fallDistance` alone.

### 8.5 Environment and periodic state damage

Cover predictable timers/contact for:

- in-fire/on-fire/campfire/lava/hot-floor;
- cactus and sweet berry bush;
- suffocation (`IN_WALL`);
- cramming;
- drowning;
- starvation;
- freezing/powder-snow state;
- outside-world-border damage;
- dry-out where relevant;
- dragon-breath/area-effect hazards;
- Wither and other damaging status-effect ticks;
- poison/magic ticks, while respecting mechanics such as effects that cannot reduce health below their vanilla floor;
- lightning when a strike entity/state is observable early enough.

Predict the actual next damage tick/cadence instead of treating these as constant DPS.

### 8.6 Conditional/reactive damage

- **Ender pearl:** model the guaranteed 5 raw pearl damage on successful player teleport plus reset of fall distance/current impulse context.
- **Thorns:** before an outgoing player attack, estimate potential reflected damage when target equipment/enchantments make it relevant; random outcomes remain a range.
- **Generic command/mod damage:** if the server creates instant damage with no client-observable precursor, mark it unobservable. Do not claim predictive coverage.
- **`FELL_OUT_OF_WORLD` and `GENERIC_KILL`:** source tags bypass death protection. The planner must avoid the condition itself; equipping a totem is not a solution.

## 9. Server timing and uncertainty

Create a `ServerTimingEstimator` that maintains:

- observed RTT/ping;
- short-term jitter;
- local tick duration;
- bounded estimate of packet arrival/processing tick;
- a configurable internal safety margin derived from uncertainty, not a fixed “human reaction” delay.

Never assume one-way latency is known exactly. Use conservative arrival bounds.

Every action exposes a **server deadline**. Examples:

- carried-slot change: selected slot must reach server before lethal damage processing;
- inventory `SWAP`: the container click must be processed before damage;
- shield: start-use packet must be processed at least 5 server ticks before impact;
- block placement: use-on-block must be processed before explosion entity damage;
- movement: the accepted server position/AABB must be safe before collision/explosion evaluation;
- pearl/wind-charge/potion: spawn, flight, collision, and resulting state change all must finish before impact.

If a deadline cannot be met under the conservative timing bound, the action is infeasible regardless of how good it looks locally.

## 10. Totem/death-protection controller

### 10.1 Selection order

When death protection is required, choose the fastest valid route that does not unnecessarily destroy another active defense:

1. death-protection item already in either hand: no inventory action;
2. item in another hotbar slot: serverbound carried-slot selection can put it in main hand with one packet;
3. item elsewhere in player inventory: use a single vanilla container `SWAP` into either:
   - the currently selected hotbar slot (main hand), or
   - offhand (`buttonNum == 40`),
   choosing whichever better preserves other protection and has valid menu synchronization.

`AbstractContainerMenu` explicitly supports `SWAP` with hotbar buttons 0–8 and offhand button 40. Use the current menu's real player-inventory slot mapping and state id; never hard-code screen slot ids.

### 10.2 Transaction safety

Track each emergency inventory transaction with:

- source inventory index/container slot;
- destination hand/hotbar index;
- pre-swap stack identities/counts/components;
- container id and state id;
- expected changed slots;
- send tick/deadline;
- observed server updates/contradictions.

If the state is stale or a transaction is rejected/overwritten, re-plan immediately. Do not perform multi-click cursor juggling for an emergency when a one-packet `SWAP` route is available.

### 10.3 Restoration

Restoration is lower priority than survival.

Restore the prior main/offhand state only after:

- no lethal threat exists inside a configurable internal grace horizon;
- the server inventory state is consistent;
- the death-protection item was not consumed in a way that invalidates the saved transaction;
- restoration will not interrupt active blocking/eating/charging required for a remaining threat.

After a pop, immediately re-evaluate the timeline and equip another death-protection item if a second lethal event can arrive before the post-pop state becomes safe.

## 11. Non-totem survival strategies

Strategies are plugins with explicit feasibility and outcome simulation. The following belong in the planner.

### 11.1 Avoid/evasion

Move the accepted server AABB out of projectile collision, melee reach, explosion radius/exposure, border danger, environmental contact, or a predicted landing hazard.

Generate only physically/server-valid movement under the player's current movement state. Do not depend on impossible teleport packets, locally spoofed positions, or anticheat desync.

### 11.2 Explosion cover placement

For explosion threats, search a small set of legal reachable block placements around the player. For each candidate:

1. apply the candidate block to a temporary world/exposure view;
2. recompute `getSeenPercent`-equivalent exposure;
3. recompute explosion raw damage and final damage;
4. reject placement if packet/reach/collision/deadline rules fail;
5. choose the placement with the strongest worst-case survival outcome.

The candidate block does not need to survive the explosion to occlude entity-damage rays, but it must have valid collision/occlusion behavior at the time entity damage is evaluated.

### 11.3 Shield/blocking

Use only if the block is already active or can be active before impact with the 5-tick server warmup and latency margin. Rotate toward the damage source only when required and only if the rotation can reach the server before impact.

### 11.4 Armor/equipment optimization

Evaluate feasible one-packet equipment swaps against the specific threat. Examples:

- chestplate instead of elytra before an explosion/melee hit;
- specialized Protection variants when they materially change the source's post-mitigation damage;
- leather equipment for freeze-related mechanics when supported by actual 26.1.2 behavior.

Use runtime enchantment/components and the damage source tags rather than item-name heuristics.

### 11.5 Effects/consumables

Treat use duration and projectile travel as real costs.

Potential actions include:

- splash/other quickly applicable Resistance or Fire Resistance effects;
- healing/absorption effects where they finish in time;
- food/golden-apple use only when the threat horizon exceeds the full use duration;
- Water Breathing/Slow Falling or other effects for longer-horizon environmental threats.

Never score a 32-tick consumption action as an emergency save for a hit arriving in 3 ticks.

### 11.6 Escape/relocation

Ender pearls can reset fall distance after successful teleport and relocate the player out of a blast/contact/void trajectory, but the teleport also applies 5 raw `ENDER_PEARL` damage. Simulate the full sequence and actual pearl flight/impact time.

Chorus/random relocation is too uncertain for normal deterministic planning and should be experimental-only if added.

### 11.7 Fall clutches

Support server-valid fall rescues such as:

- water placement;
- safe landing-block placement/use when valid;
- elytra activation/recovery when conditions allow;
- wind-charge impulse behavior;
- mace-smash fall reset when a valid target and timing exist;
- ender-pearl relocation/reset.

Two 26.1.2 mechanics are explicitly worth modeling:

- `ServerPlayer#onExplosionHit` marks a wind-charge explosion as an impulse that can limit later effective fall distance from the impulse impact position;
- `MaceItem#postHurtEnemy` resets fall distance after a valid smash attack.

Both require actual server-valid impacts/hits; they are not packet-only toggles.

## 12. Hurt-cooldown / “iframe” manipulation

Hurt cooldown is part of the normal timeline simulator for every threat. **Deliberately inducing damage** is a separate high-risk strategy and must never be assumed beneficial.

For a candidate intentional precursor, simulate:

1. precursor raw damage after blocking/freezing/helmet stages;
2. precursor armor/effect/enchantment/absorption/health loss;
3. resulting server `lastHurt` and `invulnerableTime`;
4. timing of the real incoming hit;
5. whether the incoming source bypasses cooldown;
6. residual incoming raw damage `max(incoming - lastHurt, 0)` when vanilla rules allow;
7. mitigation of that residual damage;
8. combined health/absorption result and later threats.

Only execute if all of the following hold:

- server `lastHurt` state is high-confidence;
- the intentional damage source and exact timing are server-valid and controllable;
- the complete worst-case timeline leaves the player alive;
- the result is materially safer than doing nothing;
- the action does not displace a more reliable feasible save;
- the tactic has passed runtime verification for 26.1.2.

A tiny fire tick before a huge hit normally fails this test because the larger hit still applies the excess above `lastHurt`.

Experimental hurt-cooldown manipulation belongs behind the `Experimental` safety mode until specific strategies have deterministic tests and local-server evidence.

## 13. Planner policy and modes

Expose only a few coherent presets rather than dozens of magic thresholds.

### Safe (default)

- survival dominates all item-cost concerns;
- if a high-confidence/worst-case lethal event can arrive before another **proven** action guarantees survival, equip death protection;
- use already-active blocking, clearly legal movement, and deterministic cover/equipment saves when they complete earlier and remain safe;
- no deliberate damage manipulation.

### Balanced

- allows proven non-totem actions to preserve a totem when their conservative timeline is safely non-lethal;
- accepts slightly more movement/inventory disruption;
- still rejects unverified hurt-cooldown tricks.

### Experimental

- enables individually validated research strategies such as deliberate hurt-cooldown manipulation;
- still requires server-valid mechanics, conservative timing, and a simulated survival advantage;
- “experimental” is not permission to send impossible movement or assume client desync changes server damage.

Additional user-facing settings should stay minimal:

- restore prior hand/item after danger: on/off;
- allow automatic movement/evasion: on/off;
- allow block placement/clutches: on/off;
- debug overlay/decision logging: on/off.

Advanced numerical tolerances stay internal unless real testing proves users need them.

## 14. Uncertainty/failure handling

The engine must fail conservatively.

- Unknown raw damage: use a defensible upper bound.
- Unknown server `lastHurt`: assume no cooldown reduction.
- Unknown event ordering: simulate worst materially plausible order.
- Unknown inventory synchronization: prefer an already-held/hotbar route or maintain current death protection instead of risky restoration.
- Unknown shield activation deadline: do not count shield protection.
- Predictor disagreement: merge threats rather than silently choosing the lowest estimate.
- Missed action deadline: abandon that action and re-plan immediately.
- No totem and no guaranteed save: choose the feasible action with the best worst-case remaining health/survival probability; do not intentionally add damage unless its complete sequence is proven better.
- `BYPASSES_INVULNERABILITY`: never claim a totem can save it.

The module should surface a small debug reason such as `TOTEM: lethal crystal 14.3 -> -2.1 HP in 3–4 server ticks` or `COVER: obsidian placement reduces worst-case crystal damage 16.0 -> 3.2` when diagnostics are enabled.

## 15. Performance constraints

Target stable 20 TPS client processing without frame hitching.

- broad-phase scan only entities/blocks inside the maximum relevant threat horizon;
- cache immutable/repeated player mitigation state until equipment/effects change;
- only run expensive explosion exposure raycasts for actual/potential lethal explosion candidates and candidate cover positions;
- cap projectile simulation horizon and number of tracked projectiles;
- use spatial filtering before detailed melee/explosion prediction;
- bound planner candidates per tick;
- carry forward unchanged threats rather than rebuilding every object every frame;
- never perform world-wide block scans.

Debug instrumentation must be optional and must not alter decisions/timing significantly.

## 16. Testing and verification

### 16.1 Pure deterministic tests

Create unit tests for `DamageSimulator` independent of rendering/networking. Include golden cases for:

- difficulty scaling;
- armor/toughness and armor-effectiveness modification;
- Resistance levels;
- enchantment protection;
- absorption;
- `BYPASSES_ARMOR`, `BYPASSES_EFFECTS`, `BYPASSES_RESISTANCE`, `BYPASSES_ENCHANTMENTS`;
- fire-resistance early rejection;
- freezing multiplier;
- helmet reduction;
- full and partial shield blocking;
- shield bypass and piercing-arrow bypass;
- shield warmup boundary (4 vs 5 elapsed ticks);
- death protection in main hand and offhand;
- death protection rejected by `BYPASSES_INVULNERABILITY`;
- hurt-cooldown sequences: smaller/equal follow-up, larger follow-up, cooldown expiry, and synthetic `BYPASSES_COOLDOWN` fixture;
- multi-hit timelines around a totem pop.

Tests must assert intermediate stage values, not only final health, so ordering regressions are obvious.

### 16.2 Predictor tests

Use deterministic fixtures/recorded snapshots for:

- projectile trajectories and collision ticks;
- TNT fuse/explosion timing;
- explosion distance/exposure formula;
- occlusion with/without candidate cover;
- fall landing position/damage;
- mace fall-damage estimate ranges;
- environmental periodic tick deadlines;
- server-timing deadline calculations.

### 16.3 Inventory/action tests

Test the totem transaction state machine with mocked menu snapshots:

- already in hand;
- hotbar selection;
- inventory-to-selected-hotbar `SWAP`;
- inventory-to-offhand `SWAP`;
- active offhand shield preserved by mainhand totem route;
- stale container state/rejection;
- restoration after danger;
- second totem after first pop.

### 16.4 Exact-runtime validation

Use a local vanilla/Fabric 26.1.2 server or test-only server harness to compare simulator output to real health/absorption under controlled scenarios. At minimum validate representative cases for:

- melee;
- arrows/tridents;
- crystal/TNT/bed-or-anchor explosions with varying cover;
- shields and 5-tick activation timing;
- armor/Resistance/Protection combinations;
- hurt-cooldown event sequences;
- fall/wind-charge/mace/ender-pearl mechanics;
- lava/fire/drowning/freezing/Wither-like periodic damage;
- death-protection pop and repeated threats.

Runtime tests must clearly distinguish:

- **source-confirmed:** behavior directly supported by 26.1.2 source;
- **runtime-confirmed:** reproduced against a running 26.1.2 server;
- **experimental:** plausible but not yet validated.

Never promote an exploit-like strategy from experimental based only on source speculation.

### 16.5 Build/CI

GitHub Actions must build on Java 25 using the pinned 26.1.2 Fabric dependencies and run all unit/static tests. The production jar must not include test-only server helpers.

## 17. Observability

Provide a compact optional debug HUD/log with:

- predicted threats and impact windows;
- raw and final damage intervals;
- current shadow hurt-cooldown state/confidence;
- chosen action and deadline;
- rejected actions with one-line reasons;
- inventory transaction state;
- actual post-event health so predicted vs observed outcomes can be compared.

Keep a bounded in-memory decision history for debugging. Do not spam normal chat or disk by default.

## 18. Implementation boundaries

Suggested package boundaries under `projects/predictive-survival-26-1-2/src/client/java/...`:

- `core/` — orchestrator and immutable snapshots
- `damage/` — exact damage/hurt/death-protection simulation
- `timing/` — server clock/latency estimates
- `threat/` — predictor API and implementations
- `timeline/` — multi-event simulation
- `planner/` — actions, scoring, feasibility
- `action/` — packet/state-machine executors
- `inventory/` — menu mapping and emergency transactions
- `debug/` — HUD/trace output
- `mixin/` — only accessors/hooks that cannot be implemented through public client APIs

Keep predictors and actions small and independently testable. Avoid one mega-class containing physics, inventory packets, and decision policy.

## 19. Initial implementation order

The later implementation plan should preserve this dependency order:

1. exact Fabric 26.1.2 project + CI baseline;
2. immutable snapshots + `DamageSimulator` + golden tests;
3. shadow hurt state + timeline simulator;
4. totem/death-protection inventory controller;
5. explosion predictor and cover calculation;
6. projectile and fall predictors;
7. melee/mace/spear and environment/status predictors;
8. planner + Safe mode;
9. non-totem actions (shield, movement, equipment, effects, clutches);
10. Balanced policy;
11. runtime validation suite;
12. experimental hurt-cooldown strategies only after the base engine is proven.

A later phase may parallelize independent predictor/action modules across Agents-repo workers after the core interfaces and golden damage behavior are stable.

## 20. Definition of done

The feature is done only when:

- the project builds for Minecraft 26.1.2 on Java 25;
- a deterministic damage/timeline core matches source-confirmed behavior across the test matrix;
- lethal predicted events cause death protection to be in a server-recognized hand before the conservative deadline when available;
- the controller can choose main hand or offhand and restore state safely;
- almost every client-observable vanilla damage family has a predictor or an explicit documented reason it cannot be forecast before impact;
- no-totem actions are compared through the same timeline simulator rather than hard-coded emergency hacks;
- shield timing, hurt cooldown, explosion exposure, death-protection bypass tags, and multi-hit sequences are correct;
- unverified exploit behavior remains disabled/experimental;
- runtime validation documents prediction error and known limitations honestly;
- CI and relevant tests pass before completion.

## 21. Explicit non-goals

- No claim of surviving truly unobservable instant server commands/damage.
- No reliance on impossible movement, packet flooding, ghost inventory state, or client-only spoofing as “damage prevention.”
- No hard-coded assumption that every server uses untouched vanilla tags/components; runtime registries remain authoritative where exposed.
- No giant settings wall or dozens of magic tuning sliders before testing proves they are needed.
- No implementation of speculative iframe tricks before the core vanilla damage model and validation harness are correct.
