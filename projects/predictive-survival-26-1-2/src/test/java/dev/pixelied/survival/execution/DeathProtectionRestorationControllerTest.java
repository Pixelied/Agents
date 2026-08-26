package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionRestorationControllerTest {
    @Test
    void hotbarRestoreWaitsForStableSafeServerWindow() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(true, true, false, context(inventory(1, original, protection), 101, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(inventory(1, original, protection), 102, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(inventory(1, original, protection), 103, false)).isEmpty());
        ExecutionCommand.SelectHotbar restore = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(inventory(1, original, protection), 104, false)).orElseThrow()
        );
        assertEquals(0, restore.hotbarIndex());

        assertTrue(controller.update(true, false, false, context(inventory(0, original, protection), 105, false)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void opportunityRefreshKeepsRestorationGraceResetUntilRiskIsGone() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        InventorySnapshot protectedInventory = inventory(1, original, protection);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(true, true, false, context(protectedInventory, 101, false)).isEmpty());
        assertTrue(controller.update(true, true, false, context(protectedInventory, 102, false)).isEmpty());

        assertTrue(controller.update(true, false, false, context(protectedInventory, 103, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(protectedInventory, 104, false)).isEmpty());

        assertTrue(controller.update(true, true, false, context(protectedInventory, 105, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(protectedInventory, 106, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(protectedInventory, 107, false)).isEmpty());

        ExecutionCommand.SelectHotbar restore = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(protectedInventory, 108, false)).orElseThrow()
        );
        assertEquals(0, restore.hotbarIndex());
    }

    @Test
    void chainedHotbarEmergencyRoutesComposeBackToOriginalSelection() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot shield = slot(2, "minecraft:shield", 22, 1, false);
        InventorySlotSnapshot potion = slot(3, "minecraft:potion", 33, 1, false);

        controller.arm(new RestorationCheckpoint.Hotbar(0, 2, sword, shield, 100));
        controller.arm(new RestorationCheckpoint.Hotbar(2, 3, shield, potion, 101));

        InventorySnapshot chained = inventory(3, Map.of(0, sword, 2, shield, 3, potion));
        assertTrue(controller.update(true, false, false, context(chained, 102, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(chained, 103, false)).isEmpty());
        ExecutionCommand.SelectHotbar restore = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            controller.update(true, false, false, context(chained, 104, false)).orElseThrow()
        );
        assertEquals(0, restore.hotbarIndex(),
            "a shield -> potion -> totem style rescue chain must restore the player's original slot, not an intermediate rescue slot");
    }

    @Test
    void activeUseAndSurvivalWorkPreventRestoration() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(true, false, false, context(inventory(1, original, protection), 101, true)).isEmpty());
        assertTrue(controller.update(true, false, true, context(inventory(1, original, protection), 104, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(inventory(1, original, protection), 105, false)).isEmpty());
        assertTrue(controller.hasPendingRestoration());
    }

    @Test
    void consumedOrCorrectedProtectionAbortsInsteadOfOverridingPlayerState() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        InventorySlotSnapshot consumed = slot(1, "minecraft:air", 0, 0, false);
        assertTrue(controller.update(true, false, false, context(inventory(1, original, consumed), 101, false)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void disabledSettingDiscardsAutomaticRestore() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot original = slot(0, "minecraft:diamond_sword", 11, 1, false);
        InventorySlotSnapshot protection = slot(1, "minecraft:totem_of_undying", 22, 1, true);
        controller.arm(new RestorationCheckpoint.Hotbar(0, 1, original, protection, 100));

        assertTrue(controller.update(false, false, false, context(inventory(1, original, protection), 200, false)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void containerSwapRestoresOnlyAfterExactReconcileAndConfirmsInverseRevision() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot totemBefore = slot(17, "minecraft:totem_of_undying", 31, 1, true);
        InventorySlotSnapshot shieldBefore = slot(40, "minecraft:shield", 41, 1, false);
        DeathProtectionRoute.ContainerSwap route = new DeathProtectionRoute.ContainerSwap(17, 40, DeathProtectionRoute.Destination.OFF_HAND);
        EmergencyInventoryTransaction transaction = EmergencyInventoryTransaction.planned(
            route, 7, 10, totemBefore, shieldBefore, 100, 120
        ).markSent().observeStateIdMismatch().reconcile(shieldBefore, totemBefore);
        controller.arm(new RestorationCheckpoint.Container(transaction, 17, 40, 11, 102));

        InventorySnapshot swapped = inventoryContainer(shieldBefore, totemBefore);
        assertTrue(controller.update(true, false, false, context(swapped, menu(7, 11), 103, false)).isEmpty());
        assertTrue(controller.update(true, false, false, context(swapped, menu(7, 11), 104, false)).isEmpty());
        ExecutionCommand.SwapMenuSlot restore = assertInstanceOf(
            ExecutionCommand.SwapMenuSlot.class,
            controller.update(true, false, false, context(swapped, menu(7, 11), 105, false)).orElseThrow()
        );
        assertEquals(7, restore.containerId());
        assertEquals(11, restore.stateId());
        assertEquals(17, restore.sourceMenuSlot());
        assertEquals(40, restore.button());

        assertTrue(controller.update(true, false, false, context(swapped, menu(7, 11), 106, false)).isEmpty());
        InventorySnapshot restored = inventoryContainer(totemBefore, shieldBefore);
        assertTrue(controller.update(true, false, false, context(restored, menu(7, 12), 107, false)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    @Test
    void componentMismatchAbortsContainerRestore() {
        DeathProtectionRestorationController controller = new DeathProtectionRestorationController();
        InventorySlotSnapshot totemBefore = slot(17, "minecraft:totem_of_undying", 31, 1, true);
        InventorySlotSnapshot shieldBefore = slot(40, "minecraft:shield", 41, 1, false);
        DeathProtectionRoute.ContainerSwap route = new DeathProtectionRoute.ContainerSwap(17, 40, DeathProtectionRoute.Destination.OFF_HAND);
        EmergencyInventoryTransaction transaction = EmergencyInventoryTransaction.planned(
            route, 7, 10, totemBefore, shieldBefore, 100, 120
        ).markSent().observeStateIdMismatch().reconcile(shieldBefore, totemBefore);
        controller.arm(new RestorationCheckpoint.Container(transaction, 17, 40, 11, 102));

        InventorySlotSnapshot changedTotem = slot(40, "minecraft:totem_of_undying", 999, 1, true);
        assertTrue(controller.update(true, false, false, context(inventoryContainer(shieldBefore, changedTotem), menu(7, 11), 103, false)).isEmpty());
        assertFalse(controller.hasPendingRestoration());
    }

    private static ExecutionContext context(InventorySnapshot inventory, long tick, boolean using) {
        return context(inventory, menu(7, 11), tick, using);
    }

    private static ExecutionContext context(InventorySnapshot inventory, MenuSlotMap menu, long tick, boolean using) {
        return new ExecutionContext(
            inventory,
            menu,
            new TimingSnapshot(tick, 100, 10, new TickWindow(tick + 2, tick + 2)),
            tick,
            using,
            using ? dev.pixelied.survival.planner.SurvivalAction.Hand.OFF_HAND : null,
            using ? 10 : 0,
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

    private static InventorySnapshot inventory(int selected, Map<Integer, InventorySlotSnapshot> supplied) {
        Map<Integer, InventorySlotSnapshot> slots = new HashMap<>(supplied);
        slots.putIfAbsent(40, slot(40, "minecraft:air", 0, 0, false));
        return new InventorySnapshot(selected, slots, false);
    }

    private static InventorySnapshot inventoryContainer(
        InventorySlotSnapshot source,
        InventorySlotSnapshot destination
    ) {
        Map<Integer, InventorySlotSnapshot> slots = new HashMap<>();
        slots.put(0, slot(0, "minecraft:stone", 51, 1, false));
        slots.put(17, withIndex(source, 17));
        slots.put(40, withIndex(destination, 40));
        return new InventorySnapshot(0, slots, false);
    }

    private static InventorySlotSnapshot withIndex(InventorySlotSnapshot slot, int index) {
        return new InventorySlotSnapshot(
            index, slot.stackKey(), slot.componentFingerprint(), slot.count(), slot.deathProtection(),
            slot.consumable(), slot.equippable(), slot.blockingProfile()
        );
    }

    private static MenuSlotMap menu(int container, int state) {
        return new MenuSlotMap(container, state, Map.of(0, 36, 17, 17, 40, 45));
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count, boolean protection) {
        return new InventorySlotSnapshot(index, key, fingerprint, count, protection, Optional.empty(), Optional.empty(), Optional.empty());
    }
}
