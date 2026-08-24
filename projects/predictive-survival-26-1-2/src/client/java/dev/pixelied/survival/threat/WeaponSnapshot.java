package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.DamageRange;

import java.util.Objects;

public record WeaponSnapshot(
    String weaponKey,
    DamageRange attackDamage,
    DamageRange attackStrength,
    DamageRange enchantmentBonus,
    DamageRange fallDistance,
    boolean criticalPossible,
    boolean criticalConfirmed,
    boolean canDisableBlocking
) {
    public WeaponSnapshot {
        weaponKey = Objects.requireNonNull(weaponKey, "weaponKey");
        attackDamage = Objects.requireNonNull(attackDamage, "attackDamage");
        attackStrength = Objects.requireNonNull(attackStrength, "attackStrength");
        enchantmentBonus = Objects.requireNonNull(enchantmentBonus, "enchantmentBonus");
        fallDistance = Objects.requireNonNull(fallDistance, "fallDistance");
        if (attackDamage.min() < 0f || attackStrength.min() < 0f || enchantmentBonus.min() < 0f || fallDistance.min() < 0f) {
            throw new IllegalArgumentException("weapon damage inputs must be non-negative");
        }
        if (attackStrength.max() > 1f) {
            throw new IllegalArgumentException("attackStrength must be in [0, 1]");
        }
        if (criticalConfirmed && !criticalPossible) {
            throw new IllegalArgumentException("confirmed critical must also be possible");
        }
    }

    public DamageRange rawDamageRange() {
        float baseMin = saturatingMultiply(attackDamage.min(), attackScale(attackStrength.min()));
        float baseMax = saturatingMultiply(attackDamage.max(), attackScale(attackStrength.max()));

        float enchantMin = saturatingMultiply(enchantmentBonus.min(), attackStrength.min());
        float enchantMax = saturatingMultiply(enchantmentBonus.max(), attackStrength.max());

        float maceMin = isMace() ? maceSmashBonus(fallDistance.min()) : 0f;
        float maceMax = isMace() ? maceSmashBonus(fallDistance.max()) : 0f;

        float min = saturatingAdd(saturatingAdd(baseMin, enchantMin), maceMin);
        float max = saturatingAdd(saturatingAdd(baseMax, enchantMax), maceMax);

        if (criticalConfirmed) {
            min = saturatingMultiply(min, 1.5f);
            max = saturatingMultiply(max, 1.5f);
        } else if (criticalPossible) {
            max = saturatingMultiply(max, 1.5f);
        }
        return new DamageRange(min, max);
    }

    public boolean isMaceSmash() {
        return isMace() && fallDistance.max() > 1.5f;
    }

    public static float maceSmashBonus(double fallDistance) {
        return saturatingFloat(maceSmashBonusDouble(fallDistance));
    }

    static double maceSmashBonusDouble(double fallDistance) {
        if (!Double.isFinite(fallDistance) || fallDistance < 0d) {
            throw new IllegalArgumentException("fallDistance must be finite and non-negative");
        }
        if (fallDistance <= 1.5d) return 0d;
        if (fallDistance <= 3d) return 4d * fallDistance;
        if (fallDistance <= 8d) return 12d + 2d * (fallDistance - 3d);
        return 22d + (fallDistance - 8d);
    }

    public static float attackScale(float attackStrength) {
        if (!Float.isFinite(attackStrength) || attackStrength < 0f || attackStrength > 1f) {
            throw new IllegalArgumentException("attackStrength must be finite and in [0, 1]");
        }
        return 0.2f + attackStrength * attackStrength * 0.8f;
    }

    private boolean isMace() {
        return "minecraft:mace".equals(weaponKey);
    }

    private static float saturatingAdd(float left, float right) {
        if (left == Float.MAX_VALUE || right == Float.MAX_VALUE) return Float.MAX_VALUE;
        double sum = (double) left + right;
        return saturatingFloat(sum);
    }

    private static float saturatingMultiply(float value, float factor) {
        if (value == Float.MAX_VALUE && factor > 0f) return Float.MAX_VALUE;
        return saturatingFloat((double) value * factor);
    }

    private static float saturatingFloat(double value) {
        if (Double.isNaN(value) || value <= 0d) return value <= 0d ? 0f : Float.MAX_VALUE;
        if (!Double.isFinite(value) || value >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float) value;
    }
}
