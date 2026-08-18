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

public final class CombatRegion implements BlockView {
    private final Map<BlockPos, BlockState> states;
    private final Map<BlockPos, VoxelShape> collisionShapes;

    private CombatRegion(Map<BlockPos, BlockState> states, Map<BlockPos, VoxelShape> collisionShapes) {
        this.states = immutablePosMap(states);
        this.collisionShapes = immutablePosMap(collisionShapes);
    }

    public static CombatRegion empty() {
        return new CombatRegion(Map.of(), Map.of());
    }

    public static CombatRegion singleBlock(BlockPos pos, BlockState state) {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(state, "state");
        BlockPos immutable = pos.immutable();
        VoxelShape shape = state.isAir()
            ? Shapes.empty()
            : state.getCollisionShape(EmptyBlockGetter.INSTANCE, immutable);
        return new CombatRegion(Map.of(immutable, state), Map.of(immutable, shape));
    }

    public static CombatRegion of(
        Map<BlockPos, BlockState> states,
        Map<BlockPos, VoxelShape> collisionShapes
    ) {
        Objects.requireNonNull(states, "states");
        Objects.requireNonNull(collisionShapes, "collisionShapes");
        return new CombatRegion(states, collisionShapes);
    }

    public BlockState getBlockState(BlockPos pos) {
        return states.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public BlockState blockState(BlockPos pos) {
        return getBlockState(pos);
    }

    @Override
    public VoxelShape collisionShape(BlockPos pos) {
        VoxelShape explicit = collisionShapes.get(pos);
        if (explicit != null) {
            return explicit;
        }
        BlockState state = getBlockState(pos);
        return state.isAir() ? Shapes.empty() : state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos);
    }

    public Map<BlockPos, BlockState> states() {
        return states;
    }

    private static <V> Map<BlockPos, V> immutablePosMap(Map<BlockPos, V> source) {
        LinkedHashMap<BlockPos, V> copy = new LinkedHashMap<>();
        source.forEach((pos, value) -> copy.put(pos.immutable(), Objects.requireNonNull(value)));
        return Map.copyOf(copy);
    }
}
