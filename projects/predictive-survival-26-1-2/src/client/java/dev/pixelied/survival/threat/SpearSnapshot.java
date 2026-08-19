package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.DamageRange;

import java.util.Optional;

public record SpearSnapshot(
    float baseMobDamage,
    float damageMultiplier,
    int maxDurationTicks,
    float minSpeed,
    float minRelativeSpeed,
    int ticksUsed,
    double attackerSpeedProjection,
    double targetSpeedProjection,
    float actionFactor
) {
    public SpearSnapshot {
        if (!Float.isFinite(baseMobDamage) || baseMobDamage < 0f) {
            throw new IllegalArgumentException("baseMobDamage must be finite and non-negative");
        }
        if (!Float.isFinite(damageMultiplier) || damageMultiplier < 0f) {
            throw new IllegalArgumentException("damageMultiplier must be finite and non-negative");
        }
        if (maxDurationTicks < 0 || ticksUsed < 0) {
            throw new IllegalArgumentException("spear use ticks must be non-negative");
        }
        if (!Float.isFinite(minSpeed) || minSpeed < 0f
            || !Float.isFinite(minRelativeSpeed) || minRelativeSpeed < 0f) {
            throw new IllegalArgumentException("speed thresholds must be finite and non-negative");
        }
        if (!Double.isFinite(attackerSpeedProjection) || !Double.isFinite(targetSpeedProjection)) {
            throw new IllegalArgumentException("speed projections must be finite");
        }
        if (!Float.isFinite(actionFactor) || actionFactor <= 0f) {
            throw new IllegalArgumentException("actionFactor must be finite and positive");
        }
    }

    public double relativeSpeed() {
        return Math.max(0d, attackerSpeedProjection - targetSpeedProjection);
    }

    public boolean damageConditionMet() {
        return ticksUsed <= maxDurationTicks
            && attackerSpeedProjection >= minSpeed * actionFactor
            && relativeSpeed() >= minRelativeSpeed * actionFactor;
    }

    public Optional<DamageRange> rawDamage() {
        if (!damageConditionMet()) return Optional.empty();
        double kinetic = Math.floor(relativeSpeed() * damageMultiplier);
        double total = baseMobDamage + kinetic;
        if (!Double.isFinite(total) || total >= Float.MAX_VALUE) {
            return Optional.of(DamageRange.exact(Float.MAX_VALUE));
        }
        return Optional.of(DamageRange.exact((float) Math.max(0d, total)));
    }
}
