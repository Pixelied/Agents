package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.world.CombatRegion;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

final class V3PredictionFixtures {
    private static final long TICK_NANOS = 50_000_000L;

    static List<MovementSample> samplesMovingTowardWall() {
        return List.of(
            new MovementSample(0L, new Vec3(1.0, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0)),
            new MovementSample(TICK_NANOS, new Vec3(1.4, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0)),
            new MovementSample(TICK_NANOS * 2L, new Vec3(1.8, 64.0, 0.0), new Vec3(0.40, 0.0, 0.0))
        );
    }

    static CombatRegion geometryWithWallAtX(int x) {
        LinkedHashMap<BlockPos, BlockState> states = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, VoxelShape> shapes = new LinkedHashMap<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int y = 63; y <= 66; y++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                states.put(pos, stone);
                shapes.put(pos, stone.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
            }
        }
        return CombatRegion.of(states, shapes);
    }

    static CombatRegion geometryWithFloorAtY(int y) {
        LinkedHashMap<BlockPos, BlockState> states = new LinkedHashMap<>();
        LinkedHashMap<BlockPos, VoxelShape> shapes = new LinkedHashMap<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int x = 0; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = new BlockPos(x, y, z);
                states.put(pos, stone);
                shapes.put(pos, stone.getCollisionShape(EmptyBlockGetter.INSTANCE, pos));
            }
        }
        return CombatRegion.of(states, shapes);
    }

    static AABB currentBox() {
        return new AABB(1.5, 64.0, -0.3, 2.1, 65.8, 0.3);
    }

    static Vec3 currentPosition() {
        return new Vec3(1.8, 64.0, 0.0);
    }

    private V3PredictionFixtures() {
    }
}
