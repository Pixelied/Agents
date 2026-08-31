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

class ElytraPitchedCollisionTimingTest {
    @Test
    void fiveBlockWallWithPitchedPointNineSpeedCollidesOnTickFive() {
        double pitch = Math.toRadians(20d);
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
            new AabbSnapshot(0.2d, 230d, 0.2d, 0.8d, 231.8d, 0.8d),
            new Vec3Snapshot(0.5d, 230d, 0.5d),
            new Vec3Snapshot(0.9d, 0d, 0d),
            Map.of(),
            Map.of(
                "fall_flying", "true",
                "base_gravity", "0.08",
                "elytra_pitch_degrees", "20.0",
                "elytra_look_x", Double.toString(Math.cos(pitch)),
                "elytra_look_y", Double.toString(-Math.sin(pitch)),
                "elytra_look_z", "0.0"
            )
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5.5d, 230.5d, 0.5d),
            "minecraft:obsidian",
            true,
            List.of(new AabbSnapshot(5d, 225d, -2d, 6d, 236d, 3d)),
            Map.of("full_collision_cube", "true")
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of(wall)),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );

        ElytraFlightCollisionSolver.CollisionPrediction collision = new ElytraFlightCollisionSolver()
            .solve(context)
            .orElseThrow();

        assertEquals(5L, collision.tick());
    }
}
