package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldInventoryRoutingExecutorTest {
    @Test
    void hotbarShieldRouteSelectsExactStackBeforeUsingIt() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalItemRoute.HotbarSelect route = new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );
        SurvivalAction.RaiseShield action = routedShield(route);

        ExecutionStatus.WaitingForServer begin = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context(inventory(0, 333), 100, false, null, 0, 4))
        );
        ExecutionCommand.SelectHotbar select = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            begin.command().orElseThrow()
        );
        assertEquals(2, select.hotbarIndex());

        ExecutionStatus.WaitingForServer routed = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(inventory(2, 333), 101, false, null, 0, 5))
        );
        ExecutionCommand.UseItem use = assertInstanceOf(
            ExecutionCommand.UseItem.class,
            routed.command().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, use.hand());

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(inventory(2, 333), 102, true, SurvivalAction.Hand.MAIN_HAND, 5, 6))
        );
    }

    @Test
    void routedShieldFailsClosedWhenExactComponentsChangeBeforeSelectionConfirms() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalItemRoute.HotbarSelect route = new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );
        executor.begin(routedShield(route), context(inventory(0, 333), 200, false, null, 0, 4));

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.observe(context(inventory(2, 999), 201, false, null, 0, 5))
        );
        assertEquals(true, failed.replanRequired());
    }

    @Test
    void silentExactContainerShieldAdvancesOnlyAfterCorrectionWindowSettles() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );

        ExecutionStatus.WaitingForServer begin = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(routedShield(route), containerContext(false, 300, false, null, 0, 4))
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, begin.command().orElseThrow());

        for (long tick : new long[] {301L, 302L}) {
            ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
                ExecutionStatus.WaitingForServer.class,
                executor.observe(containerContext(true, tick, false, null, 0, 4))
            );
            assertTrue(waiting.command().isEmpty(),
                "shield use must not be emitted before every modeled click correction could already have returned");
        }

        ExecutionStatus.WaitingForServer settled = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(containerContext(true, 303, false, null, 0, 4))
        );
        ExecutionCommand.UseItem use = assertInstanceOf(
            ExecutionCommand.UseItem.class,
            settled.command().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.MAIN_HAND, use.hand());
    }

    @Test
    void correctiveContainerShieldRevisionFailsClosedInsteadOfTimingOut() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );
        executor.begin(routedShield(route), containerContext(false, 400, false, null, 0, 4));

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.observe(containerContext(false, 401, false, null, 0, 5))
        );
        assertTrue(failed.replanRequired());
    }

    private static SurvivalAction.RaiseShield routedShield(SurvivalItemRoute route) {
        return new SurvivalAction.RaiseShield(
            6, true, true, true, 1d, 0f, 0, 5, 2,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            ))
        );
    }

    private static ExecutionContext context(
        InventorySnapshot inventory,
        long serverTick,
        boolean using,
        SurvivalAction.Hand usingHand,
        int useTicks,
        int stateId
    ) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, stateId, Map.of(0, 36, 2, 38, 40, 45)),
            new TimingSnapshot(serverTick, 50d, 0d, new TickWindow(serverTick + 1, serverTick + 1)),
            serverTick,
            using,
            usingHand,
            useTicks,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
    }

    private static ExecutionContext containerContext(
        boolean swapped,
        long serverTick,
        boolean using,
        SurvivalAction.Hand usingHand,
        int useTicks,
        int stateId
    ) {
        return new ExecutionContext(
            containerInventory(swapped),
            new MenuSlotMap(7, stateId, Map.of(0, 36, 10, 10, 40, 45)),
            new TimingSnapshot(serverTick, 50d, 0d, new TickWindow(serverTick + 1, serverTick + 1)),
            serverTick,
            using,
            usingHand,
            useTicks,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
    }

    private static InventorySnapshot inventory(int selected, int shieldFingerprint) {
        return new InventorySnapshot(
            selected,
            Map.of(
                0, sword(0),
                2, shield(2, shieldFingerprint),
                40, air(40)
            ),
            false
        );
    }

    private static InventorySnapshot containerInventory(boolean swapped) {
        return new InventorySnapshot(
            0,
            swapped
                ? Map.of(0, shield(0, 333), 10, sword(10), 40, air(40))
                : Map.of(0, sword(0), 10, shield(10, 333), 40, air(40)),
            false
        );
    }

    private static InventorySlotSnapshot sword(int index) {
        return new InventorySlotSnapshot(index, "minecraft:diamond_sword", 101, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static InventorySlotSnapshot shield(int index, int fingerprint) {
        return new InventorySlotSnapshot(index, "minecraft:shield", fingerprint, 1, false,
            Optional.empty(), Optional.empty(), Optional.of(BlockingProfileSnapshot.fullBlock(336)));
    }

    private static InventorySlotSnapshot air(int index) {
        return new InventorySlotSnapshot(index, "minecraft:air", 0, 0, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }
}
