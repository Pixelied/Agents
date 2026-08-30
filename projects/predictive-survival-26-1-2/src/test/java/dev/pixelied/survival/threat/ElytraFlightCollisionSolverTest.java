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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraFlightCollisionSolverTest {
    private final FallPredictor predictor = new FallPredictor();

    @Test
    void equalSpeedHeadOnWallDamagesButGlancingWallPreservesTangentialSpeed() {
        ThreatEvent headOn = wallThreat(context(
            new Vec3Snapshot(1.0, 0.0, 0.0),
            new Vec3Snapshot(1.0, 0.0, 0.0),
            wallAt(1, 1, 0)
        )).orElseThrow();

        double diagonal = Math.sqrt(0.5d);
        Optional<ThreatEvent> glancing = wallThreat(context(
            new Vec3Snapshot(diagonal, 0.0, diagonal),
            new Vec3Snapshot(diagonal, 0.0, diagonal),
            wallAt(1, 1, 0)
        ));

        assertTrue(headOn.damage().rawDamage().min() > 0f,
            "a head-on one-block wall impact at this speed must lose enough horizontal speed to damage");
        assertTrue(glancing.isEmpty(),
            "the equal-speed glancing impact preserves its tangential component, so vanilla speed-loss damage stays non-positive");
    }

    @Test
    void steepGroundOnlyCollisionIsNotFlyIntoWallDamage() {
        PredictionContext groundDive = context(
            new Vec3Snapshot(0.5, -0.9, 0.0),
            new Vec3Snapshot(0.5, -0.9, 0.0),
            fullBlockAt(0, 0, 0),
            new Vec3Snapshot(0.2, 1.5, 0.2),
            new AabbSnapshot(0.2, 1.5, 0.2, 0.8, 3.3, 0.8)
        );

        assertTrue(wallThreat(groundDive).isEmpty(),
            "vertical ground contact without an X/Z collision must never emit minecraft:fly_into_wall");
    }

    private Optional<ThreatEvent> wallThreat(PredictionContext context) {
        return predictor.predict(context).stream()
            .filter(event -> "minecraft:fly_into_wall".equals(event.damage().sourceKey()))
            .findFirst();
    }

    private static PredictionContext context(
        Vec3Snapshot velocity,
        Vec3Snapshot look,
        WorldSnapshot.BlockSnapshot block
    ) {
        return context(
            velocity,
            look,
            block,
            new Vec3Snapshot(0.2, 1.0, 0.2),
            new AabbSnapshot(0.2, 1.0, 0.2, 0.8, 2.8, 0.8)
        );
    }

    private static PredictionContext context(
        Vec3Snapshot velocity,
        Vec3Snapshot look,
        WorldSnapshot.BlockSnapshot block,
        Vec3Snapshot position,
        AabbSnapshot box
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
            box,
            position,
            velocity,
            Map.of(),
            Map.of(
                "fall_flying", "true",
                "base_gravity", "0.08",
                "elytra_pitch_degrees", "0.0",
                "elytra_look_x", Double.toString(look.x()),
                "elytra_look_y", Double.toString(look.y()),
                "elytra_look_z", Double.toString(look.z()),
                "world_min_y", "-64"
            )
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of(block)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.BlockSnapshot wallAt(int x, int y, int z) {
        return fullBlockAt(x, y, z);
    }

    private static WorldSnapshot.BlockSnapshot fullBlockAt(int x, int y, int z) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x, y, z),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
    }
}
