package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.EquipmentState;

public final class VanillaMitigationPipeline {
    public static float blockedDamage(float damage, DamageRequest request) {
        return 0.0f;
    }

    public static float afterArmor(float damage, EquipmentState equipment, DamageRequest request) {
        if (request.bypassesArmor()) {
            return damage;
        }

        float totalArmor = equipment.armorPoints();
        float toughness = 2.0f + equipment.toughness() / 4.0f;
        float lowerBound = totalArmor * 0.2f;
        float realArmor = clamp(totalArmor - damage / toughness, lowerBound, 20.0f);
        return damage * (1.0f - realArmor / 25.0f);
    }

    public static float afterEffectsAndEnchantments(
        float damage,
        EquipmentState equipment,
        DamageRequest request
    ) {
        return damage;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private VanillaMitigationPipeline() {
    }
}
