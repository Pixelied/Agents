package dev.adrien.crystaloptimizer.world;

import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record LegalitySnapshot(
    Vec3 eyePosition,
    double blockInteractionRange,
    double entityInteractionRange,
    List<AABB> occupiedEntityBoxes,
    boolean respawnAnchorWorks
) {
    public LegalitySnapshot {
        Objects.requireNonNull(eyePosition, "eyePosition");
        Objects.requireNonNull(occupiedEntityBoxes, "occupiedEntityBoxes");
        if (!Double.isFinite(blockInteractionRange) || blockInteractionRange < 0.0) {
            throw new IllegalArgumentException("blockInteractionRange must be non-negative and finite");
        }
        if (!Double.isFinite(entityInteractionRange) || entityInteractionRange < 0.0) {
            throw new IllegalArgumentException("entityInteractionRange must be non-negative and finite");
        }
        occupiedEntityBoxes = List.copyOf(occupiedEntityBoxes);
    }

    public static LegalitySnapshot unavailable() {
        return new LegalitySnapshot(Vec3.ZERO, 0.0, 0.0, List.of(), true);
    }

    public boolean withinBlockReach(BlockPos pos) {
        return eyePosition.distanceToSqr(pos.getCenter()) <= blockInteractionRange * blockInteractionRange;
    }

    public boolean withinEntityReach(Vec3 position) {
        return eyePosition.distanceToSqr(position) <= entityInteractionRange * entityInteractionRange;
    }

    public boolean hasEntityIntersecting(AABB box) {
        return occupiedEntityBoxes.stream().anyMatch(box::intersects);
    }
}
