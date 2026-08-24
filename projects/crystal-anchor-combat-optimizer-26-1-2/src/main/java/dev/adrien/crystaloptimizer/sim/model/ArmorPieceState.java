package dev.adrien.crystaloptimizer.sim.model;

public record ArmorPieceState(
    float armorPoints,
    float toughness,
    int durabilityRemaining,
    float enchantmentProtection
) {
    public static ArmorPieceState testPiece(
        float armorPoints,
        float toughness,
        int durabilityRemaining,
        float enchantmentProtection
    ) {
        return new ArmorPieceState(armorPoints, toughness, durabilityRemaining, enchantmentProtection);
    }

    public ArmorPieceState damage(int amount) {
        return new ArmorPieceState(
            armorPoints,
            toughness,
            Math.max(0, durabilityRemaining - amount),
            enchantmentProtection
        );
    }

    public boolean broken() {
        return durabilityRemaining <= 0;
    }
}
