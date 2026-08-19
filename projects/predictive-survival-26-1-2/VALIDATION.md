# Predictive Survival 26.1.2 Validation

This document separates exact-runtime evidence from source-confirmed and deterministic-test coverage. The mod intentionally does not claim that a client can predict information the server has not exposed yet.

## Exact runtime confirmation

The dedicated Fabric client GameTest source set launches Minecraft Java 26.1.2 with an integrated server and verifies the following against authoritative server state or the real production client snapshot/runtime boundary:

- Generic damage parity and `player_attack` damage parity.
- Diamond-chestplate armor + Resistance I + Protection IV mitigation parity using the runtime enchantment registry.
- Hurt-cooldown / `lastHurt` behavior for smaller, equal, and larger follow-up hits after an initial hit.
- Shield activation at the real client/server boundary: four use ticks are not active and five use ticks are active. The test drives the actual client use input and samples the integrated server rather than synthesizing server-only use state.
- Mainhand and offhand Totem of Undying activation independently.
- A repeated lethal sequence with a Totem in each hand, including consumption of the first protection, carried hurt-cooldown/effect state, and consumption of the second protection on the follow-up.
- Explosion exposure and final-health parity for radius-4 TNT-scale and radius-6 crystal-scale explosions, both unobstructed and behind real obsidian cover. The test compares `ExplosionExposure.seenPercent` directly with vanilla `ServerExplosion.getSeenPercent` before applying the explosion.
- A live Hard-difficulty TNT case through the production client world snapshot and `ExplosionPredictor`. The test synchronizes client/server player position, requires the predictor's pre-difficulty raw blast damage to equal the server's vanilla exposure calculation, and then requires exact final-health parity after vanilla Hard difficulty scaling.
- Live TNT, End Crystal, and explosive respawn-anchor snapshot metadata for difficulty-scaled explosion damage. Crystal and bad-respawn metadata are compared at the production snapshot boundary rather than inferred only from unit fixtures.
- A primed TNT minecart producing a bounded explosion threat through the production snapshot/predictor path.
- Live arrow prediction through the actual client world snapshot + `ProjectilePredictor`, including impact-window containment and final-health parity.
- Live trident prediction through the same path, including Minecraft 26.1.2's fixed 8 raw entity-hit damage and impact-window containment.
- Hard-difficulty mob-owned arrow and mob-owned trident cases. A real non-player `LivingEntity` owns each projectile, and final predicted health must equal the authoritative server hit after vanilla difficulty scaling.
- Owner-sensitive direct-hit source metadata for mob-owned Large Fireballs, Small Fireballs, and Wither Skulls. Each fixture asks the server's actual `DamageSource.scalesWithDifficulty()` result, requires the production client snapshot to expose the same value, and requires the corresponding direct projectile threat to preserve it.
- An adversarial unresolved-owner case on Hard difficulty. A projectile remains mob-owned on the server while its owner is evicted only from the client world. The production snapshot must fail closed rather than treating client-null ownership as proof that the projectile is ownerless; ambiguous Wither Skull direct damage remains the conservative vanilla 5..8 `magic`/`wither_skull` envelope without crediting armor or shield mitigation.
- Owner-sensitive snapshot metadata for mob-owned Llama Spit, breeze wind charge, Firework Rocket, and splash potion sources. The fixture compares the real server `DamageSource.scalesWithDifficulty()` value with the production client snapshot rather than inferring it from unit fixtures.
- Snapshot admission remains fail-closed under pressure: distant damaging arrows remain observable, harmless tracked entities cannot crowd out a damaging projectile, and excess threat-relevant entities produce the explicit observation-overflow marker instead of being silently discarded. Dedicated small-cap cases cover both Evoker Fangs and lightning.
- A client-visible Shulker Bullet producing a pre-impact projectile threat.
- An active Guardian beam producing its bounded pre-impact damage sequence.
- An observed Warden sonic-boom charge producing a pre-impact sonic threat.
- Visible Evoker Fangs producing a pre-impact `minecraft:indirect_magic` threat through the production runtime. The exact 26.1.2 damage tags make this damage bypass armor and, because `bypasses_shield` includes `#minecraft:bypasses_armor`, bypass shields as well. Owner/timing state that is not client-observable is handled conservatively.
- Runtime damage-source/tag + final-health parity for fall, Ender Pearl, wind charge, mace smash, lava, on-fire, drowning, freezing, Wither, Thorns, suffocation/in-wall, and starvation damage.
- A real burning-client observability case where the client receives `isOnFire == true` while its local `remainingFireTicks == 0`. The live predictor must still produce a bounded potential `minecraft:on_fire` event whose window contains the authoritative server damage tick and whose final-damage result matches the server.
- Live contact-hazard wiring through `MinecraftSurvivalRuntime`: standing on synchronized magma reaches the production timeline as `minecraft:hot_floor` rather than existing only in an isolated predictor test.
- Conservative cramming observability through the production runtime. A real tracked pushable entity is overlapped with the local player at capture time and must produce a potential `minecraft:cramming` threat without pretending the hidden server gamerule is known.
- A client-visible lightning bolt in the vanilla strike box produces four conservative cooldown-eligible potential threats. The exact-runtime fixture deliberately uses a server `visualOnly` bolt to prove fail-closed behavior when that server-only flag is unavailable to the client.
- Reactive Thorns through the production runtime with two independently enchanted visible armor pieces. The observation preserves per-piece levels rather than summing them into one proc, producing two independent bounded retaliations; the threat metadata also preserves vanilla shield eligibility.
- A tipped arrow carrying Wither retains a pre-impact status threat.
- Dragon-fireball observable damage reaches the pre-impact threat model.
- Splash and lingering Harming, Poison, and Wither paths are exercised through real potion metadata. This includes wall-impact splash falloff, poison/wither future tick persistence, stacked hidden Wither tails, post-projectile-removal persistence, bounded infinite-Wither handling, and lingering cloud handoff for damaging status effects.
- Potion snapshot metadata required for timing/effect prediction is validated at the production boundary.

The client GameTest waits until the integrated server reports `ServerGamePacketListenerImpl.hasClientLoaded()` before authoritative damage checks. This matters because Minecraft 26.1.2 treats a not-yet-loaded server player as invulnerable.

Direct health comparisons use a `0.0001` tolerance. Timing-sensitive projectile/fire tests additionally require the real server impact tick to fall inside the predictor's declared `TickWindow`.

## Source-confirmed and deterministic-test coverage

The following behavior is derived from the supplied Minecraft 26.1.2 source and covered by deterministic unit tests / full Fabric compilation. Some members of these families also have the runtime evidence above, but the broader family is not claimed runtime-confirmed unless explicitly listed there.

- Full mitigation ordering: difficulty scaling, invulnerability gates, blocking, freezing multiplier, helmet handling, hurt cooldown, armor/toughness, Resistance, enchantment protection, absorption, health, and death protection.
- Armor durability and sequential armor-break behavior, including a later hit becoming more damaging after a piece breaks.
- Conservative hurt-cooldown uncertainty handling when server `lastHurt` is not confidently known. Unknown state is never credited as protection.
- Shield angle checks, bypass-shield behavior, piercing-projectile behavior, conservative server-confirmed use warmup, and predictor/planner metadata consistency. Exact 26.1.2 tag inclusion is respected: any modeled damage source carrying `BYPASSES_ARMOR` also carries `BYPASSES_SHIELD` where vanilla's `bypasses_shield` tag inherits `#minecraft:bypasses_armor`.
- Multi-hit chronological simulation, including carried cooldown state, armor durability, absorption/effects, death-protection consumption, and continued simulation after a pop.
- Causal threat dependencies for post-hit effects. A dependent future hazard is simulated only when its prerequisite hit was actually accepted by the vanilla damage pipeline; a hit rejected by hurt cooldown cannot manufacture a follow-up effect.
- Small Fireball direct hits use the correct attributed/unattributed fireball source identity and, when accepted, add the vanilla five-second ignition sequence. The later `on_fire` damage uses its own environmental source semantics rather than inheriting the projectile's owner scaling.
- Wither Skull direct hits distinguish visible living-owner `minecraft:wither_skull` damage from the client-ambiguous ownerless/opaque case. Accepted hits add Wither II for 200 ticks on Normal and 800 ticks on Hard; Easy/Peaceful do not receive the effect. The later `minecraft:wither` ticks use their own status source semantics.
- Explosions for crystals, primed TNT, TNT minecarts, charged/normal creepers, beds/respawn anchors where explosive, fireworks, and explosion-capable projectiles. Cover is credited only when collision geometry is proven; unknown or partial shapes do not grant optimistic blast safety. Merely touching a block face at the ray origin while moving away does not count as blast cover.
- Vanilla `minecraft:explosion` / `player_explosion` damage is modeled as `DamageScaling.ALWAYS`. Projectile collision explosions therefore remain difficulty-scaled regardless of whether the projectile owner is a player, mob, unresolved, or absent; this is intentionally separate from owner-sensitive direct fireball damage.
- Projectile motion/collision families including arrows, tridents, thrown projectiles, hurting projectiles, fireworks, projectile-size-aware swept collision, wall interception, live-observation-age compensation, and conservative unknown projectile metadata.
- Owner-sensitive difficulty metadata is captured for observed projectiles rather than guessed from the projectile class alone. Arrows, tridents, hurting projectiles, Llama Spit, wind charges, fireworks, and indirect-magic potion/dragon-cloud damage preserve the non-player-living-owner rule. When the client cannot resolve the owner, Hard difficulty fails closed to the damaging scaled case; Easy/Normal do not invent a scaling multiplier that could increase the prediction.
- Lingering/dragon AreaEffectCloud attribution preserves the source's owner-sensitive difficulty flag across projectile-to-cloud handoff instead of losing it when the projectile entity disappears.
- Potential melee, mace smash, mob attacks, and Minecraft 26.1.2 spear/KineticWeapon conditions. Uncommitted enemy attacks remain potential rather than being treated as guaranteed future clicks.
- Falls, void damage, elytra wall collision, stalagmite/falling-object damage, and exact private falling-block damage coefficients captured through narrow mixin accessors.
- Periodic/environmental hazards including lava, fire, contact hazards, drowning, suffocation, cramming, freezing, starvation, world border, poison-floor behavior, lethal Wither ticking, and observable lightning.
- When the client knows an exact remaining fire countdown, on-fire damage uses that exact phase. When only the synchronized burning flag is observable, the predictor emits 20-tick bounded `POTENTIAL` windows instead of inventing a server countdown or dropping the threat.
- Reactive Thorns bounds are modeled independently per visible enchanted armor piece, and the guaranteed 5 raw self-damage from a locally owned Ender Pearl is modeled separately. If an exact pearl collision tick is unavailable, the live predictor uses a bounded projectile-horizon window instead of dropping the damage.
- Death-protection routing across selected main hand, offhand, alternate hotbar selection, and server-valid menu `SWAP` routes. Active offhand shielding can force a mainhand protection route instead of destroying shield state.
- Conservative server-authority timing for held-slot changes, inventory/equipment mutations, item use, cover placement, movement/rescue actions, and item-use warmup. A zero additional-warmup action still pays the next packet-processing window unless it is a true no-op/already-active state.
- Pending multi-tick actions retain execution progress across a normal threat countdown. If the same threat's absolute impact schedule unexpectedly tightens, the engine revalidates the active action and can immediately replace it with another survival-producing plan instead of waiting on a now-impossible deadline.
- Safe and Balanced planning are bounded by `EngineLimits.maxPlannerCandidates()` and compare complete simulated timelines instead of using a fixed "Totem first" priority.
- Experimental deliberate hurt-cooldown manipulation remains rejected unless server hurt state is trusted, timing is exact/controllable, the individual strategy is explicitly runtime-validated, and its worst-case simulation materially beats doing nothing.

## Live runtime coverage

The production client runtime currently wires:

1. timing estimation,
2. player/inventory/menu/world snapshots,
3. explosion, projectile, melee, fall, reactive, and environmental predictors,
4. chronological threat-timeline construction,
5. protection/shield candidate generation,
6. bounded survival planning,
7. authoritative action executors,
8. client interaction / packet dispatch,
9. server-state reconciliation and replanning,
10. bounded optional debug HUD/history.

The production runtime never marks an inventory/use action successful merely because local client state changed. It waits for conservative observed authority state. A failed or contradicted execution route is removed for the current danger window and the engine replans within its candidate bound.

## Explicit limitations

These are intentional limitations, not silent assumptions:

- A client cannot guarantee prediction of an enemy click, command/mod damage, or other server action with no observable precursor. Such attacks remain potential/unobservable until evidence exists.
- Network latency and server scheduling can make a theoretically correct survival action arrive too late. The timing model accounts for RTT/jitter conservatively, but it cannot deliver a packet in the past.
- The client cannot observe the authoritative server `lastHurt` value directly. Production snapshots therefore do not optimistically credit unknown hurt-cooldown protection; this can create unnecessary survival actions, but it avoids a false-safe lethal prediction.
- Live enchantment mitigation is conservative where exact source-specific protection cannot be proven from current client state; the planner must prefer unnecessary protection over a false safety claim.
- The client does not receive the server's authoritative fire countdown. A synchronized burning flag with no usable countdown is represented as bounded potential fire timing rather than exact timing.
- The server's `maxEntityCramming` gamerule is not available as trusted synchronized client state. An overlapping tracked pushable entity therefore creates a conservative `POTENTIAL` cramming threat; the client does not claim that cramming damage is certain.
- Lightning's server-only `visualOnly` state is not trusted as client-observable. A visible bolt whose vanilla strike box overlaps the player therefore fails closed as potential damaging lightning even when an authoritative test server knows that particular bolt is cosmetic. False positives in this case are intentional.
- Evoker Fangs can expose the damaging entity without exposing all owner/timing state needed for exact prediction. Pre-event timing and owner-dependent difficulty behavior are therefore conservative rather than optimistically assumed safe.
- Shulker Bullet direct damage is predicted pre-impact, but the predictor does not pre-simulate the full 200-tick Levitation trajectory that an accepted bullet hit can create. Once Levitation and the resulting velocity are synchronized on the client, normal snapshots/fall prediction see the new state. Before impact, the mod does not pretend it knows an exact future landing surface or fall path that depends on intervening player input/world collision.
- A damaging AreaEffectCloud that becomes observable without a preceding attributable projectile/cloud-creation event may not expose enough synchronized effect/owner metadata for exact reconstruction. The tracker therefore preserves known projectile-to-cloud attribution but does not invent payloads for orphan clouds.
- Generic movement and block-placement/clutch actions are represented and simulatable, but the production dispatcher refuses to invent an unproven movement path or legal support face. If a concrete server-valid route is unavailable, execution fails and the engine replans.
- Thorns prediction requires an observable outgoing-attack target. Remote armor visibility alone does not imply that the local player will attack. When a target is known, visible Thorns enchantment levels are kept per armor piece because vanilla evaluates pieces independently.
- Experimental intentional-damage strategies start disabled (`runtimeValidated == false`) and are not promoted by source reasoning alone. No deliberate hurt-cooldown manipulation strategy is currently runtime-promoted.
- The debug HUD is optional and off by default. Normal chat and disk logging are not used for routine decisions.

## Validation commands

Project verification:

```bash
gradle --no-daemon clean test build
gradle --no-daemon compileGametestJava processGametestResources
xvfb-run -a gradle --no-daemon runClientGameTest
```

Repository/workspace verification:

```bash
python -m unittest discover -s tests -v
python agentctl.py validate
```

CI verifies that the production JAR contains no `dev/pixelied/survival/validation/` GameTest classes and uploads release artifacts only after the project build, exact-runtime GameTests, and packaging-isolation checks pass.

The repository-level workspace validator is a separate required gate. The feature-branch validation workflow runs the repository Python test suite and `agentctl.py validate`; task and lease metadata remain separate from the production JAR.
