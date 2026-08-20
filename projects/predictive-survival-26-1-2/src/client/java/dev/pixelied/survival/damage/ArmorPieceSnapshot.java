package dev.pixelied.survival.damage;

import java.util.Objects;
import java.util.Set;

public record ArmorPieceSnapshot(
    Slot slot,
    float armor,
    float toughness,
    ProtectionEnchantmentsSnapshot protectionEnchantments,
    int remainingDurability,
    boolean damageOnHurt,
    Set<String> durabilityResistantDamageTypeKeys
) {
    public enum Slot { FEET, LEGS, CHEST, HEAD }

    public ArmorPieceSnapshot {
        slot = Objects.requireNonNull(slot, "slot");
        protectionEnchantments = Objects.requireNonNull(protectionEnchantments, "protectionEnchantments");
        durabilityResistantDamageTypeKeys = Set.copyOf(Objects.requireNonNull(
            durabilityResistantDamageTypeKeys, "durabilityResistantDamageTypeKeys"));
        if (armor < 0f || toughness < 0f) {
            throw new IllegalArgumentException("armor and toughness must be non-negative");
        }
        if (remainingDurability < 0) {
            throw new IllegalArgumentException("remainingDurability must be non-negative");
        }
    }

    public ArmorPieceSnapshot(
        Slot slot,
        float armor,
        float toughness,
        int enchantmentProtection,
        int remainingDurability,
        boolean damageOnHurt
    ) {
        this(slot, armor, toughness, ProtectionEnchantmentsSnapshot.genericProtection(enchantmentProtection),
            remainingDurability, damageOnHurt, Set.of());
    }

    /** Legacy accessor retained for deterministic fixtures; real protection is source-specific. */
    public int enchantmentProtection() {
        return protectionEnchantments.protection();
    }

    public boolean present() {
        return remainingDurability > 0;
    }

    public ArmorPieceSnapshot damage(DamageSourceSnapshot source, int amount) {
        if (amount <= 0 || !damageOnHurt || !present()
            || durabilityResistantDamageTypeKeys.contains(source.sourceKey())) {
            return this;
        }
        return new ArmorPieceSnapshot(
            slot, armor, toughness, protectionEnchantments,
            Math.max(0, remainingDurability - amount), damageOnHurt, durabilityResistantDamageTypeKeys
        );
    }
}
