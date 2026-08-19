package dev.pixelied.survival.core;

public record EngineLimits(
    int maxThreats,
    int maxPlannerCandidates,
    int maxProjectileHorizonTicks,
    int maxDecisionHistory
) {
    public EngineLimits {
        if (maxThreats <= 0 || maxPlannerCandidates <= 0 || maxProjectileHorizonTicks <= 0 || maxDecisionHistory <= 0) {
            throw new IllegalArgumentException("all engine limits must be positive");
        }
    }

    public static EngineLimits defaults() {
        return new EngineLimits(128, 32, 80, 128);
    }
}
