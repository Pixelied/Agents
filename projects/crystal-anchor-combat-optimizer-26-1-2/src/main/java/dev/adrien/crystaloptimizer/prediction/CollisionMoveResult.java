package dev.adrien.crystaloptimizer.prediction;

import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record CollisionMoveResult(
    Vec3 requestedDelta,
    Vec3 resolvedDelta,
    AABB box,
    boolean collidedX,
    boolean collidedY,
    boolean collidedZ
) {
    public CollisionMoveResult {
        Objects.requireNonNull(requestedDelta, "requestedDelta");
        Objects.requireNonNull(resolvedDelta, "resolvedDelta");
        Objects.requireNonNull(box, "box");
    }
}
