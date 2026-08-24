package dev.pixelied.survival.inventory;

import dev.pixelied.survival.damage.ArmorPieceSnapshot;

import java.util.Objects;

public record EquippableSurvivalSnapshot(
    ArmorPieceSnapshot armorPiece,
    boolean usable
) {
    public EquippableSurvivalSnapshot {
        armorPiece = Objects.requireNonNull(armorPiece, "armorPiece");
    }
}
