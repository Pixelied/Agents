package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.List;

/**
 * Compatibility bridge for projectile collision code. Exact VoxelShape components are authoritative
 * when captured; legacy snapshots retain their conservative envelope/full-cube fallback.
 */
final class ProjectileCollisionCandidates {
    private ProjectileCollisionCandidates() {
    }

    static List<AabbSnapshot> forBlock(WorldSnapshot.BlockSnapshot block, int x, int y, int z) {
        return ProjectileCollisionBounds.resolve(block, x, y, z);
    }
}
