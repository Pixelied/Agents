package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.List;

/** Resolves exact captured block collision components with conservative legacy fallback. */
final class ProjectileCollisionBounds {
    private ProjectileCollisionBounds() {
    }

    static List<AabbSnapshot> resolve(WorldSnapshot.BlockSnapshot block, int x, int y, int z) {
        if (!block.collisionBoxes().isEmpty()) return block.collisionBoxes();

        AabbSnapshot fullCube = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
        if (Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"))) {
            return List.of(fullCube);
        }

        Double minX = finiteUnitBound(block.properties().get("collision_min_x"));
        Double minY = finiteUnitBound(block.properties().get("collision_min_y"));
        Double minZ = finiteUnitBound(block.properties().get("collision_min_z"));
        Double maxX = finiteUnitBound(block.properties().get("collision_max_x"));
        Double maxY = finiteUnitBound(block.properties().get("collision_max_y"));
        Double maxZ = finiteUnitBound(block.properties().get("collision_max_z"));
        if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null
            || maxX <= minX || maxY <= minY || maxZ <= minZ) {
            return List.of(fullCube);
        }
        return List.of(new AabbSnapshot(
            x + minX, y + minY, z + minZ,
            x + maxX, y + maxY, z + maxZ
        ));
    }

    private static Double finiteUnitBound(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0d && parsed <= 1d ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
