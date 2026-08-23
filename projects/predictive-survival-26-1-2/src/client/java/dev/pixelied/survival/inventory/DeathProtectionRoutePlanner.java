package dev.pixelied.survival.inventory;

import java.util.Comparator;
import java.util.Optional;
import java.util.Set;

public final class DeathProtectionRoutePlanner {
    public Optional<DeathProtectionRoute> choose(InventorySnapshot inventory, MenuSlotMap menu) {
        var selected = inventory.slot(inventory.selectedHotbarIndex());
        if (selected.isPresent() && selected.get().deathProtection()) {
            return Optional.of(new DeathProtectionRoute.AlreadyInHand(DeathProtectionRoute.Destination.MAIN_HAND));
        }

        var offhand = inventory.slot(40);
        if (offhand.isPresent() && offhand.get().deathProtection()) {
            return Optional.of(new DeathProtectionRoute.AlreadyInHand(DeathProtectionRoute.Destination.OFF_HAND));
        }

        for (int hotbar = 0; hotbar <= 8; hotbar++) {
            if (hotbar == inventory.selectedHotbarIndex()) continue;
            var slot = inventory.slot(hotbar);
            if (slot.isPresent() && slot.get().deathProtection()) {
                return Optional.of(new DeathProtectionRoute.HotbarSelect(hotbar));
            }
        }

        var source = inventory.slots().values().stream()
            .filter(slot -> slot.inventoryIndex() >= 9 && slot.inventoryIndex() <= 35)
            .filter(InventorySlotSnapshot::deathProtection)
            .min(Comparator.comparingInt(InventorySlotSnapshot::inventoryIndex));
        if (source.isEmpty()) return Optional.empty();

        var sourceMenuSlot = menu.menuSlotForInventoryIndex(source.get().inventoryIndex());
        if (sourceMenuSlot.isEmpty()) return Optional.empty();

        if (inventory.activeOffhandShield()) {
            if (menu.menuSlotForInventoryIndex(inventory.selectedHotbarIndex()).isEmpty()) return Optional.empty();
            return Optional.of(new DeathProtectionRoute.ContainerSwap(
                sourceMenuSlot.getAsInt(),
                inventory.selectedHotbarIndex(),
                DeathProtectionRoute.Destination.MAIN_HAND
            ));
        }

        if (menu.menuSlotForInventoryIndex(40).isEmpty()) return Optional.empty();
        return Optional.of(new DeathProtectionRoute.ContainerSwap(
            sourceMenuSlot.getAsInt(),
            40,
            DeathProtectionRoute.Destination.OFF_HAND
        ));
    }

    /**
     * Chooses a route for a specific hand without treating protection already held in the other
     * hand as satisfying the request. This is used for proactive stacked-hit protection.
     */
    public Optional<DeathProtectionRoute> choose(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        DeathProtectionRoute.Destination destination
    ) {
        return choose(inventory, menu, destination, Set.of());
    }

    /** Chooses a specific-hand route while reserving already-claimed physical source slots. */
    public Optional<DeathProtectionRoute> choose(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        DeathProtectionRoute.Destination destination,
        Set<Integer> excludedSourceInventoryIndices
    ) {
        Set<Integer> excluded = Set.copyOf(excludedSourceInventoryIndices);
        int selectedIndex = inventory.selectedHotbarIndex();
        int destinationIndex = destination == DeathProtectionRoute.Destination.MAIN_HAND ? selectedIndex : 40;
        var destinationSlot = inventory.slot(destinationIndex);
        if (!excluded.contains(destinationIndex)
            && destinationSlot.isPresent()
            && destinationSlot.get().deathProtection()) {
            return Optional.of(new DeathProtectionRoute.AlreadyInHand(destination));
        }

        if (destination == DeathProtectionRoute.Destination.MAIN_HAND) {
            for (int hotbar = 0; hotbar <= 8; hotbar++) {
                if (hotbar == selectedIndex || excluded.contains(hotbar)) continue;
                var slot = inventory.slot(hotbar);
                if (slot.isPresent() && slot.get().deathProtection()) {
                    return Optional.of(new DeathProtectionRoute.HotbarSelect(hotbar));
                }
            }
        }

        var source = inventory.slots().values().stream()
            .filter(slot -> !excluded.contains(slot.inventoryIndex()))
            .filter(slot -> slot.inventoryIndex() != destinationIndex)
            .filter(slot -> slot.inventoryIndex() != (destination == DeathProtectionRoute.Destination.MAIN_HAND ? 40 : selectedIndex))
            .filter(InventorySlotSnapshot::deathProtection)
            .filter(slot -> menu.menuSlotForInventoryIndex(slot.inventoryIndex()).isPresent())
            .min(Comparator
                .comparingInt((InventorySlotSnapshot slot) -> slot.inventoryIndex() >= 0 && slot.inventoryIndex() <= 8 ? 0 : 1)
                .thenComparingInt(InventorySlotSnapshot::inventoryIndex));
        if (source.isEmpty()) return Optional.empty();

        var sourceMenuSlot = menu.menuSlotForInventoryIndex(source.get().inventoryIndex());
        if (sourceMenuSlot.isEmpty()) return Optional.empty();
        if (menu.menuSlotForInventoryIndex(destinationIndex).isEmpty()) return Optional.empty();
        return Optional.of(new DeathProtectionRoute.ContainerSwap(
            sourceMenuSlot.getAsInt(),
            destination == DeathProtectionRoute.Destination.OFF_HAND ? 40 : selectedIndex,
            destination
        ));
    }
}
