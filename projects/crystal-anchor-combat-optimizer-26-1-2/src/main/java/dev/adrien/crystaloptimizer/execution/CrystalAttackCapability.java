package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;

/** Vanilla 26.1.2 crystal-attack viability derived from effective positive attack damage. */
public final class CrystalAttackCapability {
    private static final double POSITIVE_DAMAGE_EPSILON = 1.0e-9;

    private CrystalAttackCapability() {
    }

    public static CrystalAttackCapability vanilla26_1_2() {
        return new CrystalAttackCapability();
    }

    public boolean canDamageCrystal(
        AttackItemProfile item,
        StatusEffectSnapshot effects
    ) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(effects, "effects");
        double effectiveDamage = item.totalAttackDamage() + effects.attackDamageDelta();
        return effectiveDamage > POSITIVE_DAMAGE_EPSILON;
    }
}
