package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

/** Source-faithful metadata for one vanilla-style entity-damaging explosion. */
public record ExplosionSpec(
    Vec3Snapshot center,
    float radiusMin,
    float radiusMax,
    String sourceKey,
    boolean scalesWithDifficulty,
    boolean blockable
) {
    public ExplosionSpec {
        center = Objects.requireNonNull(center, "center");
        sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        if (!Float.isFinite(radiusMin) || !Float.isFinite(radiusMax)
            || radiusMin < 0f || radiusMax <= 0f || radiusMin > radiusMax) {
            throw new IllegalArgumentException("invalid explosion radius range");
        }
        if (sourceKey.isBlank()) throw new IllegalArgumentException("sourceKey must not be blank");
    }

    public boolean boundedRadius() {
        return Float.compare(radiusMin, radiusMax) != 0;
    }
}
