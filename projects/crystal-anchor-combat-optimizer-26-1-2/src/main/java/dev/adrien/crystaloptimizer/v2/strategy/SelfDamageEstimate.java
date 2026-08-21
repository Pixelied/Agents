package dev.adrien.crystaloptimizer.v2.strategy;

public record SelfDamageEstimate(
    float worstCaseDamage,
    float worstCaseRemainingHealth,
    boolean totemTriggered
) {
    public SelfDamageEstimate {
        if (!Float.isFinite(worstCaseDamage) || worstCaseDamage < 0.0f) {
            throw new IllegalArgumentException("worstCaseDamage must be finite and non-negative");
        }
        if (!Float.isFinite(worstCaseRemainingHealth) || worstCaseRemainingHealth < 0.0f) {
            throw new IllegalArgumentException("worstCaseRemainingHealth must be finite and non-negative");
        }
    }

    public static SelfDamageEstimate legacy(float worstCaseDamage) {
        return new SelfDamageEstimate(worstCaseDamage, Float.MAX_VALUE, false);
    }
}
