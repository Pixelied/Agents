package dev.adrien.crystaloptimizer.v2.damage;

public record DamageProjection(
    float rawIncoming,
    float postMitigationIncoming,
    float absorptionLoss,
    float healthLoss,
    float postHitEffectiveHealth,
    float nextHurtThreshold,
    boolean totemTriggered
) {
    private static final float EPSILON = 0.0001f;

    public DamageProjection {
        requireNonNegativeFinite(rawIncoming, "rawIncoming");
        requireNonNegativeFinite(postMitigationIncoming, "postMitigationIncoming");
        requireNonNegativeFinite(absorptionLoss, "absorptionLoss");
        requireNonNegativeFinite(healthLoss, "healthLoss");
        requireNonNegativeFinite(postHitEffectiveHealth, "postHitEffectiveHealth");
        requireNonNegativeFinite(nextHurtThreshold, "nextHurtThreshold");
        if (absorptionLoss + healthLoss > postMitigationIncoming + EPSILON) {
            throw new IllegalArgumentException(
                "effective total loss cannot exceed pre-hurt-window incoming damage"
            );
        }
    }

    public float effectiveTotalLoss() {
        return absorptionLoss + healthLoss;
    }

    private static void requireNonNegativeFinite(float value, String name) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
