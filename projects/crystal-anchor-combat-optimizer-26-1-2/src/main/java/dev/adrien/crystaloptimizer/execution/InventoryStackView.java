package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import net.minecraft.world.item.Item;

public record InventoryStackView(int playerInventorySlot, Item item, int count) {
    public InventoryStackView {
        if (playerInventorySlot < 0 || playerInventorySlot > 35) {
            throw new IllegalArgumentException("playerInventorySlot must be in [0, 35]");
        }
        Objects.requireNonNull(item, "item");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
    }

    public boolean isHotbar() {
        return playerInventorySlot < 9;
    }

    public boolean isMainInventory() {
        return playerInventorySlot >= 9;
    }
}
