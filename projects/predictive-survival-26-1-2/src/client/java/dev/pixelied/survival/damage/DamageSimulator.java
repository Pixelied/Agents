package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;

public final class DamageSimulator {
    public DamageResult simulate(PlayerSnapshot player, DamageSourceSnapshot source) {
        DamageTrace.Builder trace = DamageTrace.builder();
        PlayerSnapshot working = player;
        float damage = source.rawDamage().max();
        trace.record(DamageStage.RAW, damage, damage);

        boolean bypassesInvulnerability = source.has(DamageFlag.BYPASSES_INVULNERABILITY);
        if ((working.playerInvulnerable() || working.abilityInvulnerable()) && !bypassesInvulnerability) {
            return rejected(working, trace);
        }
        if (working.deadOrDying()) {
            return rejected(working, trace);
        }
        if (working.health() <= source.applicationHealthThresholdExclusive()) {
            return rejected(working, trace);
        }

        float before = damage;
        if (source.scalesWithDifficulty()) {
            damage = VanillaDamageMath.scaleForDifficulty(damage, working.difficulty());
        }
        trace.record(DamageStage.DIFFICULTY, before, damage);
        if (damage == 0f) {
            return rejected(working, trace);
        }

        if (working.statusEffects().fireResistance() && source.has(DamageFlag.IS_FIRE)) {
            trace.record(DamageStage.FIRE_RESISTANCE, damage, 0f);
            return rejected(working, trace);
        }
        trace.record(DamageStage.FIRE_RESISTANCE, damage, damage);

        before = damage;
        damage = Math.max(0f, damage);
        trace.record(DamageStage.CLAMP_NEGATIVE, before, damage);

        before = damage;
        if (working.blocking().active()
            && !source.has(DamageFlag.BYPASSES_SHIELD)
            && !source.piercingProjectile()) {
            damage *= 1f - working.blocking().blockedFraction();
        }
        trace.record(DamageStage.BLOCKING, before, damage);

        before = damage;
        if (source.has(DamageFlag.IS_FREEZING)) {
            damage *= source.freezingMultiplier();
        }
        trace.record(DamageStage.FREEZING, before, damage);

        before = damage;
        if (source.has(DamageFlag.DAMAGES_HELMET) && working.mitigation().helmetPresent()) {
            MitigationSnapshot afterHelmetDamage = working.mitigation().damageHelmet(damage);
            working = withState(working, working.health(), working.absorption(), afterHelmetDamage,
                working.statusEffects(), working.hurtState(), working.deathProtection());
            damage *= 0.75f;
        }
        trace.record(DamageStage.HELMET, before, damage);

        before = damage;
        damage = VanillaDamageMath.sanitize(damage);
        trace.record(DamageStage.SANITIZE, before, damage);

        HurtState previousHurt = working.hurtState();
        boolean trustedLastHurt = previousHurt.confidence() == Confidence.EXACT
            || previousHurt.confidence() == Confidence.MATCHED;
        float creditedLastHurt = trustedLastHurt ? previousHurt.lastHurt().min() : 0f;
        HurtState nextHurt;

        before = damage;
        if (previousHurt.invulnerableTime() > 10 && !source.has(DamageFlag.BYPASSES_COOLDOWN)) {
            if (damage <= creditedLastHurt) {
                trace.record(DamageStage.HURT_COOLDOWN, before, 0f);
                return new DamageResult(working, trace.build(), true, false);
            }
            damage -= creditedLastHurt;
            nextHurt = new HurtState(
                DamageRange.exact(before), previousHurt.invulnerableTime(), confidenceFor(source)
            );
        } else {
            nextHurt = new HurtState(DamageRange.exact(damage), 20, confidenceFor(source));
        }
        trace.record(DamageStage.HURT_COOLDOWN, before, damage);
        working = withState(working, working.health(), working.absorption(), working.mitigation(),
            working.statusEffects(), nextHurt, working.deathProtection());

        return actuallyHurt(working, source, damage, trace);
    }

    private DamageResult actuallyHurt(PlayerSnapshot player, DamageSourceSnapshot source, float damage, DamageTrace.Builder trace) {
        MitigationSnapshot mitigation = player.mitigation();
        float before = damage;
        if (!source.has(DamageFlag.BYPASSES_ARMOR)) {
            mitigation = mitigation.damageArmor(damage);
            damage = VanillaDamageMath.afterArmor(
                damage, mitigation.armor(), mitigation.toughness(), mitigation.armorEffectivenessMultiplier()
            );
        }
        trace.record(DamageStage.ARMOR, before, damage);

        before = damage;
        if (!source.has(DamageFlag.BYPASSES_EFFECTS)
            && !source.has(DamageFlag.BYPASSES_RESISTANCE)
            && player.statusEffects().resistanceAmplifier() >= 0) {
            damage = VanillaDamageMath.afterResistance(damage, player.statusEffects().resistanceAmplifier());
        }
        trace.record(DamageStage.RESISTANCE, before, damage);

        before = damage;
        if (!source.has(DamageFlag.BYPASSES_EFFECTS)
            && damage > 0f
            && !source.has(DamageFlag.BYPASSES_ENCHANTMENTS)
            && mitigation.enchantmentProtection() > 0) {
            damage = VanillaDamageMath.afterMagicProtection(damage, mitigation.enchantmentProtection());
        }
        trace.record(DamageStage.MAGIC, before, damage);

        float originalAfterMagic = damage;
        float remainingAfterAbsorption = Math.max(damage - player.absorption(), 0f);
        float absorbed = originalAfterMagic - remainingAfterAbsorption;
        float nextAbsorption = Math.max(0f, player.absorption() - absorbed);
        trace.record(DamageStage.ABSORPTION, originalAfterMagic, remainingAfterAbsorption);

        float healthDamage = remainingAfterAbsorption;
        float nextHealth = Math.max(0f, player.health() - healthDamage);
        trace.record(DamageStage.HEALTH_DAMAGE, healthDamage, healthDamage);

        PlayerSnapshot afterDamage = withState(
            player, nextHealth, nextAbsorption, mitigation, player.statusEffects(),
            player.hurtState(), player.deathProtection()
        );

        if (nextHealth > 0f || source.has(DamageFlag.BYPASSES_INVULNERABILITY)) {
            return new DamageResult(afterDamage, trace.build(), false, false);
        }

        var consumption = afterDamage.deathProtection().consumeFirst();
        if (consumption.isEmpty()) {
            return new DamageResult(afterDamage, trace.build(), false, false);
        }

        DeathProtectionSnapshot.ProtectionItem item = consumption.get().item();
        StatusEffectsSnapshot effects = item.clearExistingEffects()
            ? afterDamage.statusEffects().clearAll()
            : afterDamage.statusEffects();
        effects = effects.apply(item.effects());
        float protectionAbsorption = nextAbsorption;
        for (EffectInstanceSnapshot effect : item.effects()) {
            if (effect.effectKey().equals("minecraft:absorption")) {
                protectionAbsorption = Math.max(protectionAbsorption, 4f * (1 + effect.amplifier()));
            }
        }

        PlayerSnapshot protectedPlayer = withState(
            afterDamage, 1f, protectionAbsorption, mitigation, effects,
            afterDamage.hurtState(), consumption.get().remaining()
        );
        return new DamageResult(protectedPlayer, trace.build(), false, true);
    }

    private static Confidence confidenceFor(DamageSourceSnapshot source) {
        return Float.compare(source.rawDamage().min(), source.rawDamage().max()) == 0
            ? Confidence.EXACT
            : Confidence.BOUNDED;
    }

    private static DamageResult rejected(PlayerSnapshot player, DamageTrace.Builder trace) {
        return new DamageResult(player, trace.build(), true, false);
    }

    private static PlayerSnapshot withState(
        PlayerSnapshot player,
        float health,
        float absorption,
        MitigationSnapshot mitigation,
        StatusEffectsSnapshot effects,
        HurtState hurtState,
        DeathProtectionSnapshot deathProtection
    ) {
        return new PlayerSnapshot(
            health, absorption, player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), mitigation, effects, player.blocking(), hurtState, deathProtection,
            player.boundingBox(), player.position(), player.velocity(), player.equipmentItemKeys(), player.stateProperties()
        );
    }
}
