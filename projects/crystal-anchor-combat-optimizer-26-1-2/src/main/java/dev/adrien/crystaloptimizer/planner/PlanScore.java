package dev.adrien.crystaloptimizer.planner;

public record PlanScore(
    boolean unacceptableSelfDeath,
    double targetDeathProbability,
    double totemDenialProbability,
    int ticksToKill,
    double threatNeutralization,
    double robustness,
    int feedbackBoundaries,
    double selfRisk,
    double futureGeometry,
    double resourceCost
) implements Comparable<PlanScore> {
    public PlanScore {
        requireProbability(targetDeathProbability, "targetDeathProbability");
        requireProbability(totemDenialProbability, "totemDenialProbability");
        requireProbability(threatNeutralization, "threatNeutralization");
        requireProbability(robustness, "robustness");
        requireProbability(selfRisk, "selfRisk");
        if (ticksToKill < 0) {
            throw new IllegalArgumentException("ticksToKill must be non-negative");
        }
        if (feedbackBoundaries < 0) {
            throw new IllegalArgumentException("feedbackBoundaries must be non-negative");
        }
        if (!Double.isFinite(futureGeometry) || !Double.isFinite(resourceCost) || resourceCost < 0.0) {
            throw new IllegalArgumentException("futureGeometry must be finite and resourceCost non-negative finite");
        }
    }

    public static PlanScore root(double futureGeometry) {
        return new PlanScore(false, 0.0, 0.0, Integer.MAX_VALUE, 0.0, 1.0, 0, 0.0, futureGeometry, 0.0);
    }

    @Override
    public int compareTo(PlanScore other) {
        int result = Boolean.compare(other.unacceptableSelfDeath, unacceptableSelfDeath);
        if (result != 0) return result;
        result = Double.compare(targetDeathProbability, other.targetDeathProbability);
        if (result != 0) return result;
        result = Double.compare(totemDenialProbability, other.totemDenialProbability);
        if (result != 0) return result;
        result = Integer.compare(other.ticksToKill, ticksToKill);
        if (result != 0) return result;
        result = Double.compare(threatNeutralization, other.threatNeutralization);
        if (result != 0) return result;
        result = Double.compare(robustness, other.robustness);
        if (result != 0) return result;
        result = Integer.compare(other.feedbackBoundaries, feedbackBoundaries);
        if (result != 0) return result;
        result = Double.compare(other.selfRisk, selfRisk);
        if (result != 0) return result;
        result = Double.compare(futureGeometry, other.futureGeometry);
        if (result != 0) return result;
        return Double.compare(other.resourceCost, resourceCost);
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
