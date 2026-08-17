package dev.adrien.crystaloptimizer.sim.damage;

import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;

public record DamageRequest(
    float rawIncoming,
    Difficulty difficulty,
    boolean scalesWithDifficulty,
    boolean bypassesCooldown,
    boolean bypassesArmor,
    boolean bypassesEffects,
    boolean bypassesResistance,
    boolean bypassesEnchantments,
    boolean bypassesInvulnerability,
    boolean bypassesShield,
    Vec3 sourcePosition
) {
    public static DamageRequest explosion(float rawIncoming) {
        return new DamageRequest(
            rawIncoming,
            Difficulty.NORMAL,
            true,
            false,
            false,
            false,
            false,
            false,
            false,
            false,
            null
        );
    }

    public DamageRequest withDifficulty(Difficulty nextDifficulty) {
        return new DamageRequest(
            rawIncoming,
            nextDifficulty,
            scalesWithDifficulty,
            bypassesCooldown,
            bypassesArmor,
            bypassesEffects,
            bypassesResistance,
            bypassesEnchantments,
            bypassesInvulnerability,
            bypassesShield,
            sourcePosition
        );
    }

    public DamageRequest withSourcePosition(Vec3 nextSourcePosition) {
        return new DamageRequest(
            rawIncoming,
            difficulty,
            scalesWithDifficulty,
            bypassesCooldown,
            bypassesArmor,
            bypassesEffects,
            bypassesResistance,
            bypassesEnchantments,
            bypassesInvulnerability,
            bypassesShield,
            nextSourcePosition
        );
    }
}
