package dev.adrien.crystaloptimizer.v2.damage;

public record ObservedDamageResult(
    float healthLoss,
    boolean totemPopped,
    boolean targetDied,
    long combatRevision
) {
    public ObservedDamageResult {
        if (!Float.isFinite(healthLoss) || healthLoss < 0.0f) {
            throw new IllegalArgumentException("healthLoss must be finite and non-negative");
        }
        if (combatRevision < 0L) {
            throw new IllegalArgumentException("combatRevision must be non-negative");
        }
    }
}
