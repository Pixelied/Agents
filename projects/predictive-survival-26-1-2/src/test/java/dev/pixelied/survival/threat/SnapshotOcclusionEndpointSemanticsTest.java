package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotOcclusionEndpointSemanticsTest {
    @Test
    void rayEndingExactlyOnCollisionSurfaceIsNotBlockedLikeVanillaClip() {
        WorldSnapshot.BlockSnapshot support = block(
            new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)
        );
        SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(support));

        assertFalse(view.blocksExplosionRay(
            new Vec3Snapshot(0.5, 1.5, 0.5),
            new Vec3Snapshot(1.5, 1.0, 0.5)
        ));
    }

    @Test
    void rayEnteringCollisionBeforeItsDestinationStillBlocks() {
        WorldSnapshot.BlockSnapshot support = block(
            new AabbSnapshot(1.0, 0.0, 0.0, 2.0, 1.0, 1.0)
        );
        SnapshotOcclusionView view = new SnapshotOcclusionView(List.of(support));

        assertTrue(view.blocksExplosionRay(
            new Vec3Snapshot(0.5, 0.5, 0.5),
            new Vec3Snapshot(2.5, 0.5, 0.5)
        ));
    }

    private static WorldSnapshot.BlockSnapshot block(AabbSnapshot box) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:obsidian",
            true,
            List.of(box),
            Map.of("full_collision_cube", "true")
        );
    }
}
