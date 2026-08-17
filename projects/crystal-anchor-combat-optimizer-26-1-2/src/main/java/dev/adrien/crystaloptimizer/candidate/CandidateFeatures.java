package dev.adrien.crystaloptimizer.candidate;

public record CandidateFeatures(
    double targetDistance,
    double selfDistance,
    double approximateExposure,
    boolean reachable,
    double rotationCostDegrees,
    boolean prepared,
    int feedbackBoundaries,
    int supportActions,
    double futureFollowupPotential,
    double targetDamage,
    double selfDamage
) {
    public CandidateFeatures {
        if (feedbackBoundaries < 0 || supportActions < 0) {
            throw new IllegalArgumentException("candidate counts must be non-negative");
        }
        if (!Double.isFinite(approximateExposure) || approximateExposure < 0.0 || approximateExposure > 1.0) {
            throw new IllegalArgumentException("approximateExposure must be in [0, 1]");
        }
        if (!Double.isFinite(rotationCostDegrees) || rotationCostDegrees < 0.0) {
            throw new IllegalArgumentException("rotationCostDegrees must be non-negative and finite");
        }
        if (!Double.isFinite(futureFollowupPotential) || futureFollowupPotential < 0.0) {
            throw new IllegalArgumentException("futureFollowupPotential must be non-negative and finite");
        }
        if (!Double.isFinite(targetDamage) || targetDamage < 0.0 || !Double.isFinite(selfDamage) || selfDamage < 0.0) {
            throw new IllegalArgumentException("damage estimates must be non-negative and finite");
        }
    }
}
