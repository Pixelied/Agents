package dev.pixelied.survival.damage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record MitigationSnapshot(
    float armor,
    float toughness,
    float armorEffectivenessMultiplier,
    int enchantmentProtection,
    boolean helmetPresent,
    int helmetDurability,
    List<ArmorPieceSnapshot> armorPieces
) {
    public MitigationSnapshot {
        if (armor < 0f || toughness < 0f || armorEffectivenessMultiplier < 0f || Float.isNaN(armorEffectivenessMultiplier)) {
            throw new IllegalArgumentException("mitigation values must be non-negative");
        }
        if (enchantmentProtection < 0 || enchantmentProtection > 20) {
            throw new IllegalArgumentException("enchantmentProtection must be in [0, 20]");
        }
        if (helmetDurability < 0) throw new IllegalArgumentException("helmetDurability must be non-negative");
        armorPieces = List.copyOf(Objects.requireNonNull(armorPieces, "armorPieces"));
    }

    public MitigationSnapshot(
        float armor,
        float toughness,
        float armorEffectivenessMultiplier,
        int enchantmentProtection,
        boolean helmetPresent,
        int helmetDurability
    ) {
        this(armor, toughness, armorEffectivenessMultiplier, enchantmentProtection, helmetPresent, helmetDurability, List.of());
    }

    public MitigationSnapshot(
        float armor,
        float toughness,
        boolean helmetPresent,
        int helmetDurability,
        List<ArmorPieceSnapshot> armorPieces
    ) {
        this(armor, toughness, 1f, 0, helmetPresent, helmetDurability, armorPieces);
    }

    public static MitigationSnapshot none() {
        return new MitigationSnapshot(0f, 0f, 1f, 0, false, 0, List.of());
    }

    public int enchantmentProtection(DamageSourceSnapshot source) {
        int total = source.has(DamageFlag.BYPASSES_INVULNERABILITY) ? 0 : enchantmentProtection;
        for (ArmorPieceSnapshot piece : armorPieces) {
            if (!piece.present()) continue;
            long next = (long) total + piece.protectionEnchantments().protectionFor(source);
            total = next > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) next;
        }
        return total;
    }

    public boolean enchantmentImmuneTo(DamageSourceSnapshot source) {
        for (ArmorPieceSnapshot piece : armorPieces) {
            if (piece.present() && piece.protectionEnchantments().immuneTo(source)) return true;
        }
        return false;
    }

    public MitigationSnapshot damageHelmet(DamageSourceSnapshot source, float damage) {
        if (!helmetPresent || damage <= 0f) return this;
        int amount = VanillaDamageMath.durabilityDamage(damage);
        boolean hasHeadPiece = armorPieces.stream().anyMatch(piece -> piece.slot() == ArmorPieceSnapshot.Slot.HEAD && piece.present());
        if (!hasHeadPiece) {
            int nextDurability = Math.max(0, helmetDurability - amount);
            return new MitigationSnapshot(
                armor, toughness, armorEffectivenessMultiplier, enchantmentProtection,
                nextDurability > 0, nextDurability, armorPieces
            );
        }
        return damageSelectedPieces(source, amount, true);
    }

    public MitigationSnapshot damageArmor(DamageSourceSnapshot source, float damage) {
        if (damage <= 0f) return this;
        int amount = VanillaDamageMath.durabilityDamage(damage);
        MitigationSnapshot afterPieces = damageSelectedPieces(source, amount, false);
        boolean hasHeadPiece = afterPieces.armorPieces.stream().anyMatch(piece -> piece.slot() == ArmorPieceSnapshot.Slot.HEAD);
        if (hasHeadPiece || !afterPieces.helmetPresent) return afterPieces;

        int nextDurability = Math.max(0, afterPieces.helmetDurability - amount);
        return new MitigationSnapshot(
            afterPieces.armor, afterPieces.toughness, afterPieces.armorEffectivenessMultiplier,
            afterPieces.enchantmentProtection, nextDurability > 0, nextDurability, afterPieces.armorPieces
        );
    }

    private MitigationSnapshot damageSelectedPieces(DamageSourceSnapshot source, int amount, boolean headOnly) {
        if (armorPieces.isEmpty()) return this;
        List<ArmorPieceSnapshot> next = new ArrayList<>(armorPieces.size());
        float nextArmor = armor;
        float nextToughness = toughness;
        boolean nextHelmetPresent = helmetPresent;
        int nextHelmetDurability = helmetDurability;

        for (ArmorPieceSnapshot piece : armorPieces) {
            boolean selected = !headOnly || piece.slot() == ArmorPieceSnapshot.Slot.HEAD;
            ArmorPieceSnapshot damaged = selected ? piece.damage(source, amount) : piece;
            if (piece.present() && !damaged.present()) {
                nextArmor = Math.max(0f, nextArmor - piece.armor());
                nextToughness = Math.max(0f, nextToughness - piece.toughness());
            }
            if (piece.slot() == ArmorPieceSnapshot.Slot.HEAD && selected) {
                nextHelmetDurability = damaged.remainingDurability();
                nextHelmetPresent = damaged.present();
            }
            next.add(damaged);
        }

        return new MitigationSnapshot(
            nextArmor, nextToughness, armorEffectivenessMultiplier,
            enchantmentProtection, nextHelmetPresent, nextHelmetDurability, next
        );
    }
}
