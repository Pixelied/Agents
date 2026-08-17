package dev.adrien.crystaloptimizer.sim.model;

import net.minecraft.world.phys.Vec3;

public record BlockingState(
    boolean active,
    Vec3 position,
    float headYawDegrees,
    float horizontalBlockingAngle,
    float baseReduction,
    float factorReduction
) {
    public static BlockingState none() {
        return new BlockingState(false, Vec3.ZERO, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    public static BlockingState shield(Vec3 position, float headYawDegrees) {
        return new BlockingState(true, position, headYawDegrees, 90.0f, 0.0f, 1.0f);
    }
}
