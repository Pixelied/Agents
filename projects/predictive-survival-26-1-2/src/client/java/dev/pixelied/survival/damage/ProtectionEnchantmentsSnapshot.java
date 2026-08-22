package dev.pixelied.survival.damage;

/**
 * Client-observable vanilla 26.1.2 armor protection enchantments whose
 * DAMAGE_PROTECTION / DAMAGE_IMMUNITY effects are relevant to player damage.
 */
public record ProtectionEnchantmentsSnapshot(
    int protection,
    int blastProtection,
    int projectileProtection,
    int fireProtection,
    int featherFalling,
    int frostWalker
) {
    public ProtectionEnchantmentsSnapshot {
        if (protection < 0 || blastProtection < 0 || projectileProtection < 0
            || fireProtection < 0 || featherFalling < 0 || frostWalker < 0) {
            throw new IllegalArgumentException("enchantment levels must be non-negative");
        }
    }

    public static ProtectionEnchantmentsSnapshot none() {
        return new ProtectionEnchantmentsSnapshot(0, 0, 0, 0, 0, 0);
    }

    /** Compatibility for older deterministic fixtures that supplied generic Protection points. */
    public static ProtectionEnchantmentsSnapshot genericProtection(int level) {
        return new ProtectionEnchantmentsSnapshot(level, 0, 0, 0, 0, 0);
    }

    public int protectionFor(DamageSourceSnapshot source) {
        if (source.has(DamageFlag.BYPASSES_INVULNERABILITY)) return 0;
        int points = protection;
        if (source.has(DamageFlag.IS_EXPLOSION)) points = saturatingAdd(points, blastProtection * 2);
        if (source.has(DamageFlag.IS_PROJECTILE)) points = saturatingAdd(points, projectileProtection * 2);
        if (source.has(DamageFlag.IS_FIRE)) points = saturatingAdd(points, fireProtection * 2);
        if (source.has(DamageFlag.IS_FALL)) points = saturatingAdd(points, featherFalling * 3);
        return points;
    }

    public boolean immuneTo(DamageSourceSnapshot source) {
        return frostWalker > 0
            && source.has(DamageFlag.BURN_FROM_STEPPING)
            && !source.has(DamageFlag.BYPASSES_INVULNERABILITY);
    }

    private static int saturatingAdd(int left, int right) {
        long sum = (long) left + right;
        return sum > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }
}
