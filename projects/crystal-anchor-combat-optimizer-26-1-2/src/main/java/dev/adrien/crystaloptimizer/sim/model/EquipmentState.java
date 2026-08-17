package dev.adrien.crystaloptimizer.sim.model;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.entity.EquipmentSlot;

public record EquipmentState(
    Optional<ArmorPieceState> head,
    Optional<ArmorPieceState> chest,
    Optional<ArmorPieceState> legs,
    Optional<ArmorPieceState> feet
) {
    public EquipmentState {
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(chest, "chest");
        Objects.requireNonNull(legs, "legs");
        Objects.requireNonNull(feet, "feet");
    }

    public static EquipmentState empty() {
        return new EquipmentState(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public Optional<ArmorPieceState> piece(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> head;
            case CHEST -> chest;
            case LEGS -> legs;
            case FEET -> feet;
            default -> Optional.empty();
        };
    }

    public EquipmentState withPiece(EquipmentSlot slot, ArmorPieceState piece) {
        Objects.requireNonNull(piece, "piece");
        return switch (slot) {
            case HEAD -> new EquipmentState(Optional.of(piece), chest, legs, feet);
            case CHEST -> new EquipmentState(head, Optional.of(piece), legs, feet);
            case LEGS -> new EquipmentState(head, chest, Optional.of(piece), feet);
            case FEET -> new EquipmentState(head, chest, legs, Optional.of(piece));
            default -> throw new IllegalArgumentException("Not an armor slot: " + slot);
        };
    }

    public EquipmentState withoutPiece(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> new EquipmentState(Optional.empty(), chest, legs, feet);
            case CHEST -> new EquipmentState(head, Optional.empty(), legs, feet);
            case LEGS -> new EquipmentState(head, chest, Optional.empty(), feet);
            case FEET -> new EquipmentState(head, chest, legs, Optional.empty());
            default -> this;
        };
    }

    public float armorPoints() {
        return head.map(ArmorPieceState::armorPoints).orElse(0.0f)
            + chest.map(ArmorPieceState::armorPoints).orElse(0.0f)
            + legs.map(ArmorPieceState::armorPoints).orElse(0.0f)
            + feet.map(ArmorPieceState::armorPoints).orElse(0.0f);
    }

    public float toughness() {
        return head.map(ArmorPieceState::toughness).orElse(0.0f)
            + chest.map(ArmorPieceState::toughness).orElse(0.0f)
            + legs.map(ArmorPieceState::toughness).orElse(0.0f)
            + feet.map(ArmorPieceState::toughness).orElse(0.0f);
    }

    public float enchantmentProtection() {
        return head.map(ArmorPieceState::enchantmentProtection).orElse(0.0f)
            + chest.map(ArmorPieceState::enchantmentProtection).orElse(0.0f)
            + legs.map(ArmorPieceState::enchantmentProtection).orElse(0.0f)
            + feet.map(ArmorPieceState::enchantmentProtection).orElse(0.0f);
    }
}
