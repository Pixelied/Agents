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

class ProjectileObservationAgeTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void staleBlockSplashBoundsDamageAcrossTheCollidedBlock() {
        WorldSnapshot.EntitySnapshot potion = new WorldSnapshot.EntitySnapshot(
            "splash:stale",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            Map.of(
                "potion_instant_damage", "12.0",
                "potion_splash_radius", "4.0",
                "potion_source_key", "minecraft:indirect_magic",
                "observation_age_ticks", "1"
            )
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );

        ThreatEvent event = predictor.predict(context(potion, wall)).getFirst();

        assertEquals(new DamageRange(0f, 10f), event.damage().rawDamage());
        assertEquals(Confidence.BOUNDED, event.confidence());
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        WorldSnapshot.BlockSnapshot block
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of(block)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
