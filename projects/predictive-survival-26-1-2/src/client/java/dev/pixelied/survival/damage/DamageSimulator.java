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
        if (working.mitigation().enchantmentImmuneTo(source)) {
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
        float blockedDamage = 0f;
        if (working.blocking().active()
            && !source.has(DamageFlag.BYPASSES_SHIELD)
            && !source.piercingProjectile()) {
            BlockingSnapshot blocking = working.blocking();
            if (blocking.profile().isPresent()) {
                BlockingProfileSnapshot profile = blocking.profile().get();
                double angle = horizontalBlockingAngle(working, source);
                blockedDamage = profile.resolveBlockedDamage(source, damage, angle);
                BlockingProfileSnapshot afterItemDamage = profile.damageForBlockedAmount(blockedDamage);
                if (!afterItemDamage.equals(profile)) {
                    working = withBlocking(working, blocking.withProfile(afterItemDamage));
                }
                damage = Math.max(0f, damage - blockedDamage);
            } else {
                blockedDamage = damage * blocking.blockedFraction();
                damage = Math.max(0f, damage - blockedDamage);
            }
        }
        boolean fullyBlocked = before > 0f && blockedDamage > 0f && damage <= 0f;
        trace.record(DamageStage.BLOCKING, before, damage);

        before = damage;
        if (source.has(DamageFlag.IS_FREEZING)) {
            damage *= source.freezingMultiplier();
        }
        trace.record(DamageStage.FREEZING, before, damage);

        before = damage;
        if (source.has(DamageFlag.DAMAGES_HELMET) && working.mitigation().helmetPresent()) {
            MitigationSnapshot afterHelmetDamage = working.mitigation().damageHelmet(source, damage);
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

        DamageResult result = actuallyHurt(working, source, damage, trace);
        if (fullyBlocked && !result.rejected()) {
            return new DamageResult(result.after(), result.trace(), true, result.deathProtectionConsumed());
        }
        return result;
    }

    private DamageResult actuallyHurt(PlayerSnapshot player, DamageSourceSnapshot source, float damage, DamageTrace.Builder trace) {
        MitigationSnapshot mitigation = player.mitigation();
        float before = damage;
        if (!source.has(DamageFlag.BYPASSES_ARMOR)) {
            mitigation = mitigation.damageArmor(source, damage);
            damage = VanillaDamageMath.afterArmor(
                damage, mitigation.armor(), mitigation.toughness(), source.armorEffectivenessAdjustment()
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
            && mitigation.enchantmentProtection(source) > 0) {
            damage = VanillaDamageMath.afterMagicProtection(damage, mitigation.enchantmentProtection(source));
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
        return new DamageResult(protectedPlayer, trace.build(), false, true, item.outcomeUncertain());
    }

    private static double horizontalBlockingAngle(PlayerSnapshot player, DamageSourceSnapshot source) {
        if (source.sourcePosition().isEmpty()) return Math.PI;
        String yawValue = player.state("head_yaw");
        if (yawValue == null) return Math.PI;
        final float yaw;
        try { yaw = Float.parseFloat(yawValue); } catch (NumberFormatException ignored) { return Math.PI; }
        if (!Float.isFinite(yaw)) return Math.PI;

        double realYRot = -Math.toRadians(yaw);
        double viewX = Math.sin(realYRot);
        double viewZ = Math.cos(realYRot);
        double toX = source.sourcePosition().get().x() - player.position().x();
        double toZ = source.sourcePosition().get().z() - player.position().z();
        double length = Math.hypot(toX, toZ);
        if (!(length > 0d) || !Double.isFinite(length)) return 0d;
        double dot = (toX / length) * viewX + (toZ / length) * viewZ;
        dot = Math.max(-1d, Math.min(1d, dot));
        return Math.acos(dot);
    }

    private static PlayerSnapshot withBlocking(PlayerSnapshot player, BlockingSnapshot blocking) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), player.mitigation(), player.statusEffects(), blocking, player.hurtState(), player.deathProtection(),
            player.boundingBox(), player.position(), player.velocity(), player.equipmentItemKeys(), player.stateProperties()
        );
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
