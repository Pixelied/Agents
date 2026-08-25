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
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectileCompoundShapeCollisionTest {
    @Test
    void projectileCanPassThroughRealGapInCompoundCollisionShape() {
        WorldSnapshot.EntitySnapshot arrow = new WorldSnapshot.EntitySnapshot(
            "arrow:gap",
            "minecraft:arrow",
            new Vec3Snapshot(0, 1.5, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.375, 0.175, 0.125, 1.625, 0.425),
            Map.of(
                "base_damage", "6.0",
                "critical", "false",
                "no_gravity", "true"
            )
        );
        WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(4.5, 1.5, 0.5),
            "minecraft:test_split",
            true,
            List.of(
                new AabbSnapshot(4.0, 1.0, 0.0, 5.0, 1.25, 1.0),
                new AabbSnapshot(4.0, 1.75, 0.0, 5.0, 2.0, 1.0)
            ),
            Map.of(
                "collision_min_x", "0",
                "collision_min_y", "0",
                "collision_min_z", "0",
                "collision_max_x", "1",
                "collision_max_y", "1",
                "collision_max_z", "1"
            )
        );

        List<ThreatEvent> throughGap = new ProjectilePredictor().predict(context(arrow, List.of(split)));
        List<ThreatEvent> open = new ProjectilePredictor().predict(context(arrow, List.of()));

        assertFalse(open.isEmpty());
        assertTrue(
            throughGap.stream().anyMatch(event -> event.id().equals("projectile:arrow:gap:direct")),
            "exact collision components must not fill the real gap with their legacy envelope"
        );
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot arrow,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(arrow), blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
