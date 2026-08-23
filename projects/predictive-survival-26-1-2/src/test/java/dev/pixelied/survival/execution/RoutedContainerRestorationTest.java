package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutedContainerRestorationTest {
    @Test
    void inverseSwapRestoresOriginalSelectedStackAfterRescueMutation() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot swordBefore = slot(0, "minecraft:diamond_sword", 101, 1);
        InventorySlotSnapshot swordAtSource = slot(10, "minecraft:diamond_sword", 101, 1);
        InventorySlotSnapshot consumedDestination = slot(0, "minecraft:air", 0, 0);
        controller.arm(new RestorationCheckpoint.RoutedContainer(
            7, 10, 10, 0, 0,
            swordBefore, swordAtSource, consumedDestination,
            12, 100
        ));

        InventorySnapshot afterRescue = inventory(0, swordAtSource, consumedDestination);
        assertTrue(controller.update(true, false, false, context(afterRescue, 12, 101)).isEmpty());
        assertTrue(controller.update(true, false, false, context(afterRescue, 12, 102)).isEmpty());
        ExecutionCommand.SwapMenuSlot inverse = assertInstanceOf(
            ExecutionCommand.SwapMenuSlot.class,
            controller.update(true, false, false, context(afterRescue, 12, 103)).orElseThrow()
        );
        assertEquals(7, inverse.containerId());
        assertEquals(12, inverse.stateId());
        assertEquals(10, inverse.sourceMenuSlot());
        assertEquals(0, inverse.button());

        InventorySnapshot restored = inventory(
            0,
            slot(10, "minecraft:air", 0, 0),
            slot(0, "minecraft:diamond_sword", 101, 1)
        );
        assertTrue(controller.update(true, false, false, context(restored, 13, 104)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void changedSourceStackAbortsInsteadOfOverwritingInventory() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot swordBefore = slot(0, "minecraft:diamond_sword", 101, 1);
        controller.arm(new RestorationCheckpoint.RoutedContainer(
            7, 10, 10, 0, 0,
            swordBefore,
            slot(10, "minecraft:diamond_sword", 101, 1),
            slot(0, "minecraft:air", 0, 0),
            12, 100
        ));

        InventorySnapshot changed = inventory(
            0,
            slot(10, "minecraft:netherite_sword", 999, 1),
            slot(0, "minecraft:air", 0, 0)
        );
        assertTrue(controller.update(true, false, false, context(changed, 12, 101)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    private static ExecutionContext context(InventorySnapshot inventory, int stateId, long tick) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, stateId, Map.of(0, 36, 10, 10, 40, 45)),
            new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true
        );
    }

    private static InventorySnapshot inventory(
        int selected,
        InventorySlotSnapshot source,
        InventorySlotSnapshot destination
    ) {
        return new InventorySnapshot(
            selected,
            Map.of(
                0, destination,
                10, source,
                40, slot(40, "minecraft:air", 0, 0)
            ),
            false
        );
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, count, false,
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
