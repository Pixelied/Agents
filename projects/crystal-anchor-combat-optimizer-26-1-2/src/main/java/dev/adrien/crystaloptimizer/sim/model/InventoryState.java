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
    Map<Integer, Integer> hotbarCounts,
    Optional<Item> offhandItem
) {
    public InventoryState(int selectedHotbarSlot, Map<Item, Integer> knownCounts) {
        this(selectedHotbarSlot, knownCounts, Map.of(), Map.of(), Optional.empty());
    }

    public InventoryState(
        int selectedHotbarSlot,
        Map<Item, Integer> knownCounts,
        Map<Integer, Item> hotbarItems,
        Optional<Item> offhandItem
    ) {
        this(
            selectedHotbarSlot,
            knownCounts,
            hotbarItems,
            deriveHotbarCounts(knownCounts, hotbarItems),
            offhandItem
        );
    }

    public InventoryState {
        if (selectedHotbarSlot < 0 || selectedHotbarSlot > 8) {
            throw new IllegalArgumentException("selectedHotbarSlot must be in [0, 8]");
        }
        Objects.requireNonNull(knownCounts, "knownCounts");
        Objects.requireNonNull(hotbarItems, "hotbarItems");
        Objects.requireNonNull(hotbarCounts, "hotbarCounts");
        Objects.requireNonNull(offhandItem, "offhandItem");

        LinkedHashMap<Item, Integer> countsCopy = new LinkedHashMap<>();
        knownCounts.forEach((item, count) -> {
            Objects.requireNonNull(item, "item");
            if (count == null || count < 0) {
                throw new IllegalArgumentException("known item counts must be non-negative");
            }
            if (count > 0) {
                countsCopy.put(item, count);
            }
        });
        knownCounts = Map.copyOf(countsCopy);

        LinkedHashMap<Integer, Item> hotbarCopy = new LinkedHashMap<>();
        hotbarItems.forEach((slot, item) -> {
            if (slot == null || slot < 0 || slot > 8) {
                throw new IllegalArgumentException("hotbar slot keys must be in [0, 8]");
            }
            hotbarCopy.put(slot, Objects.requireNonNull(item, "hotbar item"));
        });

        LinkedHashMap<Integer, Integer> hotbarCountCopy = new LinkedHashMap<>();
        hotbarCounts.forEach((slot, count) -> {
            if (slot == null || slot < 0 || slot > 8) {
                throw new IllegalArgumentException("hotbar count slot keys must be in [0, 8]");
            }
            if (count == null || count <= 0) {
                throw new IllegalArgumentException("present hotbar stack counts must be positive");
            }
            if (!hotbarCopy.containsKey(slot)) {
                throw new IllegalArgumentException("hotbar count has no corresponding hotbar item");
            }
            hotbarCountCopy.put(slot, count);
        });
        if (!hotbarCopy.keySet().equals(hotbarCountCopy.keySet())) {
            throw new IllegalArgumentException("every known hotbar item must have an exact stack count");
        }

        hotbarItems = Map.copyOf(hotbarCopy);
        hotbarCounts = Map.copyOf(hotbarCountCopy);
    }

    public static InventoryState empty() {
        return new InventoryState(0, Map.of(), Map.of(), Map.of(), Optional.empty());
    }

    public int count(Item item) {
        return knownCounts.getOrDefault(item, 0);
    }

    public int hotbarCount(int slot) {
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("hotbar slot must be in [0, 8]");
        }
        return hotbarCounts.getOrDefault(slot, 0);
    }

    public Optional<Item> selectedItem() {
        return Optional.ofNullable(hotbarItems.get(selectedHotbarSlot));
    }

    public boolean hasItemInEitherHand(Item item) {
        return selectedItem().filter(item::equals).isPresent() || offhandItem.filter(item::equals).isPresent();
    }

    public InventoryState withSelectedHotbarSlot(int slot) {
        return new InventoryState(slot, knownCounts, hotbarItems, hotbarCounts, offhandItem);
    }

    public InventoryState consume(Item item, int amount) {
        Objects.requireNonNull(item, "item");
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

        LinkedHashMap<Integer, Item> nextHotbar = new LinkedHashMap<>(hotbarItems);
        LinkedHashMap<Integer, Integer> nextHotbarCounts = new LinkedHashMap<>(hotbarCounts);
        Optional<Item> nextOffhand = offhandItem;

        if (selectedItem().filter(item::equals).isPresent()) {
            int selectedCount = hotbarCount(selectedHotbarSlot);
            if (selectedCount < amount) {
                throw new IllegalStateException(
                    "Cannot consume " + amount + " from selected hotbar stack with only " + selectedCount
                );
            }
            int selectedRemaining = selectedCount - amount;
            if (selectedRemaining == 0) {
                nextHotbar.remove(selectedHotbarSlot);
                nextHotbarCounts.remove(selectedHotbarSlot);
            } else {
                nextHotbarCounts.put(selectedHotbarSlot, selectedRemaining);
            }
        }

        if (remaining == 0) {
            nextHotbar.entrySet().removeIf(entry -> entry.getValue().equals(item));
            nextHotbarCounts.keySet().retainAll(nextHotbar.keySet());
            if (offhandItem.filter(item::equals).isPresent()) {
                nextOffhand = Optional.empty();
            }
        }

        return new InventoryState(
            selectedHotbarSlot,
            nextCounts,
            nextHotbar,
            nextHotbarCounts,
            nextOffhand
        );
    }

    private static Map<Integer, Integer> deriveHotbarCounts(
        Map<Item, Integer> knownCounts,
        Map<Integer, Item> hotbarItems
    ) {
        Objects.requireNonNull(knownCounts, "knownCounts");
        Objects.requireNonNull(hotbarItems, "hotbarItems");
        LinkedHashMap<Integer, Integer> derived = new LinkedHashMap<>();
        hotbarItems.forEach((slot, item) -> {
            int total = knownCounts.getOrDefault(item, 0);
            derived.put(slot, Math.max(1, total));
        });
        return derived;
    }
}
