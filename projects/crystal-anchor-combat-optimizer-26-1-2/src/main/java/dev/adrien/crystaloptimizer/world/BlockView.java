package dev.adrien.crystaloptimizer.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface BlockView {
    BlockState blockState(BlockPos pos);

    VoxelShape collisionShape(BlockPos pos);

    static BlockView allAir() {
        return new BlockView() {
            @Override
            public BlockState blockState(BlockPos pos) {
                return Blocks.AIR.defaultBlockState();
            }

            @Override
            public VoxelShape collisionShape(BlockPos pos) {
                return Shapes.empty();
            }
        };
    }

    static BlockView singleBlock(BlockPos occupiedPos, BlockState occupiedState) {
        BlockPos immutablePos = occupiedPos.immutable();
        return new BlockView() {
            @Override
            public BlockState blockState(BlockPos pos) {
                return immutablePos.equals(pos) ? occupiedState : Blocks.AIR.defaultBlockState();
            }

            @Override
            public VoxelShape collisionShape(BlockPos pos) {
                if (!immutablePos.equals(pos)) {
                    return Shapes.empty();
                }
                return occupiedState.getCollisionShape(EmptyBlockGetter.INSTANCE, immutablePos);
            }
        };
    }
}
