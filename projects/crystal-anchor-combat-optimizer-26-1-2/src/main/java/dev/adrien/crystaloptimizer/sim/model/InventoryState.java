package dev.adrien.crystaloptimizer.sim.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.Item;

public record InventoryState(
    int selectedHotbarSlot,
    Map<Item, Integer> knownCounts,
    Map<Integer, Item> hotbarItems,
    Optional<Item> offhandItem
) {
    public InventoryState(int selectedHotbarSlot, Map<Item, Integer> knownCounts) {
        this(selectedHotbarSlot, knownCounts, Map.of(), Optional.empty());
    }

    public InventoryState {
        if (selectedHotbarSlot < 0 || selectedHotbarSlot > 8) {
            throw new IllegalArgumentException("selectedHotbarSlot must be in [0, 8]");
        }
        Objects.requireNonNull(knownCounts, "knownCounts");
        Objects.requireNonNull(hotbarItems, "hotbarItems");
        Objects.requireNonNull(offhandItem, "offhandItem");

        knownCounts = Map.copyOf(knownCounts);
        knownCounts.forEach((item, count) -> {
            Objects.requireNonNull(item, "item");
            if (count == null || count < 0) {
                throw new IllegalArgumentException("known item counts must be non-negative");
            }
        });

        LinkedHashMap<Integer, Item> hotbarCopy = new LinkedHashMap<>();
        hotbarItems.forEach((slot, item) -> {
            if (slot == null || slot < 0 || slot > 8) {
                throw new IllegalArgumentException("hotbar slot keys must be in [0, 8]");
            }
            hotbarCopy.put(slot, Objects.requireNonNull(item, "hotbar item"));
        });
        hotbarItems = Map.copyOf(hotbarCopy);
    }

    public static InventoryState empty() {
        return new InventoryState(0, Map.of(), Map.of(), Optional.empty());
    }

    public int count(Item item) {
        return knownCounts.getOrDefault(item, 0);
    }

    public Optional<Item> selectedItem() {
        return Optional.ofNullable(hotbarItems.get(selectedHotbarSlot));
    }

    public boolean hasItemInEitherHand(Item item) {
        return selectedItem().filter(item::equals).isPresent() || offhandItem.filter(item::equals).isPresent();
    }

    public InventoryState withSelectedHotbarSlot(int slot) {
        return new InventoryState(slot, knownCounts, hotbarItems, offhandItem);
    }

    public InventoryState consume(Item item, int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (amount == 0) {
            return this;
        }
        int available = count(item);
        if (available < amount) {
            throw new IllegalStateException("Cannot consume " + amount + " of item with only " + available + " known");
        }

        int remaining = available - amount;
        LinkedHashMap<Item, Integer> nextCounts = new LinkedHashMap<>(knownCounts);
        if (remaining == 0) {
            nextCounts.remove(item);
        } else {
            nextCounts.put(item, remaining);
        }

        Map<Integer, Item> nextHotbar = hotbarItems;
        Optional<Item> nextOffhand = offhandItem;
        if (remaining == 0) {
            LinkedHashMap<Integer, Item> filtered = new LinkedHashMap<>();
            hotbarItems.forEach((slot, held) -> {
                if (!held.equals(item)) {
                    filtered.put(slot, held);
                }
            });
            nextHotbar = Map.copyOf(filtered);
            if (offhandItem.filter(item::equals).isPresent()) {
                nextOffhand = Optional.empty();
            }
        }

        return new InventoryState(selectedHotbarSlot, nextCounts, nextHotbar, nextOffhand);
    }
}
