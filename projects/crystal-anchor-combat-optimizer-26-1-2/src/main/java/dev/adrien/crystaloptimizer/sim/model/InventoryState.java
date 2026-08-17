package dev.adrien.crystaloptimizer.sim.model;

import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;

public record InventoryState(
    int selectedHotbarSlot,
    Map<Item, Integer> knownCounts
) {
    public InventoryState {
        if (selectedHotbarSlot < 0 || selectedHotbarSlot > 8) {
            throw new IllegalArgumentException("selectedHotbarSlot must be in [0, 8]");
        }
        Objects.requireNonNull(knownCounts, "knownCounts");
        knownCounts = Map.copyOf(knownCounts);
        knownCounts.forEach((item, count) -> {
            Objects.requireNonNull(item, "item");
            if (count == null || count < 0) {
                throw new IllegalArgumentException("known item counts must be non-negative");
            }
        });
    }

    public static InventoryState empty() {
        return new InventoryState(0, Map.of());
    }

    public int count(Item item) {
        return knownCounts.getOrDefault(item, 0);
    }
}
