package dev.pixelied.survival.inventory;

import java.util.Objects;

public record InventorySlotSnapshot(
    int inventoryIndex,
    String stackKey,
    int count,
    boolean deathProtection
) {
    public InventorySlotSnapshot {
        if (inventoryIndex < 0 || inventoryIndex > 40) {
            throw new IllegalArgumentException("inventoryIndex must be in [0, 40]");
        }
        stackKey = Objects.requireNonNull(stackKey, "stackKey");
        if (stackKey.isBlank()) throw new IllegalArgumentException("stackKey must not be blank");
        if (count < 0) throw new IllegalArgumentException("count must be non-negative");
        if (count == 0 && deathProtection) {
            throw new IllegalArgumentException("empty slot cannot provide death protection");
        }
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
