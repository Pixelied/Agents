package dev.adrien.crystaloptimizer.prediction;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public record MovementSample(long timestampNanos, Vec3 position, Vec3 velocity) {
    private static final double NANOS_PER_TICK = 50_000_000.0;

    public MovementSample {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        requireFinite(position, "position");
        requireFinite(velocity, "velocity");
    }

    public double ticksSince(MovementSample earlier) {
        Objects.requireNonNull(earlier, "earlier");
        if (timestampNanos < earlier.timestampNanos) {
            throw new IllegalArgumentException("earlier sample is newer than this sample");
        }
        return (timestampNanos - earlier.timestampNanos) / NANOS_PER_TICK;
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
