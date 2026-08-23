package dev.pixelied.survival.inventory;

import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;
import java.util.Optional;

/** Plans a conservative route for one exact inventory stack. */
public final class SurvivalItemRoutePlanner {
    public Optional<SurvivalItemRoute> route(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        InventorySlotSnapshot source,
        boolean inventoryRouting,
        boolean mainHandTakeover
    ) {
        return route(inventory, menu, source, inventoryRouting, mainHandTakeover, 1);
    }

    public Optional<SurvivalItemRoute> route(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        InventorySlotSnapshot source,
        boolean inventoryRouting,
        boolean mainHandTakeover,
        int containerSwapTicks
    ) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(menu, "menu");
        Objects.requireNonNull(source, "source");
        if (containerSwapTicks < 0) throw new IllegalArgumentException("containerSwapTicks must be non-negative");
        if (source.count() <= 0) return Optional.empty();

        if (source.inventoryIndex() == inventory.selectedHotbarIndex()) {
            return Optional.of(new SurvivalItemRoute.AlreadyHeld(
                SurvivalAction.Hand.MAIN_HAND, source.stackKey(), source.componentFingerprint()
            ));
        }
        if (source.inventoryIndex() == 40) {
            return Optional.of(new SurvivalItemRoute.AlreadyHeld(
                SurvivalAction.Hand.OFF_HAND, source.stackKey(), source.componentFingerprint()
            ));
        }
        if (!inventoryRouting || !mainHandTakeover) return Optional.empty();

        if (source.inventoryIndex() >= 0 && source.inventoryIndex() <= 8) {
            return Optional.of(new SurvivalItemRoute.HotbarSelect(
                source.inventoryIndex(), SurvivalAction.Hand.MAIN_HAND,
                source.stackKey(), source.componentFingerprint()
            ));
        }

        var sourceMenu = menu.menuSlotForInventoryIndex(source.inventoryIndex());
        var destinationMenu = menu.menuSlotForInventoryIndex(inventory.selectedHotbarIndex());
        if (sourceMenu.isEmpty() || destinationMenu.isEmpty()) return Optional.empty();
        return Optional.of(new SurvivalItemRoute.ContainerSwap(
            source.inventoryIndex(),
            sourceMenu.getAsInt(),
            inventory.selectedHotbarIndex(),
            inventory.selectedHotbarIndex(),
            SurvivalAction.Hand.MAIN_HAND,
            source.stackKey(),
            source.componentFingerprint(),
            containerSwapTicks
        ));
    }
}
