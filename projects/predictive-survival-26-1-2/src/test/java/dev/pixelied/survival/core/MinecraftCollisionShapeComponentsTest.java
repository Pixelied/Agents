package dev.pixelied.survival.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftCollisionShapeComponentsTest {
    @Test
    void capturesDisjointVoxelComponentsWithoutFillingTheirGap() {
        BlockPos pos = new BlockPos(4, 5, 6);
        var split = Shapes.or(
            Shapes.box(0, 0, 0, 1, 0.25, 1),
            Shapes.box(0, 0.75, 0, 1, 1, 1)
        );

        List<AabbSnapshot> components = MinecraftCollisionShapeSnapshot.capture(split, pos);

        assertEquals(2, components.size());
        assertEquals(5.25, components.getFirst().maxY(), 1.0E-9);
        assertEquals(5.75, components.get(1).minY(), 1.0E-9);
    }

    @Test
    void capturesRepresentativePartialCollisionShapesInWorldCoordinates() {
        BlockPos pos = new BlockPos(10, 20, 30);
        var slabShape = Shapes.box(0, 0, 0, 1, 0.5, 1);
        var stairShape = Shapes.or(
            Shapes.box(0, 0, 0, 1, 0.5, 1),
            Shapes.box(0, 0.5, 0.5, 1, 1, 1)
        );
        var fenceShape = Shapes.or(
            Shapes.box(0.375, 0, 0.375, 0.625, 1.5, 0.625),
            Shapes.box(0, 0.375, 0.4375, 1, 1.125, 0.5625)
        );
        var wallShape = Shapes.or(
            Shapes.box(0.25, 0, 0.25, 0.75, 1.5, 0.75),
            Shapes.box(0, 0, 0.3125, 1, 1.5, 0.6875)
        );
        var trapdoorShape = Shapes.box(0, 0, 0, 1, 0.1875, 1);

        var slab = MinecraftCollisionShapeSnapshot.capture(slabShape, pos);
        var stairs = MinecraftCollisionShapeSnapshot.capture(stairShape, pos);
        var fence = MinecraftCollisionShapeSnapshot.capture(fenceShape, pos);
        var wall = MinecraftCollisionShapeSnapshot.capture(wallShape, pos);
        var trapdoor = MinecraftCollisionShapeSnapshot.capture(trapdoorShape, pos);

        assertFalse(slab.isEmpty());
        assertEquals(20.5, slab.getFirst().maxY(), 1.0E-9);
        assertTrue(stairs.size() >= 2);
        assertFalse(fence.isEmpty());
        assertFalse(wall.isEmpty());
        assertFalse(trapdoor.isEmpty());
        assertTrue(stairs.stream().allMatch(box -> box.minX() >= 10 && box.maxX() <= 11));
        assertTrue(fence.stream().allMatch(box -> box.minZ() >= 30 && box.maxZ() <= 31));
        assertEquals(20.1875, trapdoor.getFirst().maxY(), 1.0E-9);
    }
}
