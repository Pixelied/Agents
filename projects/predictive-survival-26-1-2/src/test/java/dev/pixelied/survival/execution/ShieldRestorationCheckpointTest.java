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

class ShieldRestorationCheckpointTest {
    @Test
    void confirmedHotbarShieldRouteExposesExactRestorationCheckpointOnce() {
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalItemRoute.HotbarSelect route = new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );
        SurvivalAction.RaiseShield action = new SurvivalAction.RaiseShield(
            6, true, true, true, 1d, 0f, 0, 5, 2,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            ))
        );

        executor.begin(action, context(inventory(0), 100, false, null, 0));
        executor.observe(context(inventory(2), 101, false, null, 0));
        executor.observe(context(inventory(2), 102, true, SurvivalAction.Hand.MAIN_HAND, 5));

        RestorationCheckpoint.Hotbar checkpoint = assertInstanceOf(
            RestorationCheckpoint.Hotbar.class,
            executor.takeRestorationCheckpoint().orElseThrow()
        );
        assertEquals(0, checkpoint.originalSelectedIndex());
        assertEquals(2, checkpoint.protectionHotbarIndex());
        assertEquals("minecraft:diamond_sword", checkpoint.originalSelectedBefore().stackKey());
        assertEquals("minecraft:shield", checkpoint.protectionAfter().stackKey());
        assertEquals(333, checkpoint.protectionAfter().componentFingerprint());
        assertTrue(executor.takeRestorationCheckpoint().isEmpty());
    }

    private static ExecutionContext context(
        InventorySnapshot inventory,
        long serverTick,
        boolean using,
        SurvivalAction.Hand hand,
        int useTicks
    ) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(7, (int) (serverTick - 90), Map.of(0, 36, 2, 38, 40, 45)),
            new TimingSnapshot(serverTick, 50d, 0d, new TickWindow(serverTick + 1, serverTick + 1)),
            serverTick,
            using,
            hand,
            useTicks,
            true
        );
    }

    private static InventorySnapshot inventory(int selected) {
        return new InventorySnapshot(
            selected,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:shield", 333, 1, false,
                    Optional.empty(), Optional.empty(), Optional.of(BlockingProfileSnapshot.fullBlock(336))),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, 0, false,
                    Optional.empty(), Optional.empty(), Optional.empty())
            ),
            false
        );
    }
}
