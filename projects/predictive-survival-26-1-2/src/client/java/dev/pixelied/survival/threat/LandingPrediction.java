package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

public record LandingPrediction(
    Vec3Snapshot position,
    long tick,
    String surfaceBlockId,
    DamageRange rawFallDamage
) {
    public LandingPrediction {
        position = Objects.requireNonNull(position, "position");
        surfaceBlockId = Objects.requireNonNull(surfaceBlockId, "surfaceBlockId");
        rawFallDamage = Objects.requireNonNull(rawFallDamage, "rawFallDamage");
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
    }
}
