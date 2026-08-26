# Predictive Survival 26.1.2 Instant-Burst Audit

Scope: Minecraft Java 26.1.2 only, using the exact source bundle supplied for this project. This is an in-game hazard-modeling audit for Predictive Survival.

Classification:
- `actual-lead-time`: the damaging in-game state itself is observable early enough to model.
- `opportunity-modeled`: an earlier legal in-game action can create damage before waiting for the final state is safe.
- `no-observable-precursor`: no synchronized precursor is tight enough to justify earlier modeling without inventing hidden server state.

| Family | 26.1.2 source evidence | Earliest defensible observation | Classification | Handling |
| --- | --- | --- | --- | --- |
| Primed TNT | `PrimedTnt.tick/explode` | entity + synchronized fuse | actual-lead-time | `ExplosionPredictor`, observation-aged fuse |
| Creeper | `Creeper.tick/explodeCreeper` | entity + synchronized swell/ignited/powered flags | actual-lead-time | `ExplosionPredictor`, hidden radius bounded by safety mode |
| Wither spawn | `WitherBoss` invulnerability countdown then explosion | entity + synchronized invulnerability count | actual-lead-time | `ExplosionPredictor` countdown |
| Existing End Crystal | `EndCrystal.hurtServer` | crystal entity | opportunity-modeled | actual triggerable threat plus precursor model |
| Crystal place then break | `EndCrystalItem.useOn`, `EndCrystal.hurtServer` | attacker + legal support/space/reach + visible crystal where policy requires | opportunity-modeled | `CrystalOpportunityPredictor`; bounded exact work + fail-closed overflow |
| Bed | `BedBlock.useWithoutItem` | exploding-dimension rule + placement/use legality | opportunity-modeled | `BedOpportunityPredictor`, post-removal exposure |
| Respawn anchor | `RespawnAnchorBlock.useItemOn/useWithoutItem/explode` | anchor charge/dimension + visible glowstone where required | opportunity-modeled | `RespawnAnchorOpportunityPredictor`; source removed before exposure; water is not fake entity shielding |
| TNT minecart | `MinecartTNT.tick/hurtServer/destroy/causeFallDamage` | cart motion/collision/fall/incoming burning projectile or primed fuse | opportunity-modeled | `TntMinecartOpportunityPredictor`; primed countdown stays actual timeline |
| Player melee | `Player.attack` | attacker motion/LOS/range/weapon | opportunity-modeled | `MeleeApproachOpportunityPredictor`; already-in-range stays `MeleePredictor` |
| Mace | `Player.attack` mace-smash branch | player melee state + mace/fall uncertainty | opportunity-modeled | same approach predictor, shared `MeleePredictor` formula |
| Piercing spear STAB | `ServerboundPlayerActionPacket.STAB`, `PiercingWeapon.attack`, `ProjectileUtil.getHitEntitiesAlong`, `AttackRange` | visible spear + attacker/target motion + first legal hostile STAB ray | opportunity-modeled | `MeleeApproachOpportunityPredictor`; `ServerPlayerAttackRange` uses the 26.1.2 positive `knownMovement · look` extension over feasible hostile rays, not movement magnitude; exact-runtime pre-arm/pop proof |
| Kinetic spear use | `KineticWeapon.damageEntities` | visible spear/use state + relative motion once synchronized | actual-lead-time | separate from STAB; current melee modeling fails closed when exact kinetic fields are unavailable, and this audit does not claim exact-runtime validation of the delayed kinetic-use path |
| Mob melee | `MeleeAttackGoal.tick/checkAndPerformAttack`, `MeleeAttack` | mob motion/LOS/range/vehicle box | opportunity-modeled | projected first legal range-entry tick using `MobMeleeRange`; damage reused from `MeleePredictor` |
| Arrow / spectral arrow | `AbstractArrow.onHitEntity` | spawned projectile | actual-lead-time | `ProjectilePredictor` |
| Bow | `BowItem.releaseUsing` -> arrow | spawned arrow; remote use state is also observable | actual-lead-time | exact runtime probe proves first-projectile observation still leaves authority lead; no precursor predictor |
| Loaded crossbow arrow | `CrossbowItem.use` -> arrow | visible loaded crossbow | opportunity-modeled | `ProjectileReleaseOpportunityPredictor` |
| Trident | `ThrownTrident.onHitEntity` | spawned trident | no-observable-precursor | actual `ProjectilePredictor`; no hidden pre-release guess |
| Thrown spear plan candidate | supplied source has `SpearAttack/SpearApproach/SpearRetreat/SpearUseGoal` but no separate thrown-spear projectile entity | N/A | actual-lead-time | no separate projectile predictor; spear damage remains item-action/raycast handling rather than a fabricated thrown projectile |
| Llama spit | `LlamaSpit.onHitEntity` | spawned spit | no-observable-precursor | `ProjectilePredictor` after spawn |
| Fireball / small fireball | `AbstractHurtingProjectile` subclasses | spawned projectile | no-observable-precursor | `ProjectilePredictor` + followups |
| Dragon fireball | `DragonFireball` impact -> `AreaEffectCloud` | spawned projectile/cloud | no-observable-precursor | projectile + cloud modeling |
| Wither skull | `WitherSkull` hit path | spawned skull | no-observable-precursor | projectile + status followup |
| Player wind charge | `WindChargeItem.use`, `AbstractWindCharge.onHitEntity` | visible wind charge + first-tick reach | opportunity-modeled | `ProjectileReleaseOpportunityPredictor` when direct hit can be lethal |
| Breeze wind charge | `BreezeWindCharge` / `AbstractWindCharge` | spawned projectile | no-observable-precursor | actual projectile only; no invented AI windup |
| Spawned firework rocket | `FireworkRocketEntity.explode` | spawned rocket + components | actual-lead-time | `ProjectilePredictor` |
| Loaded crossbow firework | `CrossbowItem.use` -> firework | visible loaded crossbow + firework payload | opportunity-modeled | `ProjectileReleaseOpportunityPredictor` |
| Splash Harming | `ThrownSplashPotion.onHitAsPotion` | visible splash potion + visible instant-damage payload + first-tick reach | opportunity-modeled | `ProjectileReleaseOpportunityPredictor` |
| Other splash potion | `ThrownSplashPotion.onHitAsPotion` | spawned potion + contents | actual-lead-time | `ProjectilePredictor` + status followups |
| Lingering potion | lingering impact -> `AreaEffectCloud` | spawned potion/cloud | actual-lead-time | projectile/cloud timeline |
| Area-effect cloud | `AreaEffectCloud.tick` | existing cloud state | actual-lead-time | area/status timeline |
| Ender pearl self damage | `ThrownEnderpearl` impact owner damage | own spawned pearl | actual-lead-time | projectile/self-damage path |
| Shulker bullet | `ShulkerBullet.onHitEntity` | existing homing bullet | actual-lead-time | `ShulkerBulletPredictor` |
| Evoker fangs | `EvokerFangs.tick/dealDamageTo` | fangs + warmup | actual-lead-time | `EvokerFangsPredictor` |
| Guardian beam | `GuardianAttackGoal.tick` | active target/beam charge | actual-lead-time | `GuardianBeamPredictor` |
| Warden sonic boom | `SonicBoom.tick` | charge/target state | actual-lead-time | `WardenSonicBoomPredictor` |
| Lightning | `LightningBolt.tick/thunderHit` | bolt itself | no-observable-precursor | reactive/environment handling once observable |
| Fall | living/player fall pipeline | local position/velocity/fall state | actual-lead-time | `FallPredictor` |
| Falling block / anvil | `FallingBlockEntity.causeFallDamage` | falling entity + motion/components | actual-lead-time | current falling-block/projectile-environment path |
| Contact / fire / cactus / berry / environment | block/entity contact and environment damage paths | current AABB + nearby environment | actual-lead-time | contact annotator + `EnvironmentPredictorRegistry` |
| Reactive damage | source-specific accepted-hit followups | initiating threat + observable reactive state where available | actual-lead-time | `ReactiveDamagePredictor` with causal prerequisites |
| Poison / Wither / periodic status | effect tick path | synchronized current effect + cadence | actual-lead-time | status timeline; first application tied to causal source when known |

## Exact instant-burst authority result

`InstantBurstValidationClientGameTest` drives the production client snapshot/runtime against an integrated 26.1.2 server and requires protection to become server-authoritative before the real hostile damage path. The suite covers:

- End Crystal placement followed immediately by the server crystal-break explosion path.
- Uncharged respawn-anchor charge + detonation without an observation gap, plus an already-charged anchor immediate-use case.
- Explosive bed placement + use without an observation gap.
- Unprimed TNT minecart glancing-collision prediction and a separate burning-arrow ignition path.
- Primed TNT whose client-visible fuse must be aged by live network timing rather than treated as exact server time.
- Ordinary player melee and mace smash at the first legal server-range-entry tick.
- Netherite spear `PiercingWeapon.attack` / STAB at the first legal adversarial ray through the vanilla `AttackRange` hitbox margin.

The STAB proof is intentionally separate from `KineticWeapon.damageEntities`. Vanilla extends the STAB ray only by `max(0, knownMovement · look)`, so the production reach helper rejects full forward credit for tangential movement. A deterministic regression guards that rule, while the integrated-server fixture still delegates final legality to vanilla `ProjectileUtil.getHitEntitiesAlong`.

## Exact player-launch authority result

`FirstFrameProjectileAuthorityValidationScenarios` compares protection started only after first projectile observation against protection already authoritative from precursor state. It asserts classification, not brittle absolute ticks.

- Bow: first-projectile path retains authority lead; precursor path survives.
- Loaded crossbow arrow: first-projectile path does not guarantee authority; precursor path survives.
- Loaded crossbow damaging firework: same race; precursor path survives.
- Wind charge: same race; precursor path survives.
- Splash Harming: same race; precursor path survives.

Therefore the four racing families are handled by `ProjectileReleaseOpportunityPredictor`; Bow intentionally is not.

## Performance / completeness

`OpportunityBudgetTest` guards a 4,000-block, 16-player, 200-sample opportunity fixture at median <2 ms and p95 <5 ms. Crystal receives a deterministic per-family share of `EngineLimits.maxOpportunities()` for exact explosion narrow phases and emits a conservative overflow opportunity for unscanned legal candidates. Snapshot occlusion prefilters non-colliders and crystal occupancy uses indexed block cells. No frame-crossing health/equipment/damage cache was added.

The source audit found five material precursor gaps: four player-launch authority races (loaded crossbow arrow, loaded crossbow firework, wind charge, splash Harming) and mob first-range-entry melee. All five are now opportunity-modeled. Families with no defensible synchronized precursor are explicitly left at their first observable state rather than fabricating server AI/RNG/NBT state.
