package dev.adrien.crystaloptimizer.sim.damage;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public record ExplosionContext(
    ExplosionKind kind,
    Vec3 center,
    Optional<BlockPos> sourceBlock,
    boolean anchorInWater
) {
    public ExplosionContext {
        sourceBlock = sourceBlock == null ? Optional.empty() : sourceBlock;
    }

    public static ExplosionContext crystal(Vec3 center) {
        return new ExplosionContext(ExplosionKind.CRYSTAL, center, Optional.empty(), false);
    }

    public static ExplosionContext anchor(BlockPos sourceBlock, boolean inWater) {
        return new ExplosionContext(ExplosionKind.ANCHOR, sourceBlock.getCenter(), Optional.of(sourceBlock.immutable()), inWater);
    }

    public float radius() {
        return kind.radius();
    }

    public Optional<Float> blockResistanceOverride(BlockPos pos) {
        if (kind == ExplosionKind.ANCHOR && anchorInWater && sourceBlock.filter(pos::equals).isPresent()) {
            return Optional.of(Blocks.WATER.getExplosionResistance());
        }
        return Optional.empty();
    }
}
