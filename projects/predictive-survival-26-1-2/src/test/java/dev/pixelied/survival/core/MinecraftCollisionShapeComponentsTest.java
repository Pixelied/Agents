package dev.pixelied.survival.core;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
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
    void capturesRepresentativeVanillaPartialCollisionShapes() {
        BlockPos pos = new BlockPos(10, 20, 30);

        var slab = MinecraftCollisionShapeSnapshot.capture(
            Blocks.OAK_SLAB.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, pos), pos
        );
        var stairs = MinecraftCollisionShapeSnapshot.capture(
            Blocks.OAK_STAIRS.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, pos), pos
        );
        var fence = MinecraftCollisionShapeSnapshot.capture(
            Blocks.OAK_FENCE.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, pos), pos
        );
        var wall = MinecraftCollisionShapeSnapshot.capture(
            Blocks.COBBLESTONE_WALL.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, pos), pos
        );
        var trapdoor = MinecraftCollisionShapeSnapshot.capture(
            Blocks.OAK_TRAPDOOR.defaultBlockState().getCollisionShape(EmptyBlockGetter.INSTANCE, pos), pos
        );

        assertFalse(slab.isEmpty());
        assertEquals(20.5, slab.getFirst().maxY(), 1.0E-9);
        assertFalse(stairs.isEmpty());
        assertFalse(fence.isEmpty());
        assertFalse(wall.isEmpty());
        assertFalse(trapdoor.isEmpty());
        assertTrue(stairs.stream().allMatch(box -> box.minX() >= 10 && box.maxX() <= 11));
        assertTrue(fence.stream().allMatch(box -> box.minZ() >= 30 && box.maxZ() <= 31));
    }
}
