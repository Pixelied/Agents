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
            true
        );
    }

    private static InventorySnapshot inventory(int selected, int shieldFingerprint) {
        return new InventorySnapshot(
            selected,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:shield", shieldFingerprint, 1, false,
                    Optional.empty(), Optional.empty(), Optional.of(BlockingProfileSnapshot.fullBlock(336))),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, 0, false,
                    Optional.empty(), Optional.empty(), Optional.empty())
            ),
            false
        );
    }
}
