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

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPredictorExactCollisionComponentsTest {
    @Test
    void predictorUsesCapturedComponentsEvenWhenBlockIsNotMarkedFullCube() {
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:exact-components",
            "minecraft:tnt",
            new Vec3Snapshot(3.5, 0.5, 0.5),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(3.25, 0.25, 0.25, 3.75, 0.75, 0.75),
            Map.of("explosion_radius", "4.0", "fuse_ticks", "1")
        );
        WorldSnapshot.BlockSnapshot exactOccluder = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:test_exact_occluder",
            true,
            List.of(new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)),
            Map.of("full_collision_cube", "false")
        );

        float open = new ExplosionPredictor().predict(context(tnt, List.of())).getFirst()
            .damage().rawDamage().max();
        float occluded = new ExplosionPredictor().predict(context(tnt, List.of(exactOccluder))).getFirst()
            .damage().rawDamage().max();

        assertTrue(occluded < open, "exact captured collision components must reduce explosion exposure");
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        Vec3Snapshot position = new Vec3Snapshot(0.3, 0, 0.5);
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.2, 0.6, 1.8, 0.8),
            position, new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
