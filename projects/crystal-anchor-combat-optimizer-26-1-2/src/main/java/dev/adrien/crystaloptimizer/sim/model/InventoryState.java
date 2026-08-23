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
    Optional<Item> offhandItem,
    int offhandCount
) {
    public InventoryState(int selectedHotbarSlot, Map<Item, Integer> knownCounts) {
        this(selectedHotbarSlot, knownCounts, Map.of(), Map.of(), Optional.empty(), 0);
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

    /**
     * Compatibility constructor for older snapshots and tests that tracked offhand identity but
     * not its exact stack size. Live capture paths use the six-argument constructor.
     */
    public InventoryState(
        int selectedHotbarSlot,
        Map<Item, Integer> knownCounts,
        Map<Integer, Item> hotbarItems,
        Map<Integer, Integer> hotbarCounts,
        Optional<Item> offhandItem
    ) {
        this(
            selectedHotbarSlot,
            knownCounts,
            hotbarItems,
            hotbarCounts,
            offhandItem,
            inferLegacyOffhandCount(knownCounts, hotbarItems, hotbarCounts, offhandItem)
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
        if (offhandCount < 0) {
            throw new IllegalArgumentException("offhandCount must be non-negative");
        }
        if (offhandItem.isEmpty() && offhandCount != 0) {
            throw new IllegalArgumentException("empty offhand cannot have a positive stack count");
        }
        if (offhandItem.isPresent() && offhandCount <= 0) {
            throw new IllegalArgumentException("present offhand item must have a positive stack count");
        }

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

        if (offhandItem.isPresent()
            && countsCopy.getOrDefault(offhandItem.orElseThrow(), 0) < offhandCount) {
            throw new IllegalArgumentException("offhand stack exceeds aggregate known item count");
        }

        hotbarItems = Map.copyOf(hotbarCopy);
        hotbarCounts = Map.copyOf(hotbarCountCopy);
    }

    public static InventoryState empty() {
        return new InventoryState(0, Map.of(), Map.of(), Map.of(), Optional.empty(), 0);
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
        return selectedItem().filter(item::equals).isPresent()
            || (offhandCount > 0 && offhandItem.filter(item::equals).isPresent());
    }

    public InventoryState withSelectedHotbarSlot(int slot) {
        return new InventoryState(
            slot,
            knownCounts,
            hotbarItems,
            hotbarCounts,
            offhandItem,
            offhandCount
        );
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
            throw new IllegalStateException(
                "Cannot consume " + amount + " of item with only " + available + " known"
            );
        }

        int heldAvailable = 0;
        if (offhandItem.filter(item::equals).isPresent()) {
            heldAvailable += offhandCount;
        }
        if (selectedItem().filter(item::equals).isPresent()) {
            heldAvailable += hotbarCount(selectedHotbarSlot);
        }
        if (heldAvailable < amount) {
            throw new IllegalStateException(
                "Cannot consume " + amount + " from interaction hands with only " + heldAvailable + " held"
            );
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
        int nextOffhandCount = offhandCount;
        int stillNeeded = amount;

        // Live routing prefers a matching offhand interaction item, so simulation must do the same.
        if (stillNeeded > 0 && offhandItem.filter(item::equals).isPresent()) {
            int consumedFromOffhand = Math.min(stillNeeded, offhandCount);
            nextOffhandCount -= consumedFromOffhand;
            stillNeeded -= consumedFromOffhand;
            if (nextOffhandCount == 0) {
                nextOffhand = Optional.empty();
            }
        }

        if (stillNeeded > 0 && selectedItem().filter(item::equals).isPresent()) {
            int selectedCount = hotbarCount(selectedHotbarSlot);
            if (selectedCount < stillNeeded) {
                throw new IllegalStateException(
                    "Cannot consume " + stillNeeded
                        + " from selected hotbar stack with only " + selectedCount
                );
            }
            int selectedRemaining = selectedCount - stillNeeded;
            if (selectedRemaining == 0) {
                nextHotbar.remove(selectedHotbarSlot);
                nextHotbarCounts.remove(selectedHotbarSlot);
            } else {
                nextHotbarCounts.put(selectedHotbarSlot, selectedRemaining);
            }
            stillNeeded = 0;
        }

        if (stillNeeded != 0) {
            throw new IllegalStateException("interaction-hand consumption was not fully satisfied");
        }

        if (remaining == 0) {
            nextHotbar.entrySet().removeIf(entry -> entry.getValue().equals(item));
            nextHotbarCounts.keySet().retainAll(nextHotbar.keySet());
            if (nextOffhand.filter(item::equals).isPresent()) {
                nextOffhand = Optional.empty();
                nextOffhandCount = 0;
            }
        }

        return new InventoryState(
            selectedHotbarSlot,
            nextCounts,
            nextHotbar,
            nextHotbarCounts,
            nextOffhand,
            nextOffhandCount
        );
    }

    private static int inferLegacyOffhandCount(
        Map<Item, Integer> knownCounts,
        Map<Integer, Item> hotbarItems,
        Map<Integer, Integer> hotbarCounts,
        Optional<Item> offhandItem
    ) {
        Objects.requireNonNull(knownCounts, "knownCounts");
        Objects.requireNonNull(hotbarItems, "hotbarItems");
        Objects.requireNonNull(hotbarCounts, "hotbarCounts");
        Objects.requireNonNull(offhandItem, "offhandItem");
        if (offhandItem.isEmpty()) {
            return 0;
        }

        Item item = offhandItem.orElseThrow();
        int total = knownCounts.getOrDefault(item, 0);
        if (total <= 0) {
            return 0;
        }
        long exactHotbar = hotbarItems.entrySet().stream()
            .filter(entry -> entry.getValue().equals(item))
            .mapToLong(entry -> hotbarCounts.getOrDefault(entry.getKey(), 0))
            .sum();
        long residual = (long) total - exactHotbar;
        if (residual <= 0L) {
            // Legacy snapshots could not distinguish reserve inventory from offhand quantity.
            return 1;
        }
        return residual >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) residual;
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
