package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionCompoundShapeExposureTest {
    @Test
    void rayThroughGapBetweenTwoCollisionComponentsRemainsVisible() {
        WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:test_split",
            true,
            List.of(
                new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 0.25, 1.0),
                new AabbSnapshot(1.0, 0.75, 0.0, 2.0, 1.0, 1.0)
            ),
            Map.of("collision_min_y", "0", "collision_max_y", "1")
        );
        SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(split));

        assertFalse(view.blocksExplosionRay(
            new Vec3Snapshot(0.5, 0.5, 0.5),
            new Vec3Snapshot(2.5, 0.5, 0.5)
        ));
    }

    @Test
    void rayThroughExactCollisionComponentIsBlocked() {
        WorldSnapshot.BlockSnapshot split = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:test_split",
            true,
            List.of(
                new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 0.25, 1.0),
                new AabbSnapshot(1.0, 0.75, 0.0, 2.0, 1.0, 1.0)
            ),
            Map.of()
        );
        SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(split));

        assertTrue(view.blocksExplosionRay(
            new Vec3Snapshot(0.5, 0.1, 0.5),
            new Vec3Snapshot(2.5, 0.1, 0.5)
        ));
    }
}
