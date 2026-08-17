package dev.pixelied.survival.damage;

import java.util.Objects;

public record ArmorPieceSnapshot(
    Slot slot,
    float armor,
    float toughness,
    int enchantmentProtection,
    int remainingDurability,
    boolean damageOnHurt
) {
    public enum Slot { FEET, LEGS, CHEST, HEAD }

    public ArmorPieceSnapshot {
        slot = Objects.requireNonNull(slot, "slot");
        if (armor < 0f || toughness < 0f) {
            throw new IllegalArgumentException("armor and toughness must be non-negative");
        }
        if (enchantmentProtection < 0 || enchantmentProtection > 20) {
            throw new IllegalArgumentException("enchantmentProtection must be in [0, 20]");
        }
        if (remainingDurability < 0) {
            throw new IllegalArgumentException("remainingDurability must be non-negative");
        }
    }

    public boolean present() {
        return remainingDurability > 0;
    }

    public ArmorPieceSnapshot damage(int amount) {
        if (amount <= 0 || !damageOnHurt || !present()) return this;
        return new ArmorPieceSnapshot(
            slot, armor, toughness, enchantmentProtection,
            Math.max(0, remainingDurability - amount), damageOnHurt
        );
    }
}
