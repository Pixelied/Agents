package dev.pixelied.survival.core;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Map;
import java.util.Objects;

/** Serializes the conservative AABB envelope of a vanilla block collision shape. */
final class MinecraftCollisionShapeSnapshot {
    private MinecraftCollisionShapeSnapshot() {
    }

    static void write(Map<String, String> properties, VoxelShape shape, boolean fullCollisionCube) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(shape, "shape");
        properties.put("full_collision_cube", Boolean.toString(fullCollisionCube));
        if (shape.isEmpty()) return;

        AABB bounds = shape.bounds();
        properties.put("collision_min_x", Double.toString(bounds.minX));
        properties.put("collision_min_y", Double.toString(bounds.minY));
        properties.put("collision_min_z", Double.toString(bounds.minZ));
        properties.put("collision_max_x", Double.toString(bounds.maxX));
        properties.put("collision_max_y", Double.toString(bounds.maxY));
        properties.put("collision_max_z", Double.toString(bounds.maxZ));
    }
}
