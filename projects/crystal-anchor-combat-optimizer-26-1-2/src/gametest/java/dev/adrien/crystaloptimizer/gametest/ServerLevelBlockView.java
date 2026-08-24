package dev.adrien.crystaloptimizer.gametest;

import dev.adrien.crystaloptimizer.world.BlockView;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public record ServerLevelBlockView(ServerLevel level) implements BlockView {
    public ServerLevelBlockView {
        Objects.requireNonNull(level, "level");
    }

    @Override
    public BlockState blockState(BlockPos pos) {
        return level.getBlockState(pos);
    }

    @Override
    public VoxelShape collisionShape(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos);
    }
}
