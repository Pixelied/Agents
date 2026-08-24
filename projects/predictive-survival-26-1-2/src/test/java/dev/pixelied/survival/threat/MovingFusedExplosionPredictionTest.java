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
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovingFusedExplosionPredictionTest {
    @Test
    void movingTntUsesDetonationMotionEnvelopeInsteadOfCurrentPositionOnly() {
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:incoming",
            "minecraft:tnt",
            new Vec3Snapshot(6.3, 0.9, 0.3),
            new Vec3Snapshot(-1.5, 0, 0),
            new AabbSnapshot(5.8, 0.4, -0.2, 6.8, 1.4, 0.8),
            Map.of(
                "explosion_radius", "4.0",
                "fuse_ticks", "3",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );

        var event = new ExplosionPredictor().predict(context(tnt)).stream()
            .filter(candidate -> candidate.id().equals("explosion:tnt:incoming"))
            .findFirst()
            .orElseThrow();

        assertEquals(new TickWindow(3, 3), event.impact());
        assertTrue(
            event.damage().rawDamage().max() > 20f,
            "TNT moving toward the player must be bounded at its detonation geometry, not only its current position"
        );
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot tnt) {
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
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(tnt), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
