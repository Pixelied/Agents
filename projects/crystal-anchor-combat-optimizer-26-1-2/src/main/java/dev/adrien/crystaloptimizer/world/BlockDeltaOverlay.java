package dev.adrien.crystaloptimizer.world;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class BlockDeltaOverlay implements BlockView {
    private final BlockView base;
    private final Map<BlockPos, BlockState> stateDeltas;
    private final Map<BlockPos, VoxelShape> shapeDeltas;

    public BlockDeltaOverlay(BlockView base) {
        this(base, Map.of(), Map.of());
    }

    private BlockDeltaOverlay(
        BlockView base,
        Map<BlockPos, BlockState> stateDeltas,
        Map<BlockPos, VoxelShape> shapeDeltas
    ) {
        this.base = Objects.requireNonNull(base, "base");
        this.stateDeltas = immutablePosMap(stateDeltas);
        this.shapeDeltas = immutablePosMap(shapeDeltas);
    }

    public BlockDeltaOverlay withRemoved(BlockPos pos) {
        return withPlaced(pos, Blocks.AIR.defaultBlockState(), Shapes.empty());
    }

    public BlockDeltaOverlay withPlaced(BlockPos pos, BlockState state) {
        BlockPos immutable = pos.immutable();
        VoxelShape shape = state.isAir()
            ? Shapes.empty()
            : state.getCollisionShape(EmptyBlockGetter.INSTANCE, immutable);
        return withPlaced(immutable, state, shape);
    }

    public BlockDeltaOverlay withPlaced(BlockPos pos, BlockState state, VoxelShape collisionShape) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(collisionShape, "collisionShape");
        BlockPos immutable = pos.immutable();
        LinkedHashMap<BlockPos, BlockState> nextStates = new LinkedHashMap<>(stateDeltas);
        nextStates.put(immutable, state);
        LinkedHashMap<BlockPos, VoxelShape> nextShapes = new LinkedHashMap<>(shapeDeltas);
        nextShapes.put(immutable, collisionShape);
        return new BlockDeltaOverlay(base, nextStates, nextShapes);
    }

    public BlockState getBlockState(BlockPos pos) {
        BlockState delta = stateDeltas.get(pos);
        return delta != null ? delta : base.blockState(pos);
    }

    @Override
    public BlockState blockState(BlockPos pos) {
        return getBlockState(pos);
    }

    @Override
    public VoxelShape collisionShape(BlockPos pos) {
        VoxelShape delta = shapeDeltas.get(pos);
        return delta != null ? delta : base.collisionShape(pos);
    }

    public Map<BlockPos, BlockState> stateDeltas() {
        return stateDeltas;
    }

    private static <V> Map<BlockPos, V> immutablePosMap(Map<BlockPos, V> source) {
        LinkedHashMap<BlockPos, V> copy = new LinkedHashMap<>();
        source.forEach((pos, value) -> copy.put(pos.immutable(), Objects.requireNonNull(value)));
        return Map.copyOf(copy);
    }
}
