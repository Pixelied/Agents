package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualUserIntentRestorationTest {
    @Test
    void newerUserSelectionCancelsFutureAutomaticRestoreIntent() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", 11, false);
        InventorySlotSnapshot totem = slot(1, "minecraft:totem_of_undying", 22, true);
        InventorySlotSnapshot pickaxe = slot(2, "minecraft:diamond_pickaxe", 33, false);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, sword, totem, 100));

        observeUserSelection(totem, pickaxe, 101);

        for (long tick = 101; tick <= 140; tick++) {
            assertTrue(controller.update(
                true,
                false,
                false,
                context(inventory(1, sword, totem, pickaxe), tick)
            ).isEmpty(), "stale restoration must never fire after newer manual slot intent");
        }
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void userSelectionDoesNotPretendAnAlreadySentRestorePacketWasCancelled() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", 11, false);
        InventorySlotSnapshot totem = slot(1, "minecraft:totem_of_undying", 22, true);
        InventorySlotSnapshot pickaxe = slot(2, "minecraft:diamond_pickaxe", 33, false);
        InventorySnapshot protectedInventory = inventory(1, sword, totem, pickaxe);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, sword, totem, 100));

        ExecutionCommand.SelectHotbar restore = null;
        long restoreDispatchedAt = -1L;
        for (long tick = 101; tick <= 130; tick++) {
            Optional<ExecutionCommand> command = controller.update(true, false, false, context(protectedInventory, tick));
            if (command.isPresent()) {
                restore = assertInstanceOf(ExecutionCommand.SelectHotbar.class, command.orElseThrow());
                restoreDispatchedAt = tick;
                break;
            }
        }
        if (restore == null) throw new AssertionError("fixture never dispatched the automatic restore");

        long manualIntentTick = restoreDispatchedAt + 1L;
        observeUserSelection(totem, pickaxe, manualIntentTick);
        assertTrue(controller.update(true, false, false, context(protectedInventory, manualIntentTick)).isEmpty());
        assertTrue(controller.hasPendingRestoration(),
            "already-sent restore must stay pending until server evidence resolves packet order");

        long confirmationTick = restoreDispatchedAt + 2L;
        assertTrue(controller.update(
            true,
            false,
            false,
            context(inventory(0, sword, totem, pickaxe), confirmationTick)
        ).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    private static void observeUserSelection(
        InventorySlotSnapshot before,
        InventorySlotSnapshot after,
        long epoch
    ) {
        new PendingEquipmentMutation(
            SurvivalAction.Hand.MAIN_HAND,
            before,
            after,
            new TickWindow(epoch + 1, epoch + 2),
            PendingEquipmentMutation.Origin.USER,
            epoch
        );
    }

    private static ExecutionContext context(InventorySnapshot inventory, long tick) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 0, Map.of()),
            new TimingSnapshot(tick, 100, 10, new TickWindow(tick + 2, tick + 2)),
            tick,
            false,
            null,
            0,
            true
        );
    }

    private static InventorySnapshot inventory(
        int selected,
        InventorySlotSnapshot sword,
        InventorySlotSnapshot totem,
        InventorySlotSnapshot pickaxe
    ) {
        return new InventorySnapshot(selected, Map.of(
            0, sword,
            1, totem,
            2, pickaxe,
            40, slot(40, "minecraft:air", 0, false)
        ), false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, boolean protection) {
        return new InventorySlotSnapshot(
            index,
            key,
            fingerprint,
            "minecraft:air".equals(key) ? 0 : 1,
            protection,
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }
}
