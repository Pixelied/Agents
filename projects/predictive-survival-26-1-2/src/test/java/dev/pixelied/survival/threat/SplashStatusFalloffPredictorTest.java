package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
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

class SplashStatusFalloffPredictorTest {
    @Test
    void freshWallSplashPoisonUsesVanillaPotionAabbDistanceAndRoundedDuration() {
        List<ThreatEvent> poison = EnvironmentPredictorRegistry.defaults().predict(context(
            splashPoison(Map.of(
                "potion_poison_duration_ticks", "200",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0",
                "potion_splash_radius", "4.0",
                "projectile_margin", "0.0"
            )),
            wallAt(5)
        )).stream().filter(event -> event.id().contains(":splash_status:poison:")).toList();

        assertFalse(poison.isEmpty());
        ThreatEvent first = poison.getFirst();
        // Impact is tick 4. Potion AABB at the x=5 wall face ends at x=5.125; player starts at 6.7.
        // scale = 1 - 1.575/4 = 0.60625; round(200*scale) = 121; 121 % 25 = 21.
        assertEquals(new TickWindow(25, 25), first.impact());
        assertEquals(DamageRange.exact(1f), first.damage().rawDamage());
        assertEquals("minecraft:magic", first.damage().sourceKey());
        assertEquals(1f, first.damage().applicationHealthThresholdExclusive(), 0.0001f);
        assertEquals(Confidence.EXACT, first.confidence());
    }

    @Test
    void futureImpactUsesVanillaProjectileMarginFromPredictedAge() {
        List<ThreatEvent> poison = EnvironmentPredictorRegistry.defaults().predict(context(
            splashPoison(Map.of(
                "potion_poison_duration_ticks", "200",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0",
                "potion_splash_radius", "4.0",
                "projectile_age_ticks", "2"
            )),
            wallAt(5)
        )).stream().filter(event -> event.id().contains(":splash_status:poison:")).toList();

        assertFalse(poison.isEmpty());
        // Collision is four modeled ticks away, so vanilla margin at impact is (2 + 4 - 2) / 20 = 0.2.
        // Effective x gap becomes 6.5 - 5.125 = 1.375; rounded duration is 131; 131 % 25 = 6.
        assertEquals(new TickWindow(10, 10), poison.getFirst().impact());
    }

    @Test
    void staleWallSplashPoisonKeepsConservativeFutureDamage() {
        List<ThreatEvent> poison = EnvironmentPredictorRegistry.defaults().predict(context(
            splashPoison(Map.of(
                "potion_poison_duration_ticks", "200",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0",
                "potion_splash_radius", "4.0",
                "projectile_margin", "0.0",
                "observation_age_ticks", "1"
            )),
            wallAt(5)
        )).stream().filter(event -> event.id().contains(":splash_status:poison:")).toList();

        assertFalse(poison.isEmpty(), "one-tick projectile staleness cannot prove a nearby status splash is safe");
        ThreatEvent first = poison.getFirst();
        assertEquals(Confidence.BOUNDED, first.confidence());
        assertEquals(0f, first.damage().rawDamage().min(), 0.0001f);
        assertEquals(1f, first.damage().rawDamage().max(), 0.0001f);
        assertEquals(1f, first.damage().applicationHealthThresholdExclusive(), 0.0001f);
    }

    @Test
    void wallSplashPoisonOutsideFourBlockRadiusDoesNotInventStatusThreat() {
        List<ThreatEvent> poison = EnvironmentPredictorRegistry.defaults().predict(context(
            splashPoison(Map.of(
                "potion_poison_duration_ticks", "200",
                "potion_poison_amplifier", "0",
                "potion_duration_scale", "1.0",
                "potion_splash_radius", "4.0",
                "projectile_margin", "0.0"
            )),
            wallAt(2)
        )).stream().filter(event -> event.id().contains(":splash_status:poison:")).toList();

        assertTrue(poison.isEmpty());
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        WorldSnapshot.BlockSnapshot wall
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of(wall)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot splashPoison(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "splash:status",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }

    private static WorldSnapshot.BlockSnapshot wallAt(int x) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
    }
}
