package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionHoldLeaseRestorationControllerTest {
    @Test
    void oneSafeFrameCannotRestoreBeforeTimingDerivedHoldLeaseExpires() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        InventorySnapshot protectedInventory = inventory(1, original, protection);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(true, true, false, context(protectedInventory, 101)).isEmpty());
        for (long tick = 102; tick <= 109; tick++) {
            assertTrue(controller.update(true, false, false, context(protectedInventory, tick)).isEmpty(),
                "restoration must remain blocked through timing-derived release uncertainty at tick " + tick);
        }

        ExecutionCommand.SelectHotbar restore = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(protectedInventory, 110)).orElseThrow()
        );
        assertEquals(0, restore.hotbarIndex());
    }

    @Test
    void renewedDangerRestartsContinuousSafeEvidenceWindow() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        InventorySnapshot protectedInventory = inventory(1, original, protection);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(true, true, false, context(protectedInventory, 101)).isEmpty());
        assertTrue(controller.update(true, false, false, context(protectedInventory, 102)).isEmpty());
        assertTrue(controller.update(true, false, false, context(protectedInventory, 103)).isEmpty());
        assertTrue(controller.update(true, true, false, context(protectedInventory, 104)).isEmpty());

        for (long tick = 105; tick <= 112; tick++) {
            assertTrue(controller.update(true, false, false, context(protectedInventory, tick)).isEmpty(),
                "renewed danger must invalidate the earlier safe interval at tick " + tick);
        }
        ExecutionCommand.SelectHotbar restore = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(protectedInventory, 113)).orElseThrow()
        );
        assertEquals(0, restore.hotbarIndex());
    }

    private static ExecutionContext context(InventorySnapshot inventory, long tick) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, 11, Map.of(0, 36, 1, 37, 40, 45)),
            new TimingSnapshot(tick, 100.0d, 10.0d, new TickWindow(tick + 2L, tick + 2L)),
            tick,
            false,
            null,
            0,
            true
        );
    }

    private static InventorySnapshot inventory(
        int selected,
        InventorySlotSnapshot original,
        InventorySlotSnapshot protection
    ) {
        Map<Integer, InventorySlotSnapshot> slots = new HashMap<>();
        slots.put(0, original);
        slots.put(1, protection);
        slots.put(40, slot(40, "minecraft:air", 0, 0, false));
        return new InventorySnapshot(selected, slots, false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count, boolean protection) {
        return new InventorySlotSnapshot(
            index,
            key,
            fingerprint,
            count,
            protection,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
