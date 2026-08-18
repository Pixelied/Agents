# Predictive Survival 26.1.2 Validation

This document separates exact-runtime evidence from source-confirmed and unit-tested behavior. The mod intentionally does not claim that a client can predict information the server has not exposed yet.

## Exact runtime confirmation

The dedicated Fabric client GameTest source set launches Minecraft 26.1.2 under the integrated server and currently verifies:

- Generic raw damage parity: the pure `DamageSimulator` prediction is compared directly with the server result for a 4-damage generic hit.
- Hurt cooldown / `lastHurt` semantics: a same-window 5 -> 3 -> 8 sequence produces the vanilla result where 5 applies, 3 is rejected, and only the 3-point excess of the final hit applies.
- Mainhand death protection: a Totem of Undying is consumed and health is set to 1 on lethal generic damage.
- Offhand death protection: the same lethal-damage behavior is verified independently for the offhand.

The client GameTest waits until the integrated server reports `ServerGamePacketListenerImpl.hasClientLoaded()` before damaging the test player. This matters because Minecraft 26.1.2 treats a not-yet-loaded server player as invulnerable.

Runtime tolerance for the direct health comparisons above is `0.0001` health.

## Source-confirmed and unit-tested behavior

The following behavior is derived from the supplied Minecraft 26.1.2 source and covered by deterministic unit tests / full Fabric compilation, but is not labeled runtime-confirmed by the current GameTest suite:

- Armor, armor toughness, armor durability, helmet damage, Resistance, enchantment-protection bounds, absorption, difficulty scaling, and bypass damage tags.
- Exact hurt-cooldown uncertainty handling when server `lastHurt` is not confidently known.
- Five-server-tick shield warmup, block-angle checks, bypass-shield handling, and piercing-arrow blocking behavior.
- Multi-hit chronological simulation, including armor breakage, carried cooldown state, death-protection consumption, and continued simulation after a pop.
- Explosions for crystals, primed TNT, charged/normal creepers, beds/respawn anchors where explosive, fireworks, and explosion-capable projectiles. Cover is only credited when collision geometry is proven; unknown or partial shapes never grant optimistic blast safety.
- Projectile motion/collision families, including arrows, thrown projectiles, hurting projectiles, firework timing, projectile-size-aware swept collision, wall interception, and conservative unknown arrow metadata.
- Potential melee, mace smash, mob attacks, and Minecraft 26.1.2 spear/KineticWeapon conditions. Uncommitted enemy attacks remain potential rather than being treated as certain future clicks.
- Falls, void damage, elytra wall collision, stalagmite/falling-object damage, and exact private falling-block damage coefficients captured through narrow mixin accessors.
- Periodic/environmental hazards including fire, lava, drowning, suffocation/cramming, freezing, starvation, world border, poison-floor behavior, and lethal Wither-style ticking damage.
- Reactive Thorns bounds and the guaranteed 5 raw self-damage from a locally owned Ender Pearl. If an exact pearl collision tick is unavailable, the live predictor uses a bounded projectile-horizon window rather than dropping the damage.
- Death-protection routing across selected hotbar, offhand, hotbar selection, and server-valid menu `SWAP` routes. Active offhand shielding can force a mainhand protection route instead of destroying the shield state.
- Conservative server-authority timing for held-slot changes and item-use warmup based on the latest packet-processing bound.
- Safe-mode and Balanced-mode planning, bounded to `EngineLimits.maxPlannerCandidates()`, using full timeline simulation instead of fixed "Totem first" priority.
- Experimental deliberate hurt-cooldown manipulation remains rejected unless the server hurt state is trusted, timing is exact/controllable, the strategy has been explicitly runtime-validated, and worst-case simulation materially beats doing nothing.

## Live runtime coverage

The production client runtime currently wires:

1. timing estimation,
2. player/inventory/menu/world snapshots,
3. registered threat predictors,
4. chronological threat timeline construction,
5. protection/shield candidate generation,
6. bounded survival planning,
7. authoritative action executors,
8. packet/client interaction dispatch,
9. server-state reconciliation and replanning,
10. bounded optional debug HUD/history.

The production runtime never marks an inventory/use action successful merely because the local client changed. It waits for conservative observed authority state. A failed or contradicted execution route is removed for the current danger window and the engine replans within its candidate bound.

## Explicit limitations

These are intentional limitations, not silent assumptions:

- A client cannot guarantee prediction of an enemy click, command/mod damage, or other server action that has no observable precursor. Such attacks are potential/unobservable until evidence exists.
- Network latency and server scheduling can make a theoretically correct survival action arrive too late. The timing model accounts for RTT/jitter conservatively, but it cannot deliver a packet in the past.
- Live enchantment mitigation is conservative where exact source-specific protection cannot be proven from current client state; the planner must prefer unnecessary protection over a false safety claim.
- Generic movement and block-placement/clutch actions are represented and simulatable, but the production dispatcher refuses to invent an unproven movement path or legal support face. If a concrete server-valid route is unavailable, execution fails and the engine replans.
- Thorns prediction requires an observable outgoing-attack target. Remote armor visibility alone does not imply that the local player will attack.
- Experimental intentional-damage strategies start disabled (`runtimeValidated == false`) and are not promoted by source reasoning alone.
- The debug HUD is optional and off by default. Normal chat and disk logging are not used for routine decisions.

## Validation commands

Project verification:

```bash
gradle --no-daemon clean test build
gradle --no-daemon compileGametestJava processGametestResources
xvfb-run -a gradle --no-daemon runClientGameTest
```

CI also verifies that the production JAR contains no `dev/pixelied/survival/validation/` GameTest classes.
