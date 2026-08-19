package dev.pixelied.survival.inventory;

import java.util.Comparator;
import java.util.Optional;

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
}
