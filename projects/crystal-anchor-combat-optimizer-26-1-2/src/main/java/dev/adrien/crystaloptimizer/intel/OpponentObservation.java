package dev.adrien.crystaloptimizer.intel;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;

public record OpponentObservation(
    UUID opponentId,
    Type type,
    EvidenceKind kind,
    long timestampNanos,
    Optional<EquipmentSlot> slot,
    Optional<Item> item,
    int amount
) {
    public OpponentObservation {
        Objects.requireNonNull(opponentId, "opponentId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(item, "item");
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }

    public enum Type {
        VISIBLE_EQUIPMENT,
        PICKUP,
        PROTECTED_FROM_DEATH,
        OBSERVED_REFILL
    }
}
