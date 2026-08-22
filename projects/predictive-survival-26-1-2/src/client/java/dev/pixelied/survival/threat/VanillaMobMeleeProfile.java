package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.DamageRange;

/** Pure source-audited vanilla 26.1.2 mob melee reconstruction helpers. */
public final class VanillaMobMeleeProfile {
    private VanillaMobMeleeProfile() {}

    public static float reconstructedAttackAttribute(
        String typeKey,
        float defaultBase,
        boolean baby,
        int syncedSize,
        boolean killerRabbit
    ) {
        float value = switch (typeKey) {
            case "minecraft:wither_skeleton" -> 4f;
            case "minecraft:hoglin", "minecraft:zoglin" -> baby ? 0.5f : 6f;
            case "minecraft:slime", "minecraft:magma_cube" -> Math.max(1, syncedSize);
            case "minecraft:phantom" -> 6f + Math.max(0, syncedSize);
            case "minecraft:goat" -> baby ? 1f : 2f;
            default -> defaultBase;
        };
        if ("minecraft:rabbit".equals(typeKey) && killerRabbit) value += 5f;
        return Math.max(0f, value);
    }

    public static DamageRange directDamage(
        String typeKey,
        float attackAttributeValue,
        boolean baby,
        float genericItemPipelineDamage
    ) {
        float attack = Math.max(0f, attackAttributeValue);
        return switch (typeKey) {
            case "minecraft:creeper" -> DamageRange.exact(0f);
            case "minecraft:iron_golem" -> randomHalfPlusInt(attack);
            case "minecraft:hoglin", "minecraft:zoglin" -> baby
                ? DamageRange.exact(attack)
                : randomHalfPlusInt(attack);
            case "minecraft:bee" -> DamageRange.exact((int)attack);
            case "minecraft:magma_cube" -> DamageRange.exact(attack + 2f);
            case "minecraft:slime" -> DamageRange.exact(attack);
            default -> DamageRange.exact(Math.max(0f, genericItemPipelineDamage));
        };
    }

    public static boolean usesGenericItemAttackPipeline(String typeKey) {
        return switch (typeKey) {
            case "minecraft:creeper", "minecraft:iron_golem", "minecraft:hoglin", "minecraft:zoglin",
                 "minecraft:bee", "minecraft:slime", "minecraft:magma_cube" -> false;
            default -> true;
        };
    }

    public static float genericDirectDamage(
        float attackAttributeValue,
        float enchantmentDamageBonus,
        String weaponKey,
        double fallDistance,
        int densityLevel
    ) {
        if (!Double.isFinite(fallDistance) || fallDistance < 0d) {
            throw new IllegalArgumentException("fallDistance must be finite and non-negative");
        }
        double result = (double)Math.max(0f, attackAttributeValue) + Math.max(0f, enchantmentDamageBonus);
        if ("minecraft:mace".equals(weaponKey) && fallDistance > 1.5d) {
            result += WeaponSnapshot.maceSmashBonusDouble(fallDistance);
            result += Math.max(0, densityLevel) * 0.5d * fallDistance;
        }
        if (!Double.isFinite(result) || result >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float)result;
    }

    private static DamageRange randomHalfPlusInt(float attack) {
        int integer = (int)attack;
        if (integer <= 0) return DamageRange.exact(attack);
        return new DamageRange(attack / 2f, attack / 2f + integer - 1f);
    }
}
