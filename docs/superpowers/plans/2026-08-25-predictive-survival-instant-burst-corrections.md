# Predictive Survival Instant-Burst Plan Corrections

These corrections are normative and override the corresponding text in `docs/superpowers/plans/2026-08-25-predictive-survival-instant-burst.md`. They were found during the plan's post-write source review against the supplied Minecraft Java 26.1.2 sources. Execution must read the approved design, the main implementation plan, and this correction file before touching production code.

## Correction 1 — Task 3 compound-shape RED geometry

The Task 3 example originally separated boxes along X while also firing the ray along X, which would hit the first component instead of traveling through a gap. Replace that RED with components separated along Y so the ray crosses the block through the actual empty channel:

```java
@Test
void rayThroughGapBetweenTwoCollisionComponentsRemainsVisible() {
    WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
        new Vec3Snapshot(1.5, 0.5, 0.5),
        "minecraft:test_split",
        true,
        List.of(
            new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 0.25, 1.0),
            new AabbSnapshot(1.0, 0.75, 0.0, 2.0, 1.0, 1.0)
        ),
        Map.of("collision_min_y", "0", "collision_max_y", "1")
    );
    SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(split));

    assertFalse(view.blocksExplosionRay(
        new Vec3Snapshot(0.5, 0.5, 0.5),
        new Vec3Snapshot(2.5, 0.5, 0.5)
    ));
}
```

This specifically proves that a conservative single `VoxelShape.bounds()` envelope must not fill disjoint/compound gaps.

## Correction 2 — Task 4 `ExplosionSpec` responsibility

Remove `removesSourceBeforeExplosion` from the proposed `ExplosionSpec`. Source removal is a world-state/occlusion concern, not explosion-source metadata.

Use this interface instead:

```java
public record ExplosionSpec(
    Vec3Snapshot center,
    float radiusMin,
    float radiusMax,
    String sourceKey,
    boolean scalesWithDifficulty,
    boolean blockable
) {}
```

The caller supplies an `OcclusionView` representing the world state at the moment entity damage is calculated. Bed halves / anchor source blocks are removed by constructing a filtered `SnapshotOcclusionView` before calling `ExplosionThreatFactory`. This keeps actual and hypothetical explosions on the same raw-damage path without hiding mutation ordering inside the explosion spec.

The Task 4 parity example becomes:

```java
ExplosionSpec spec = new ExplosionSpec(
    new Vec3Snapshot(3, 0, 0),
    6f,
    6f,
    "minecraft:explosion",
    true,
    true
);
```

## Correction 3 — Respawn Anchor water does not reduce same-explosion entity exposure

Minecraft 26.1.2 `RespawnAnchorBlock.explode` removes the anchor, computes whether water would flow at the source, and installs a custom `ExplosionDamageCalculator#getBlockExplosionResistance` override for the source block position. `ServerExplosion.hurtEntities`, however, obtains entity exposure from `ServerExplosion.getSeenPercent`, whose clip is `Block.COLLIDER` + `Fluid.NONE`. The custom block-resistance override participates in exploded-block calculation, not the immediate entity exposure/damage calculation.

Therefore replace Task 7's water requirement with:

- remove the anchor before same-explosion exposure calculation;
- do **not** add fake water shielding/reduction to `ExplosionExposure` or `ExplosionThreatFactory`;
- preserve `RespawnAnchorBlock.isWaterThatWouldFlow` evidence only if later block-destruction/follow-up modeling needs to reproduce which blocks survive;
- add a regression proving surrounding/above water does not change immediate projected player damage unless the water's associated block collision geometry independently blocks the `Block.COLLIDER` ray (normal water has no collider).

Required RED:

```java
@Test
void anchorWaterResistanceDoesNotInventEntityDamageShielding() {
    ThreatEvent dry = predictChargedAnchor(false).projectedThreat();
    ThreatEvent sourceWater = predictChargedAnchor(true).projectedThreat();

    assertEquals(dry.damage().rawDamage().max(), sourceWater.damage().rawDamage().max(), 1.0E-6f);
}
```

Delete the plan sentence that says to apply the water override in the projected explosion occlusion/resistance model for immediate player damage.

## Correction 4 — Add explicit first-frame player-projectile authority probe

Task 11's complete hazard-family audit must include a concrete exact-runtime probe for player-launched projectile families whose first spawned entity may be too late to react to at point blank. Do not decide this from held-item intuition alone.

Create:

- `src/gametest/java/dev/pixelied/survival/validation/FirstFrameProjectileAuthorityValidationScenarios.java`
- `src/test/java/dev/pixelied/survival/core/RemoteUseStateSnapshotContractTest.java`

Capture remote player precursor evidence in `MinecraftMeleeSnapshotAdapter` / the player snapshot adapter:

```java
properties.put("using_item", Boolean.toString(player.isUsingItem()));
properties.put("used_hand", player.isUsingItem()
    ? (player.getUsedItemHand() == InteractionHand.OFF_HAND ? "off_hand" : "main_hand")
    : "none");
properties.put("client_observed_use_ticks", Integer.toString(Math.max(0, player.getTicksUsingItem())));
```

`isUsingItem` and used hand come from `LivingEntity.DATA_LIVING_ENTITY_FLAGS`. The client initializes `useItemRemaining` when that synchronized flag arrives, so `client_observed_use_ticks` is observation-relative rather than authoritative server elapsed time; bound it with `TimingSnapshot.observationAgeWindow()`.

The exact-runtime probe must cover at least:

1. a bow release at lethal point-blank range after the minimum legal draw;
2. a loaded crossbow firing an arrow;
3. a loaded crossbow firing a damaging firework rocket;
4. a wind charge use;
5. a splash Harming potion use.

For each family, run two paths:

```text
A: protection begins only after the first projectile entity is client-observable
B: protection is already authoritative from precursor state
```

Record whether path A can be guaranteed to establish protection before server damage at the tested minimum legal range. If A cannot be guaranteed for a family, that family is **not** allowed to remain merely `actual-lead-time` in `INSTANT_BURST_AUDIT.md`.

### If any probe demonstrates the same authority race

Before Task 11 can pass, create:

- `src/client/java/dev/pixelied/survival/threat/opportunity/ProjectileReleaseOpportunityPredictor.java`
- `src/test/java/dev/pixelied/survival/threat/opportunity/ProjectileReleaseOpportunityPredictorTest.java`

The predictor may support only the failing source-confirmed families. It must use synchronized precursor evidence and source-accurate launch semantics; it may not use `held dangerous item => lethal`.

Required interface remains:

```java
public final class ProjectileReleaseOpportunityPredictor implements LethalOpportunityPredictor {
    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        // one family-specific evaluator per source-confirmed launch mechanic
    }
}
```

For draw/release weapons, compute a server elapsed-use interval from observed local use ticks plus observation age rather than calling the local remote-player counter exact. For loaded crossbows, inspect the synchronized `DataComponents.CHARGED_PROJECTILES` contents from the held stack snapshot. For immediate-use items such as wind charge and splash potion, require a source-confirmed geometric path to the local player's swept AABB and evaluate the hypothetical impact through the same projectile/damage helpers used by existing projectile prediction.

### If all probes prove first-entity observation still provides guaranteed authority lead time

Do not add `ProjectileReleaseOpportunityPredictor`. Instead, `INSTANT_BURST_AUDIT.md` must include the exact runtime scenario names/results that justify `actual-lead-time` for those five families. This is a measured conclusion, not a guess.

## Correction 5 — Projectile collision geometry follows Task 3 exact components

Task 3 introduces exact `BlockSnapshot.collisionBoxes`. Update `ProjectilePredictor.CollisionBlockIndex` during that task or immediately afterward so projectile collision does not keep using a single partial-shape envelope while explosion occlusion uses exact components.

Required regression:

```java
@Test
void projectileCanPassThroughRealGapInCompoundCollisionShape() {
    // two disjoint Y components; projectile path crosses y=0.5 gap
    // assert no block collision is reported before the player intersection
}
```

This prevents the instant-burst work from making explosion geometry exact while leaving the shared nearby collision snapshot internally inconsistent.

## Correction 6 — Completion gate

Task 13 cannot claim the plan fully executed unless:

- the three corrections above are reflected in final production/tests;
- `FirstFrameProjectileAuthorityValidationScenarios` has an explicit outcome for all five listed player-launch cases;
- `INSTANT_BURST_AUDIT.md` points to runtime evidence for every player-launch family classified as safe to handle only after projectile spawn;
- any family proven to have the same authority race has a precursor predictor and zero-delay regression before final CI/artifact verification.
