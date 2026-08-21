package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.Objects;
import java.util.UUID;

public record TargetPreScore(
    UUID targetId,
    double distanceSquared,
    float effectiveHealthUpperBound,
    float cheapDamageUpperBound,
    boolean recentAttacker,
    boolean sticky
) {
    public TargetPreScore {
        Objects.requireNonNull(targetId, "targetId");
        if (!Double.isFinite(distanceSquared) || distanceSquared < 0.0) {
            throw new IllegalArgumentException("distanceSquared must be finite and non-negative");
        }
        if (!Float.isFinite(effectiveHealthUpperBound) || effectiveHealthUpperBound < 0.0f) {
            throw new IllegalArgumentException("effectiveHealthUpperBound must be finite and non-negative");
        }
        if (!Float.isFinite(cheapDamageUpperBound) || cheapDamageUpperBound < 0.0f) {
            throw new IllegalArgumentException("cheapDamageUpperBound must be finite and non-negative");
        }
    }

    public boolean cheapCouldFinish() {
        return cheapDamageUpperBound >= effectiveHealthUpperBound;
    }
}
