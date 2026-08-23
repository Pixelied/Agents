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

class MixedRestorationSequenceTest {
    @Test
    void containerThenHotbarEmergencyChainRestoresInReverseOrderToTrueOriginalSlot() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot sword0 = slot(0, "minecraft:diamond_sword", 101, 1);
        InventorySlotSnapshot sword10 = slot(10, "minecraft:diamond_sword", 101, 1);
        InventorySlotSnapshot shield0 = slot(0, "minecraft:shield", 202, 1);
        InventorySlotSnapshot shield10 = slot(10, "minecraft:shield", 202, 1);
        InventorySlotSnapshot totem2 = slot(2, "minecraft:totem_of_undying", 303, 1);

        controller.arm(new RestorationCheckpoint.RoutedContainer(
            7, 10, 10, 0, 0,
            sword0, sword10, shield0,
            11, 100
        ));
        controller.arm(new RestorationCheckpoint.Hotbar(
            0, 2, shield0, totem2, 101
        ));

        InventorySnapshot emergency = inventory(2, Map.of(0, shield0, 2, totem2, 10, sword10));
        assertTrue(controller.update(true, false, false, context(emergency, 11, 102)).isEmpty());
        ExecutionCommand.SelectHotbar select = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(emergency, 11, 103)).orElseThrow()
        );
        assertEquals(0, select.hotbarIndex());

        InventorySnapshot selectedShield = inventory(0, Map.of(0, shield0, 2, totem2, 10, sword10));
        assertTrue(controller.update(true, false, false, context(selectedShield, 11, 104)).isEmpty());
        assertTrue(controller.hasPendingRestoration(),
            "restoring the hotbar selection must reveal the earlier container mutation instead of discarding it");

        assertTrue(controller.update(true, false, false, context(selectedShield, 11, 105)).isEmpty());
        ExecutionCommand.SwapMenuSlot inverse = assertInstanceOf(
            ExecutionCommand.SwapMenuSlot.class,
            controller.update(true, false, false, context(selectedShield, 11, 106)).orElseThrow()
        );
        assertEquals(10, inverse.sourceMenuSlot());
        assertEquals(0, inverse.button());

        InventorySnapshot fullyRestored = inventory(0, Map.of(0, sword0, 2, totem2, 10, shield10));
        assertTrue(controller.update(true, false, false, context(fullyRestored, 12, 107)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    private static ExecutionContext context(InventorySnapshot inventory, int stateId, long tick) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, stateId, Map.of(0, 36, 2, 38, 10, 10, 40, 45)),
            new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
            tick, false, null, 0, true
        );
    }

    private static InventorySnapshot inventory(int selected, Map<Integer, InventorySlotSnapshot> supplied) {
        java.util.HashMap<Integer, InventorySlotSnapshot> slots = new java.util.HashMap<>(supplied);
        slots.putIfAbsent(40, slot(40, "minecraft:air", 0, 0));
        return new InventorySnapshot(selected, slots, false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, count, "minecraft:totem_of_undying".equals(key),
            Optional.empty(), Optional.empty(), Optional.empty()
        );
    }
}
