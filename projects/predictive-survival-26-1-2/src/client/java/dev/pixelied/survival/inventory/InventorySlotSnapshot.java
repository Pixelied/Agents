package dev.pixelied.survival.inventory;

import java.util.Objects;
import java.util.Optional;

public record InventorySlotSnapshot(
    int inventoryIndex,
    String stackKey,
    int count,
    boolean deathProtection,
    Optional<ConsumableSurvivalSnapshot> consumable,
    Optional<EquippableSurvivalSnapshot> equippable
) {
    public InventorySlotSnapshot {
        if (inventoryIndex < 0 || inventoryIndex > 40) {
            throw new IllegalArgumentException("inventoryIndex must be in [0, 40]");
        }
        stackKey = Objects.requireNonNull(stackKey, "stackKey");
        if (stackKey.isBlank()) throw new IllegalArgumentException("stackKey must not be blank");
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        consumable = Objects.requireNonNull(consumable, "consumable");
        equippable = Objects.requireNonNull(equippable, "equippable");
        if (count == 0 && (deathProtection || consumable.isPresent() || equippable.isPresent())) {
            throw new IllegalArgumentException("empty slot cannot provide survival capabilities");
        }
    }

    public InventorySlotSnapshot(int inventoryIndex, String stackKey, int count, boolean deathProtection) {
        this(inventoryIndex, stackKey, count, deathProtection, Optional.empty(), Optional.empty());
    }

    public boolean empty() {
        return count == 0;
    }

    public boolean sameContents(InventorySlotSnapshot other) {
        return other != null
            && stackKey.equals(other.stackKey)
            && count == other.count
            && deathProtection == other.deathProtection;
    }
}
