package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import net.minecraft.world.phys.Vec3;

public final class VanillaMitigationPipeline {
    public static float blockedDamage(
        BlockingState blocking,
        Vec3 sourcePosition,
        float damage,
        DamageRequest request
    ) {
        if (damage <= 0.0f || !blocking.active() || request.bypassesShield() || sourcePosition == null) {
            return 0.0f;
        }

        double yawRadians = Math.toRadians(blocking.headYawDegrees());
        Vec3 viewVector = new Vec3(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3 vectorTo = sourcePosition.subtract(blocking.position());
        vectorTo = new Vec3(vectorTo.x, 0.0, vectorTo.z).normalize();
        double dot = clamp(vectorTo.dot(viewVector), -1.0, 1.0);
        double angle = Math.acos(dot);
        if (angle > Math.toRadians(blocking.horizontalBlockingAngle())) {
            return 0.0f;
        }

        return clamp(blocking.baseReduction() + blocking.factorReduction() * damage, 0.0f, damage);
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
        EffectState effects,
        DamageRequest request
    ) {
        if (request.bypassesEffects()) {
            return damage;
        }

        if (effects.hasResistance() && !request.bypassesResistance()) {
            int absorbValue = (effects.resistanceAmplifier() + 1) * 5;
            int remaining = 25 - absorbValue;
            damage = Math.max(damage * remaining / 25.0f, 0.0f);
        }

        if (damage <= 0.0f || request.bypassesEnchantments()) {
            return Math.max(damage, 0.0f);
        }

        float protection = equipment.enchantmentProtection();
        if (protection > 0.0f) {
            float realProtection = clamp(protection, 0.0f, 20.0f);
            damage *= 1.0f - realProtection / 25.0f;
        }

        return damage;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private VanillaMitigationPipeline() {
    }
}
