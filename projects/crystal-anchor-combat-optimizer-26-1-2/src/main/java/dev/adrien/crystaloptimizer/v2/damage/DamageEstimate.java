package dev.adrien.crystaloptimizer.v2.damage;

import java.util.Objects;
import java.util.Set;

public record DamageEstimate(
    float lowerBound,
    float expected,
    float upperBound,
    double confidence,
    Set<DamageUncertainty> uncertainties,
    long geometryRevision,
    long combatRevision
) {
    public DamageEstimate {
        Objects.requireNonNull(uncertainties, "uncertainties");
        uncertainties = Set.copyOf(uncertainties);
        if (!Float.isFinite(lowerBound) || !Float.isFinite(expected) || !Float.isFinite(upperBound)) {
            throw new IllegalArgumentException("damage bounds must be finite");
        }
        if (lowerBound < 0.0f || lowerBound > expected || expected > upperBound) {
            throw new IllegalArgumentException("unordered damage bounds");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence outside [0,1]");
        }
    }

    public static DamageEstimate exact(float damage, long geometryRevision, long combatRevision) {
        return new DamageEstimate(
            damage,
            damage,
            damage,
            1.0,
            Set.of(),
            geometryRevision,
            combatRevision
        );
    }

    public boolean exact() {
        return uncertainties.isEmpty()
            && Float.compare(lowerBound, expected) == 0
            && Float.compare(expected, upperBound) == 0;
    }
}
