package dev.pixelied.survival.damage;

public record MitigationSnapshot(
    float armor,
    float toughness,
    float armorEffectivenessMultiplier,
    int enchantmentProtection,
    boolean helmetPresent,
    int helmetDurability
) {
    public MitigationSnapshot {
        if (armor < 0f || toughness < 0f || armorEffectivenessMultiplier < 0f) {
            throw new IllegalArgumentException("mitigation values must be non-negative");
        }
        if (enchantmentProtection < 0 || enchantmentProtection > 20) {
            throw new IllegalArgumentException("enchantmentProtection must be in [0, 20]");
        }
        if (helmetDurability < 0) {
            throw new IllegalArgumentException("helmetDurability must be non-negative");
        }
    }

    public static MitigationSnapshot none() {
        return new MitigationSnapshot(0f, 0f, 1f, 0, false, 0);
    }
}
