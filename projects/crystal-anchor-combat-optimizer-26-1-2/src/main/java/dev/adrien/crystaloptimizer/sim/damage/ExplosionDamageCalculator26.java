package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.world.BlockView;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ExplosionDamageCalculator26 {
    public static float incoming(
        ExplosionContext context,
        AABB targetBox,
        Vec3 targetPosition,
        BlockView blocks
    ) {
        float doubleRadius = context.radius() * 2.0f;
        double dist = Math.sqrt(targetPosition.distanceToSqr(context.center())) / doubleRadius;
        if (dist > 1.0) {
            return 0.0f;
        }

        float exposure = ExplosionExposure.seenPercent(context.center(), targetBox, blocks);
        double power = (1.0 - dist) * exposure;
        return (float) (((power * power + power) / 2.0) * 7.0 * doubleRadius + 1.0);
    }

    public ExplosionDamageCalculator26() {
    }
}
