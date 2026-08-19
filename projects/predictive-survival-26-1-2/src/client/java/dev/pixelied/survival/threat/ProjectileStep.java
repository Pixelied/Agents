package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

public record ProjectileStep(Vec3Snapshot position, Vec3Snapshot velocity, long tick) {
    public ProjectileStep {
        position = Objects.requireNonNull(position, "position");
        velocity = Objects.requireNonNull(velocity, "velocity");
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
    }
}
