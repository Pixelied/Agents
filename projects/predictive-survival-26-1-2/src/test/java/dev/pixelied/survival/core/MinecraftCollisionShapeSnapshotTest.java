package dev.pixelied.survival.core;

import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinecraftCollisionShapeSnapshotTest {
    @Test
    void partialVoxelShapeBoundsAreSerializedExactly() {
        Map<String, String> properties = new LinkedHashMap<>();

        MinecraftCollisionShapeSnapshot.write(
            properties,
            Shapes.box(0d, 0d, 0d, 1d, 0.5d, 1d),
            false
        );

        assertEquals("false", properties.get("full_collision_cube"));
        assertEquals("0.0", properties.get("collision_min_x"));
        assertEquals("0.0", properties.get("collision_min_y"));
        assertEquals("0.0", properties.get("collision_min_z"));
        assertEquals("1.0", properties.get("collision_max_x"));
        assertEquals("0.5", properties.get("collision_max_y"));
        assertEquals("1.0", properties.get("collision_max_z"));
    }

    @Test
    void emptyShapeOnlyRecordsNonFullCollisionMarker() {
        Map<String, String> properties = new LinkedHashMap<>();

        MinecraftCollisionShapeSnapshot.write(properties, Shapes.empty(), false);

        assertEquals(Map.of("full_collision_cube", "false"), properties);
    }
}
