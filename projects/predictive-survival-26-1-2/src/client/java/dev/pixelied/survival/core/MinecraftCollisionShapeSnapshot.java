package dev.pixelied.survival.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Serializes vanilla block collision geometry for deterministic prediction. */
final class MinecraftCollisionShapeSnapshot {
    private MinecraftCollisionShapeSnapshot() {
    }

    static List<AabbSnapshot> capture(VoxelShape shape, BlockPos pos) {
        Objects.requireNonNull(shape, "shape");
        Objects.requireNonNull(pos, "pos");
        List<AabbSnapshot> boxes = new ArrayList<>();
        shape.forAllBoxes((minX, minY, minZ, maxX, maxY, maxZ) -> boxes.add(new AabbSnapshot(
            pos.getX() + minX,
            pos.getY() + minY,
            pos.getZ() + minZ,
            pos.getX() + maxX,
            pos.getY() + maxY,
            pos.getZ() + maxZ
        )));
        return List.copyOf(boxes);
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
