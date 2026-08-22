package dev.adrien.crystaloptimizer.v2.damage;

import java.util.Objects;
import java.util.Set;

public record DamageEstimate(
    float lowerBound,
    float expected,
    float upperBound,
    float healthLossLowerBound,
    float healthLossExpected,
    float healthLossUpperBound,
    float postMitigationLowerBound,
    float postMitigationExpected,
    float postMitigationUpperBound,
    double popProbability,
    double killProbability,
    double confidence,
    Set<DamageUncertainty> uncertainties,
    long geometryRevision,
    long combatRevision
) {
    public DamageEstimate {
        Objects.requireNonNull(uncertainties, "uncertainties");
        uncertainties = Set.copyOf(uncertainties);
        requireOrdered(lowerBound, expected, upperBound, "effective loss");
        requireOrdered(
            healthLossLowerBound,
            healthLossExpected,
            healthLossUpperBound,
            "health loss"
        );
        requireOrdered(
            postMitigationLowerBound,
            postMitigationExpected,
            postMitigationUpperBound,
            "post-mitigation incoming"
        );
        requireProbability(popProbability, "popProbability");
        requireProbability(killProbability, "killProbability");
        requireProbability(confidence, "confidence");
    }

    public static DamageEstimate exact(float damage, long geometryRevision, long combatRevision) {
        if (!Float.isFinite(damage) || damage < 0.0f) {
            throw new IllegalArgumentException("damage must be finite and non-negative");
        }
        return new DamageEstimate(
            damage,
            damage,
            damage,
            damage,
            damage,
            damage,
            damage,
            damage,
            damage,
            0.0,
            0.0,
            1.0,
            Set.of(),
            geometryRevision,
            combatRevision
        );
    }

    public boolean exact() {
        return uncertainties.isEmpty()
            && Float.compare(lowerBound, expected) == 0
            && Float.compare(expected, upperBound) == 0
            && Float.compare(healthLossLowerBound, healthLossExpected) == 0
            && Float.compare(healthLossExpected, healthLossUpperBound) == 0
            && Float.compare(postMitigationLowerBound, postMitigationExpected) == 0
            && Float.compare(postMitigationExpected, postMitigationUpperBound) == 0;
    }

    private static void requireOrdered(float lower, float expected, float upper, String name) {
        if (!Float.isFinite(lower) || !Float.isFinite(expected) || !Float.isFinite(upper)) {
            throw new IllegalArgumentException(name + " bounds must be finite");
        }
        if (lower < 0.0f || lower > expected || expected > upper) {
            throw new IllegalArgumentException("unordered " + name + " bounds");
        }
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " outside [0,1]");
        }
    }
}
