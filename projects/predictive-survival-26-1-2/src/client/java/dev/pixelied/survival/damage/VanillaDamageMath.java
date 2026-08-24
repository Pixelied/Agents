package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.DifficultySnapshot;

public final class VanillaDamageMath {
    private VanillaDamageMath() {
    }

    public static float scaleForDifficulty(float damage, DifficultySnapshot difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> 0f;
            case EASY -> Math.min(damage / 2f + 1f, damage);
            case NORMAL -> damage;
            case HARD -> damage * 1.5f;
        };
    }

    public static float sanitize(float damage) {
        return Float.isFinite(damage) ? damage : Float.MAX_VALUE;
    }

    public static int durabilityDamage(float damage) {
        return (int)Math.max(1f, damage / 4f);
    }

    public static float afterArmor(float damage, float armor, float toughness, float armorEffectivenessAdjustment) {
        float toughnessFactor = 2f + toughness / 4f;
        float realArmor = clamp(armor - damage / toughnessFactor, armor * 0.2f, 20f);
        float armorFraction = realArmor / 25f;
        float modifiedArmorFraction = clamp(armorFraction + armorEffectivenessAdjustment, 0f, 1f);
        return damage * (1f - modifiedArmorFraction);
    }

    public static float afterResistance(float damage, int amplifier) {
        if (amplifier < 0) return damage;
        int absorbValue = (amplifier + 1) * 5;
        int absorb = 25 - absorbValue;
        return Math.max(damage * absorb / 25f, 0f);
    }

    public static float afterMagicProtection(float damage, int protection) {
        float realProtection = clamp(protection, 0f, 20f);
        return damage * (1f - realProtection / 25f);
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
