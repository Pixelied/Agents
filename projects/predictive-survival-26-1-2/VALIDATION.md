# Predictive Survival 26.1.2 Validation

This document separates exact-runtime evidence from source-confirmed and deterministic-test coverage. The mod intentionally does not claim that a client can predict information the server has not exposed yet.

## Exact runtime confirmation

The dedicated Fabric client GameTest source set launches Minecraft Java 26.1.2 with an integrated server and currently verifies all of the following against authoritative server state:

- Generic damage parity and `player_attack` damage parity.
- Diamond-chestplate armor + Resistance I + Protection IV mitigation parity using the runtime enchantment registry.
- Hurt-cooldown / `lastHurt` behavior for smaller, equal, and larger follow-up hits after an initial hit.
- Shield activation at the real client/server boundary: four use ticks are not active and five use ticks are active. The test drives the actual client use input and samples the integrated server rather than synthesizing server-only use state.
- Mainhand and offhand Totem of Undying activation independently.
- A repeated lethal sequence with a Totem in each hand, including consumption of the first protection, carried hurt-cooldown/effect state, and consumption of the second protection on the follow-up.
- Explosion exposure and final-health parity for radius-4 TNT-scale and radius-6 crystal-scale explosions, both unobstructed and behind real obsidian cover. The test compares `ExplosionExposure.seenPercent` directly with vanilla `ServerExplosion.getSeenPercent` before applying the explosion.
- Live arrow prediction through the actual client world snapshot + `ProjectilePredictor`, including impact-window containment and final-health parity.
- Live trident prediction through the same path, including Minecraft 26.1.2's fixed 8 raw entity-hit damage and impact-window containment.
- Runtime damage-source/tag + final-health parity for fall, Ender Pearl, wind charge, mace smash, lava, on-fire, drowning, freezing, Wither, Thorns, suffocation/in-wall, and starvation damage.
- A real burning-client observability case where the client receives `isOnFire == true` while its local `remainingFireTicks == 0`. The live predictor must still produce a bounded potential `minecraft:on_fire` event whose window contains the authoritative server damage tick and whose final-damage result matches the server.

The client GameTest waits until the integrated server reports `ServerGamePacketListenerImpl.hasClientLoaded()` before authoritative damage checks. This matters because Minecraft 26.1.2 treats a not-yet-loaded server player as invulnerable.

Direct health comparisons use a `0.0001` tolerance. Timing-sensitive projectile/fire tests additionally require the real server impact tick to fall inside the predictor's declared `TickWindow`.

## Source-confirmed and deterministic-test coverage

The following behavior is derived from the supplied Minecraft 26.1.2 source and covered by deterministic unit tests / full Fabric compilation. Some members of these families also have the runtime evidence above, but the broader family is not claimed runtime-confirmed unless explicitly listed there.

- Full mitigation ordering: difficulty scaling, invulnerability gates, blocking, freezing multiplier, helmet handling, hurt cooldown, armor/toughness, Resistance, enchantment protection, absorption, health, and death protection.
- Armor durability and sequential armor-break behavior, including a later hit becoming more damaging after a piece breaks.
- Conservative hurt-cooldown uncertainty handling when server `lastHurt` is not confidently known. Unknown state is never credited as protection.
- Shield angle checks, bypass-shield behavior, piercing-projectile behavior, and conservative server-confirmed use warmup.
- Multi-hit chronological simulation, including carried cooldown state, armor durability, absorption/effects, death-protection consumption, and continued simulation after a pop.
- Explosions for crystals, primed TNT, charged/normal creepers, beds/respawn anchors where explosive, fireworks, and explosion-capable projectiles. Cover is credited only when collision geometry is proven; unknown or partial shapes do not grant optimistic blast safety.
- Projectile motion/collision families including arrows, tridents, thrown projectiles, hurting projectiles, fireworks, projectile-size-aware swept collision, wall interception, live-observation-age compensation, and conservative unknown projectile metadata.
- Potential melee, mace smash, mob attacks, and Minecraft 26.1.2 spear/KineticWeapon conditions. Uncommitted enemy attacks remain potential rather than being treated as guaranteed future clicks.
- Falls, void damage, elytra wall collision, stalagmite/falling-object damage, and exact private falling-block damage coefficients captured through narrow mixin accessors.
- Periodic/environmental hazards including lava, fire, drowning, suffocation/cramming, freezing, starvation, world border, poison-floor behavior, and lethal Wither ticking.
- When the client knows an exact remaining fire countdown, on-fire damage uses that exact phase. When only the synchronized burning flag is observable, the predictor emits 20-tick bounded `POTENTIAL` windows instead of inventing a server countdown or dropping the threat.
- Reactive Thorns bounds and the guaranteed 5 raw self-damage from a locally owned Ender Pearl. If an exact pearl collision tick is unavailable, the live predictor uses a bounded projectile-horizon window instead of dropping the damage.
- Death-protection routing across selected main hand, offhand, alternate hotbar selection, and server-valid menu `SWAP` routes. Active offhand shielding can force a mainhand protection route instead of destroying shield state.
- Conservative server-authority timing for held-slot changes and item-use warmup based on the latest packet-processing bound.
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
- Generic movement and block-placement/clutch actions are represented and simulatable, but the production dispatcher refuses to invent an unproven movement path or legal support face. If a concrete server-valid route is unavailable, execution fails and the engine replans.
- Thorns prediction requires an observable outgoing-attack target. Remote armor visibility alone does not imply that the local player will attack.
- Experimental intentional-damage strategies start disabled (`runtimeValidated == false`) and are not promoted by source reasoning alone. No deliberate hurt-cooldown manipulation strategy is currently runtime-promoted.
- The debug HUD is optional and off by default. Normal chat and disk logging are not used for routine decisions.

## Validation commands

Project verification:

```bash
gradle --no-daemon clean test build
gradle --no-daemon compileGametestJava processGametestResources
xvfb-run -a gradle --no-daemon runClientGameTest
```

CI also verifies that the production JAR contains no `dev/pixelied/survival/validation/` GameTest classes and uploads the production JAR only after the project build, exact-runtime GameTests, and packaging-isolation checks pass.

The repository-level workspace validator is a separate gate. At the time of this validation, its Python unit tests pass, while `agentctl.py validate` is blocked by unrelated pre-existing missing `handoffs/` directories in `tasks/build-fallen-knight-26-1-2` and `tasks/build-spear-client-26-1-2`.
