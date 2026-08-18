package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectilePredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void arrowReportsFirstSweptPlayerIntersection() {
        List<ThreatEvent> events = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "false"))),
            List.of()
        ));

        assertEquals(new TickWindow(7, 7), events.getFirst().impact());
        assertEquals(DamageRange.exact(6f), events.getFirst().damage().rawDamage());
    }

    @Test
    void observedServerLeadWidensImpactWindowWithoutChangingDamage() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of(
                "base_damage", "6.0",
                "critical", "false",
                "observation_age_ticks", "1"
            ))),
            List.of()
        )).getFirst();

        assertEquals(new TickWindow(6, 7), event.impact());
        assertEquals(DamageRange.exact(6f), event.damage().rawDamage());
    }

    @Test
    void tridentExplicitRawDamageOverridesArrowLikeVelocityFormula() {
        WorldSnapshot.EntitySnapshot trident = new WorldSnapshot.EntitySnapshot(
            "trident:1",
            "minecraft:trident",
            new Vec3Snapshot(0, 1.9, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.775, 0.175, 0.125, 2.025, 0.425),
            Map.of(
                "base_damage", "2.0",
                "raw_damage", "8.0",
                "critical", "false"
            )
        );

        ThreatEvent event = predictor.predict(context(List.of(trident), List.of())).getFirst();
        assertEquals(DamageRange.exact(8f), event.damage().rawDamage());
    }

    @Test
    void earlierWallCollisionRemovesPlayerThreat() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(4, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        assertTrue(predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "false"))),
            List.of(wall)
        )).isEmpty());
    }

    @Test
    void modernSplashAndLingeringPotionTypesRemainThrowableFamilies() {
        assertEquals(ProjectileFamily.THROWABLE, ProjectileFamily.from(splashHarming()).orElseThrow());
        assertEquals(ProjectileFamily.THROWABLE, ProjectileFamily.from(lingeringHarming()).orElseThrow());
    }

    @Test
    void splashHarmingDirectPlayerCollisionUsesFullMagicDamage() {
        ThreatEvent event = predictor.predict(context(List.of(splashHarming()), List.of())).getFirst();

        assertEquals(DamageRange.exact(12f), event.damage().rawDamage());
        assertEquals("minecraft:indirect_magic", event.damage().sourceKey());
        assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertFalse(event.blockable());
    }

    @Test
    void splashHarmingNearbyWallCollisionStillEmitsReducedMagicDamage() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        ThreatEvent event = predictor.predict(context(List.of(splashHarming()), List.of(wall))).getFirst();
        assertTrue(event.damage().rawDamage().max() > 0f);
        assertTrue(event.damage().rawDamage().max() < 12f);
        assertFalse(event.blockable());
    }

    @Test
    void splashHarmingDoesNotTreatCurrentVelocityAsGuaranteedEscape() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        List<ThreatEvent> events = predictor.predict(context(
            List.of(splashHarming()),
            List.of(wall),
            new Vec3Snapshot(1.0, 0, 0)
        ));

        assertFalse(events.isEmpty(), "current velocity alone cannot prove the player will escape a nearby splash");
        DamageRange range = events.getFirst().damage().rawDamage();
        assertEquals(0f, range.min(), 0.0001f);
        assertTrue(range.max() > 0f);
        assertTrue(range.max() <= 12f);
    }

    @Test
    void splashHarmingWallCollisionOutsideFourBlockRadiusDoesNotInventThreat() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        assertTrue(predictor.predict(context(List.of(splashHarming()), List.of(wall))).isEmpty());
    }

    @Test
    void lingeringHarmingForecastsHalfStrengthCloudTenTicksAfterImpact() {
        ThreatEvent event = predictor.predict(context(List.of(lingeringHarming()), List.of())).stream()
            .filter(candidate -> candidate.id().endsWith(":lingering_cloud:0"))
            .findFirst()
            .orElseThrow();

        assertEquals(DamageRange.exact(6f), event.damage().rawDamage());
        assertEquals("minecraft:indirect_magic", event.damage().sourceKey());
        assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertEquals(new TickWindow(15, 15), event.impact());
        assertFalse(event.blockable());
    }

    @Test
    void dragonFireballDirectCollisionProducesLingeringBreathThreat() {
        List<ThreatEvent> events = predictor.predict(context(
            List.of(dragonFireball()),
            List.of()
        ));

        assertTrue(!events.isEmpty(), "a visible dragon fireball that collides with the player must predict its breath cloud");
        ThreatEvent first = events.getFirst();
        assertEquals(DamageRange.exact(6f), first.damage().rawDamage());
        assertEquals("minecraft:indirect_magic", first.damage().sourceKey());
        assertTrue(first.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertTrue(first.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertEquals(20L, first.impact().latest() - first.impact().earliest());
    }

    @Test
    void dragonFireballWallCollisionNearPlayerStillProducesBreathThreat() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        List<ThreatEvent> events = predictor.predict(context(
            List.of(dragonFireball()),
            List.of(wall)
        ));

        assertTrue(!events.isEmpty(), "dragon breath remains dangerous when the projectile hits nearby cover first");
        assertEquals(DamageRange.exact(6f), events.getFirst().damage().rawDamage());
    }

    @Test
    void dragonFireballWallCollisionOutsideCloudRadiusDoesNotInventThreat() {
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        assertTrue(predictor.predict(context(
            List.of(dragonFireball()),
            List.of(wall)
        )).isEmpty());
    }

    @Test
    void unknownCriticalStateWidensDamageRange() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0", "critical", "unknown"))),
            List.of()
        )).getFirst();

        assertEquals(6f, event.damage().rawDamage().min(), 0.0001f);
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
    }

    @Test
    void missingCriticalStateIsTreatedAsUnknown() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("base_damage", "6.0"))),
            List.of()
        )).getFirst();

        assertEquals(6f, event.damage().rawDamage().min(), 0.0001f);
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
    }

    @Test
    void missingBaseDamageNeverFallsBackToVanillaMinimum() {
        ThreatEvent event = predictor.predict(context(
            List.of(arrow(Map.of("critical", "false"))),
            List.of()
        )).getFirst();

        assertEquals(Float.MAX_VALUE, event.damage().rawDamage().max());
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        return context(entities, blocks, new Vec3Snapshot(0, 0, 0));
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks,
        Vec3Snapshot playerVelocity
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), playerVelocity, Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot arrow(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "arrow:1",
            "minecraft:arrow",
            new Vec3Snapshot(0, 1.9, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.775, 0.175, 0.125, 2.025, 0.425),
            properties
        );
    }

    private static WorldSnapshot.EntitySnapshot splashHarming() {
        return potion(
            "splash:1",
            "minecraft:splash_potion",
            Map.of(
                "potion_instant_damage", "12.0",
                "potion_splash_radius", "4.0",
                "potion_source_key", "minecraft:indirect_magic"
            )
        );
    }

    private static WorldSnapshot.EntitySnapshot lingeringHarming() {
        return potion(
            "lingering:1",
            "minecraft:lingering_potion",
            Map.of(
                "potion_instant_damage", "12.0",
                "potion_lingering", "true",
                "potion_source_key", "minecraft:indirect_magic"
            )
        );
    }

    private static WorldSnapshot.EntitySnapshot potion(
        String id,
        String type,
        Map<String, String> properties
    ) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            type,
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }

    private static WorldSnapshot.EntitySnapshot dragonFireball() {
        return new WorldSnapshot.EntitySnapshot(
            "dragon:1",
            "minecraft:dragon_fireball",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.5, 0.5, -0.2, 0.5, 1.5, 0.8),
            Map.of("acceleration_power", "0.0")
        );
    }
}
