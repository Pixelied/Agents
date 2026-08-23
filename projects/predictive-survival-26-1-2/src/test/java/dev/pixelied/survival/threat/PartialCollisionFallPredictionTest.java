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

class PartialCollisionFallPredictionTest {
    @Test
    void slabCollisionSurfaceStillProducesLandingDamage() {
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
            new AabbSnapshot(0.2, 8, 0.2, 0.8, 9.8, 0.8),
            new Vec3Snapshot(0.2, 8, 0.2),
            new Vec3Snapshot(0, -1, 0),
            Map.of(),
            Map.of(
                "fall_distance", "6",
                "safe_fall_distance", "3",
                "fall_damage_multiplier", "1",
                "world_min_y", "-64",
                "base_gravity", "0.08",
                "vertical_friction", "0.98",
                "horizontal_friction", "0.91",
                "fall_flying", "false",
                "suppressing_bounce", "false"
            )
        );
        WorldSnapshot.BlockSnapshot slab = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(0.5, 0.5, 0.5),
            "minecraft:stone_slab",
            true,
            Map.of(
                "full_collision_cube", "false",
                "collision_min_x", "0.0",
                "collision_min_y", "0.0",
                "collision_min_z", "0.0",
                "collision_max_x", "1.0",
                "collision_max_y", "0.5",
                "collision_max_z", "1.0"
            )
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of(slab)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );

        LandingPrediction landing = new FallLandingSolver().solve(context)
            .orElseThrow(() -> new AssertionError("collidable partial-height surface must stop the projected fall"));

        assertEquals("minecraft:stone_slab", landing.surfaceBlockId());
        assertTrue(landing.rawFallDamage().max() > 0f);
    }
}
