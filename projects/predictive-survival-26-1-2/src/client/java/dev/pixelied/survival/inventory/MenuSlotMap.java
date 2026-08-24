package dev.pixelied.survival.inventory;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

public record MenuSlotMap(
    int containerId,
    int stateId,
    Map<Integer, Integer> inventoryIndexToMenuSlot
) {
    public MenuSlotMap {
        if (containerId < 0 || stateId < 0) {
            throw new IllegalArgumentException("containerId and stateId must be non-negative");
        }
        inventoryIndexToMenuSlot = Map.copyOf(Objects.requireNonNull(inventoryIndexToMenuSlot, "inventoryIndexToMenuSlot"));
    }

    public OptionalInt menuSlotForInventoryIndex(int inventoryIndex) {
        Integer slot = inventoryIndexToMenuSlot.get(inventoryIndex);
        return slot == null ? OptionalInt.empty() : OptionalInt.of(slot);
    }
}
