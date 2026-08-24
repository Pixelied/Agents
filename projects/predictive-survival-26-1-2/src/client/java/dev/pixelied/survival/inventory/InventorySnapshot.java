package dev.pixelied.survival.inventory;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record InventorySnapshot(
    int selectedHotbarIndex,
    Map<Integer, InventorySlotSnapshot> slots,
    boolean activeOffhandShield
) {
    public InventorySnapshot {
        if (selectedHotbarIndex < 0 || selectedHotbarIndex > 8) {
            throw new IllegalArgumentException("selectedHotbarIndex must be in [0, 8]");
        }
        slots = Map.copyOf(Objects.requireNonNull(slots, "slots"));
        for (var entry : slots.entrySet()) {
            if (entry.getKey() != entry.getValue().inventoryIndex()) {
                throw new IllegalArgumentException("slot map key must equal inventory index");
            }
        }
    }

    public Optional<InventorySlotSnapshot> slot(int inventoryIndex) {
        return Optional.ofNullable(slots.get(inventoryIndex));
    }
}
