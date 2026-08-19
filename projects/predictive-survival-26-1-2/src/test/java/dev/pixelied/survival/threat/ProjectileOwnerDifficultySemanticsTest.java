package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileOwnerDifficultySemanticsTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void mobOwnedSplashInstantDamageRetainsDifficultyScaling() {
        WorldSnapshot.EntitySnapshot potion = movingProjectile(
            "splash",
            "minecraft:splash_potion",
            Map.of(
                "potion_instant_damage", "6",
                "potion_splash_radius", "4.0",
                "potion_source_key", "minecraft:indirect_magic",
                "scales_with_difficulty", "true"
            )
        );

        ThreatEvent event = predictor.predict(movingContext(potion)).stream()
            .filter(candidate -> candidate.id().equals("projectile:splash:splash_magic"))
            .findFirst()
            .orElseThrow();

        assertTrue(event.damage().scalesWithDifficulty());
    }

    @Test
    void mobOwnedLingeringInstantDamageRetainsDifficultyScaling() {
        WorldSnapshot.EntitySnapshot potion = movingProjectile(
            "lingering",
            "minecraft:lingering_potion",
            Map.of(
                "potion_instant_damage", "6",
                "potion_lingering", "true",
                "potion_source_key", "minecraft:indirect_magic",
                "scales_with_difficulty", "true"
            )
        );

        ThreatEvent event = predictor.predict(movingContext(potion)).stream()
            .filter(candidate -> candidate.id().equals("projectile:lingering:lingering_cloud:0"))
            .findFirst()
            .orElseThrow();

        assertTrue(event.damage().scalesWithDifficulty());
    }

    @Test
    void mobOwnedDragonBreathCloudRetainsDifficultyScaling() {
        WorldSnapshot.EntitySnapshot fireball = movingProjectile(
            "dragon",
            "minecraft:dragon_fireball",
            Map.of(
                "acceleration_power", "0",
                "scales_with_difficulty", "true"
            )
        );

        ThreatEvent event = predictor.predict(movingContext(fireball)).stream()
            .filter(candidate -> candidate.id().contains(":dragon_breath:"))
            .findFirst()
            .orElseThrow();

        assertTrue(event.damage().scalesWithDifficulty());
    }

    @Test
    void mobOwnedFireworkExplosionRetainsDifficultyScaling() {
        WorldSnapshot.EntitySnapshot firework = new WorldSnapshot.EntitySnapshot(
            "firework",
            "minecraft:firework_rocket",
            new Vec3Snapshot(4d, 1d, 0d),
            new Vec3Snapshot(0d, 0d, 0d),
            new AabbSnapshot(3.9d, 0.9d, -0.1d, 4.1d, 1.1d, 0.1d),
            Map.of(
                "life_ticks", "1",
                "lifetime_ticks", "0",
                "firework_explosions", "1",
                "source_key", "minecraft:fireworks",
                "scales_with_difficulty", "true"
            )
        );

        ThreatEvent event = predictor.predict(context(
            firework,
            new Vec3Snapshot(0d, 0d, 0d),
            new AabbSnapshot(-0.3d, 0d, -0.3d, 0.3d, 1.8d, 0.3d)
        )).stream()
            .filter(candidate -> candidate.id().equals("projectile:firework:firework"))
            .findFirst()
            .orElseThrow();

        assertTrue(event.damage().scalesWithDifficulty());
    }

    private static WorldSnapshot.EntitySnapshot movingProjectile(
        String id,
        String type,
        Map<String, String> properties
    ) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            type,
            new Vec3Snapshot(0d, 1d, 0.3d),
            new Vec3Snapshot(1.5d, 0d, 0d),
            new AabbSnapshot(-0.25d, 0.75d, 0.05d, 0.25d, 1.25d, 0.55d),
            properties
        );
    }

    private static PredictionContext movingContext(WorldSnapshot.EntitySnapshot entity) {
        return context(
            entity,
            new Vec3Snapshot(6.7d, 0d, 0d),
            new AabbSnapshot(6.7d, 0d, 0d, 7.3d, 1.8d, 0.6d)
        );
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot playerPosition,
        AabbSnapshot playerBox
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.HARD,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            playerBox,
            playerPosition,
            new Vec3Snapshot(0d, 0d, 0d),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of()),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            new EngineLimits(128, 32, 80, 256)
        );
    }
}
