package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import net.minecraft.world.item.Item;

public record RestockDecision(int sourceInventorySlot, int hotbarSlot, Item item) {
    public RestockDecision {
        if (sourceInventorySlot < 9 || sourceInventorySlot > 35) {
            throw new IllegalArgumentException("sourceInventorySlot must be a main-inventory slot in [9, 35]");
        }
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("hotbarSlot must be in [0, 8]");
        }
        Objects.requireNonNull(item, "item");
    }
}
