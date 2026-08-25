package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectileCollisionBoundsTest {
    @Test
    void exactComponentsTakePriorityOverLegacyEnvelope() {
        List<AabbSnapshot> exact = List.of(
            new AabbSnapshot(1, 0, 0, 2, 0.25, 1),
            new AabbSnapshot(1, 0.75, 0, 2, 1, 1)
        );
        WorldSnapshot.BlockSnapshot block = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5), "minecraft:test_split", true, exact,
            Map.of(
                "full_collision_cube", "false",
                "collision_min_x", "0", "collision_min_y", "0", "collision_min_z", "0",
                "collision_max_x", "1", "collision_max_y", "1", "collision_max_z", "1"
            )
        );

        assertEquals(exact, ProjectileCollisionBounds.resolve(block, 1, 0, 0));
    }
}
