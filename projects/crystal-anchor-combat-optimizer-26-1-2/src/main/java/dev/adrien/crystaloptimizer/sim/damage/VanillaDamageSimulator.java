package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import java.util.Set;
import net.minecraft.world.Difficulty;

public final class VanillaDamageSimulator {
    public static DamageResult apply(SimCombatant target, DamageRequest request) {
        float rawIncoming = request.rawIncoming();
        float difficultyScaled = applyDifficultyScaling(
            rawIncoming,
            request.difficulty(),
            request.scalesWithDifficulty()
        );
        float previousLastHurt = target.hurtWindow().lastHurt();

        if (difficultyScaled == 0.0f) {
            return new DamageResult(
                target,
                new DamageTrace(
                    rawIncoming,
                    0.0f,
                    0.0f,
                    0.0f,
                    previousLastHurt,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    Set.of(),
                    false,
                    target.dead()
                ),
                false
            );
        }

        float blockedDamage = VanillaMitigationPipeline.blockedDamage(
            target.blocking(),
            request.sourcePosition(),
            difficultyScaled,
            request
        );
        float incoming = Math.max(0.0f, difficultyScaled - blockedDamage);

        var hurtDecision = HurtWindowProcessor.evaluate(target.hurtWindow(), incoming);
        if (!hurtDecision.accepted()) {
            return new DamageResult(
                target,
                new DamageTrace(
                    rawIncoming,
                    difficultyScaled,
                    blockedDamage,
                    incoming,
                    previousLastHurt,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    Set.of(),
                    false,
                    target.dead()
                ),
                false,
                hurtDecision.uncertain()
            );
        }

        float acceptedIncoming = hurtDecision.damageForMitigation();
        EquipmentDamage equipmentDamage = EquipmentDamage.applyExplosionDurability(
            target.equipment(),
            acceptedIncoming,
            request
        );
        float postArmor = VanillaMitigationPipeline.afterArmor(
            acceptedIncoming,
            equipmentDamage.equipment(),
            request
        );
        float postMagic = VanillaMitigationPipeline.afterEffectsAndEnchantments(
            postArmor,
            equipmentDamage.equipment(),
            target.effects(),
            request
        );

        float absorptionConsumed = Math.min(target.absorption(), postMagic);
        float nextAbsorption = Math.max(0.0f, target.absorption() - absorptionConsumed);
        float healthDamage = Math.max(0.0f, postMagic - absorptionConsumed);
        float nextHealth = Math.max(0.0f, target.health() - healthDamage);
        HurtWindowState nextHurtWindow = hurtDecision.nextState();
        boolean totemTriggered = false;
        boolean dead = nextHealth <= 0.0f;
        var nextEffects = target.effects();
        var nextTotem = target.totem();

        if (dead && !request.bypassesInvulnerability() && nextTotem.available()) {
            totemTriggered = true;
            dead = false;
            nextHealth = 1.0f;
            nextAbsorption = 8.0f;
            nextTotem = nextTotem.consumeFirst();
            nextEffects = EffectState.totemEffects();
        }

        SimCombatant nextTarget = target.withDamageState(
            nextHealth,
            nextAbsorption,
            equipmentDamage.equipment(),
            nextEffects,
            nextHurtWindow,
            nextTotem,
            dead
        );

        return new DamageResult(
            nextTarget,
            new DamageTrace(
                rawIncoming,
                difficultyScaled,
                blockedDamage,
                incoming,
                previousLastHurt,
                acceptedIncoming,
                postArmor,
                postMagic,
                absorptionConsumed,
                healthDamage,
                equipmentDamage.brokenSlots(),
                totemTriggered,
                dead
            ),
            true
        );
    }

    public static float applyDifficultyScaling(float damage, Difficulty difficulty, boolean scalesWithDifficulty) {
        if (!scalesWithDifficulty) {
            return damage;
        }

        return switch (difficulty) {
            case PEACEFUL -> 0.0f;
            case EASY -> Math.min(damage / 2.0f + 1.0f, damage);
            case HARD -> damage * 3.0f / 2.0f;
            case NORMAL -> damage;
        };
    }

    public VanillaDamageSimulator() {
    }
}
