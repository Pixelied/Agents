package dev.adrien.crystaloptimizer.planner;

import java.util.Objects;
import java.util.UUID;

public record TargetPriority(
    UUID targetId,
    double killOpportunity,
    double threat,
    double distance
) {
    public TargetPriority {
        Objects.requireNonNull(targetId, "targetId");
        requireUnit(killOpportunity, "killOpportunity");
        requireUnit(threat, "threat");
        if (!Double.isFinite(distance) || distance < 0.0) {
            throw new IllegalArgumentException("distance must be non-negative and finite");
        }
    }

    private static void requireUnit(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
