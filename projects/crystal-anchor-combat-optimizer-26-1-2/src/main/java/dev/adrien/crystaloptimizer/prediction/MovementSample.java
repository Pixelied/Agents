package dev.adrien.crystaloptimizer.prediction;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record MovementSample(long timestampNanos, Vec3 position, Vec3 velocity) {
    public MovementSample {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        requireFinite(position, "position");
        requireFinite(velocity, "velocity");
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
