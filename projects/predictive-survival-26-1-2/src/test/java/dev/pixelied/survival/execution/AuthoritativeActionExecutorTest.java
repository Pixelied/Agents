package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthoritativeActionExecutorTest {
    @Test
    void heldSlotRouteWaitsForObservedServerSelection() {
        DeathProtectionActionExecutor executor = new DeathProtectionActionExecutor();
        SurvivalAction.EquipDeathProtection action = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND,
            1, true, true, 1.0, 1, 1
        );
        ExecutionContext before = context(inventory(0, true, false), false, null, 0, true, 100);

        ExecutionStatus begin = executor.begin(action, before);
        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(ExecutionStatus.WaitingForServer.class, begin);
        ExecutionCommand.SelectHotbar command = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            waiting.command().orElseThrow()
        );
        assertEquals(1, command.hotbarIndex());

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(inventory(0, true, false), false, null, 0, true, 101))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(inventory(1, true, false), false, null, 0, true, 102))
        );
    }

    @Test
    void shieldWarmupWaitsAtFourOfFiveAndConfirmsAtFive() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalAction.RaiseShield action = new SurvivalAction.RaiseShield(
            0, true, true, true, 1.0, 1f, 4, 5, 0
        );

        ExecutionStatus atFour = executor.begin(
            action,
            context(inventory(0, false, true), true, SurvivalAction.Hand.OFF_HAND, 4, true, 200)
        );
        assertInstanceOf(ExecutionStatus.WaitingForServer.class, atFour);

        ExecutionStatus atFive = executor.observe(
            context(inventory(0, false, true), true, SurvivalAction.Hand.OFF_HAND, 5, true, 201)
        );
        assertInstanceOf(ExecutionStatus.Confirmed.class, atFive);
    }

    @Test
    void shieldAngleContradictionFailsAndRequestsReplan() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalAction.RaiseShield action = new SurvivalAction.RaiseShield(
            0, true, true, true, 1.0, 1f, 4, 5, 0
        );
        executor.begin(
            action,
            context(inventory(0, false, true), true, SurvivalAction.Hand.OFF_HAND, 4, true, 300)
        );

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.observe(context(inventory(0, false, true), true, SurvivalAction.Hand.OFF_HAND, 4, false, 301))
        );
        assertTrue(failed.replanRequired());
    }

    @Test
    void containerSwapRequiresStateRevisionAndDestinationContents() {
        DeathProtectionActionExecutor executor = new DeathProtectionActionExecutor();
        SurvivalAction.EquipDeathProtection action = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            1, true, true, 1.0, 1, 1
        );

        InventorySnapshot beforeInventory = inventoryWithMainInventoryTotem(false);
        ExecutionContext before = context(beforeInventory, false, null, 0, true, 400);
        ExecutionStatus.WaitingForServer begin = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, before)
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, begin.command().orElseThrow());

        InventorySnapshot locallyPredicted = inventoryWithMainInventoryTotem(true);
        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(locallyPredicted, menu(7, 10), false, null, 0, true, 401))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(locallyPredicted, menu(7, 11), false, null, 0, true, 402))
        );
    }

    private static ExecutionContext context(
        InventorySnapshot inventory,
        boolean serverUsingItem,
        SurvivalAction.Hand usingHand,
        int serverUseTicks,
        boolean shieldAngleValid,
        long serverTick
    ) {
        return context(inventory, menu(7, 10), serverUsingItem, usingHand, serverUseTicks, shieldAngleValid, serverTick);
    }

    private static ExecutionContext context(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        boolean serverUsingItem,
        SurvivalAction.Hand usingHand,
        int serverUseTicks,
        boolean shieldAngleValid,
        long serverTick
    ) {
        return new ExecutionContext(
            inventory,
            menu,
            new TimingSnapshot(serverTick, 50, 0, new TickWindow(serverTick + 1, serverTick + 1)),
            serverTick,
            serverUsingItem,
            usingHand,
            serverUseTicks,
            shieldAngleValid
        );
    }

    private static InventorySnapshot inventory(int selected, boolean hotbarTotem, boolean offhandShield) {
        Map<Integer, InventorySlotSnapshot> slots = new HashMap<>();
        slots.put(0, slot(0, "minecraft:stone", 1, false));
        slots.put(1, slot(1, hotbarTotem ? "minecraft:totem_of_undying" : "minecraft:stone", 1, hotbarTotem));
        slots.put(40, slot(40, offhandShield ? "minecraft:shield" : "minecraft:stone", 1, false));
        return new InventorySnapshot(selected, slots, offhandShield);
    }

    private static InventorySnapshot inventoryWithMainInventoryTotem(boolean movedToOffhand) {
        Map<Integer, InventorySlotSnapshot> slots = new HashMap<>();
        slots.put(0, slot(0, "minecraft:stone", 1, false));
        slots.put(9, slot(9, movedToOffhand ? "minecraft:stone" : "minecraft:totem_of_undying", 1, !movedToOffhand));
        slots.put(40, slot(40, movedToOffhand ? "minecraft:totem_of_undying" : "minecraft:stone", 1, movedToOffhand));
        return new InventorySnapshot(0, slots, false);
    }

    private static MenuSlotMap menu(int containerId, int stateId) {
        return new MenuSlotMap(containerId, stateId, Map.of(
            0, 36,
            1, 37,
            9, 9,
            40, 45
        ));
    }

    private static InventorySlotSnapshot slot(int index, String key, int count, boolean protection) {
        return new InventorySlotSnapshot(index, key, count, protection);
    }
}
