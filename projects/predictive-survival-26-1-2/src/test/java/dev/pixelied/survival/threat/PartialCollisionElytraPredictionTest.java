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

import static org.junit.jupiter.api.Assertions.assertEquals;

class PartialCollisionElytraPredictionTest {
    @Test
    void collidablePartialBlockCanProduceFlyIntoWallThreat() {
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
            new AabbSnapshot(0.2, 1, 0.2, 0.8, 2.8, 0.8),
            new Vec3Snapshot(0.2, 1, 0.2),
            new Vec3Snapshot(1, 0, 0),
            Map.of(),
            Map.of("fall_flying", "true", "world_min_y", "-64")
        );
        WorldSnapshot.BlockSnapshot fenceLike = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 1.5, 0.5),
            "minecraft:oak_fence",
            true,
            Map.of(
                "full_collision_cube", "false",
                "collision_min_x", "0.375",
                "collision_min_y", "0.0",
                "collision_min_z", "0.375",
                "collision_max_x", "0.625",
                "collision_max_y", "1.5",
                "collision_max_z", "0.625"
            )
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of(fenceLike)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );

        ThreatEvent event = new FallPredictor().predict(context).stream()
            .filter(threat -> threat.id().equals("fall:elytra_wall"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("collidable partial block must not be invisible to Elytra wall prediction"));

        assertEquals("minecraft:fly_into_wall", event.damage().sourceKey());
    }
}
