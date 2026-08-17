package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;

public final class DamageSimulator {
    public DamageResult simulate(PlayerSnapshot player, DamageSourceSnapshot source) {
        DamageTrace.Builder trace = DamageTrace.builder();
        float damage = source.rawDamage().max();
        trace.record(DamageStage.RAW, damage, damage);

        boolean bypassesInvulnerability = source.has(DamageFlag.BYPASSES_INVULNERABILITY);
        if ((player.playerInvulnerable() || player.abilityInvulnerable()) && !bypassesInvulnerability) {
            return rejected(player, trace);
        }
        if (player.deadOrDying()) {
            return rejected(player, trace);
        }

        float before = damage;
        if (source.scalesWithDifficulty()) {
            damage = VanillaDamageMath.scaleForDifficulty(damage, player.difficulty());
        }
        trace.record(DamageStage.DIFFICULTY, before, damage);
        if (damage == 0f) {
            return rejected(player, trace);
        }

        if (player.statusEffects().fireResistance() && source.has(DamageFlag.IS_FIRE)) {
            trace.record(DamageStage.FIRE_RESISTANCE, damage, 0f);
            return rejected(player, trace);
        }
        trace.record(DamageStage.FIRE_RESISTANCE, damage, damage);

        before = damage;
        damage = Math.max(0f, damage);
        trace.record(DamageStage.CLAMP_NEGATIVE, before, damage);

        before = damage;
        if (player.blocking().active()
            && !source.has(DamageFlag.BYPASSES_SHIELD)
            && !source.piercingProjectile()) {
            damage *= 1f - player.blocking().blockedFraction();
        }
        trace.record(DamageStage.BLOCKING, before, damage);

        before = damage;
        if (source.has(DamageFlag.IS_FREEZING)) {
            damage *= source.freezingMultiplier();
        }
        trace.record(DamageStage.FREEZING, before, damage);

        before = damage;
        if (source.has(DamageFlag.DAMAGES_HELMET) && player.mitigation().helmetPresent()) {
            damage *= 0.75f;
        }
        trace.record(DamageStage.HELMET, before, damage);

        before = damage;
        damage = VanillaDamageMath.sanitize(damage);
        trace.record(DamageStage.SANITIZE, before, damage);

        HurtState previousHurt = player.hurtState();
        boolean trustedLastHurt = previousHurt.confidence() == Confidence.EXACT
            || previousHurt.confidence() == Confidence.MATCHED;
        float creditedLastHurt = trustedLastHurt ? previousHurt.lastHurt().min() : 0f;
        HurtState nextHurt;

        before = damage;
        if (previousHurt.invulnerableTime() > 10 && !source.has(DamageFlag.BYPASSES_COOLDOWN)) {
            if (damage <= creditedLastHurt) {
                trace.record(DamageStage.HURT_COOLDOWN, before, 0f);
                return new DamageResult(player, trace.build(), true, false);
            }
            damage -= creditedLastHurt;
            nextHurt = new HurtState(
                DamageRange.exact(before),
                previousHurt.invulnerableTime(),
                confidenceFor(source)
            );
        } else {
            nextHurt = new HurtState(DamageRange.exact(damage), 20, confidenceFor(source));
        }
        trace.record(DamageStage.HURT_COOLDOWN, before, damage);

        return new DamageResult(withHurtState(player, nextHurt), trace.build(), false, false);
    }

    private static Confidence confidenceFor(DamageSourceSnapshot source) {
        return Float.compare(source.rawDamage().min(), source.rawDamage().max()) == 0
            ? Confidence.EXACT
            : Confidence.BOUNDED;
    }

    private static DamageResult rejected(PlayerSnapshot player, DamageTrace.Builder trace) {
        return new DamageResult(player, trace.build(), true, false);
    }

    private static PlayerSnapshot withHurtState(PlayerSnapshot player, HurtState hurtState) {
        return new PlayerSnapshot(
            player.health(),
            player.absorption(),
            player.playerInvulnerable(),
            player.abilityInvulnerable(),
            player.deadOrDying(),
            player.difficulty(),
            player.mitigation(),
            player.statusEffects(),
            player.blocking(),
            hurtState,
            player.deathProtection(),
            player.boundingBox(),
            player.position(),
            player.velocity(),
            player.equipmentItemKeys()
        );
    }
}
