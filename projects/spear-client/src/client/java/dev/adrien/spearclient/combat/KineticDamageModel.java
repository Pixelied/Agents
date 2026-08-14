package dev.adrien.spearclient.combat;

public final class KineticDamageModel {
    private KineticDamageModel() {}

    public static double relativeSpeed(
        double attackerForwardKnownMovement,
        double targetForwardKnownMovement
    ) {
        return Math.max(0.0, (attackerForwardKnownMovement - targetForwardKnownMovement) * 20.0);
    }

    public static double rawDamage(
        double baseAttackDamage,
        double damageMultiplier,
        double relativeSpeed
    ) {
        return baseAttackDamage + Math.floor(relativeSpeed * damageMultiplier);
    }

    public static double requiredKnownMovement(
        double targetRawDamage,
        double baseAttackDamage,
        double damageMultiplier
    ) {
        double requiredSpeed = Math.max(0.0, targetRawDamage - baseAttackDamage) / damageMultiplier;
        return requiredSpeed / 20.0;
    }
}
