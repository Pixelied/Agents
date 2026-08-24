package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionExactSourceTest {
    @Test
    void executorUsesPlannedProtectionSourceInsteadOfRechoosingFirstAvailableStack() {
        DeathProtectionActionExecutor executor = new DeathProtectionActionExecutor();
        SurvivalAction.EquipDeathProtection action = actionForHotbarSource(2, 202);

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context(inventory(202), 100))
        );
        ExecutionCommand.SelectHotbar select = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class,
            waiting.command().orElseThrow()
        );

        assertEquals(2, select.hotbarIndex(),
            "execution must honor the exact source selected by planning, not silently substitute slot 1");
    }

    @Test
    void changedPlannedProtectionFingerprintFailsClosedInsteadOfSubstitutingAnotherStack() {
        DeathProtectionActionExecutor executor = new DeathProtectionActionExecutor();
        SurvivalAction.EquipDeathProtection action = actionForHotbarSource(2, 202);

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.begin(action, context(inventory(999), 200))
        );

        assertTrue(failed.replanRequired());
    }

    private static SurvivalAction.EquipDeathProtection actionForHotbarSource(int sourceIndex, int fingerprint) {
        DeathProtectionRoute route = new DeathProtectionRoute.HotbarSelect(sourceIndex);
        SurvivalAction.DeathProtectionSourceRef source = new SurvivalAction.DeathProtectionSourceRef(
            sourceIndex,
            "minecraft:totem_of_undying",
            fingerprint,
            route
        );
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND,
            1,
            true,
            true,
            1d,
            1,
            1,
            Optional.of(source)
        );
    }

    private static InventorySnapshot inventory(int plannedFingerprint) {
        return new InventorySnapshot(
            0,
            Map.of(
                0, slot(0, "minecraft:diamond_sword", 11, false),
                1, slot(1, "minecraft:totem_of_undying", 101, true),
                2, slot(2, "minecraft:totem_of_undying", plannedFingerprint, true),
                40, slot(40, "minecraft:air", 0, false)
            ),
            false
        );
    }

    private static ExecutionContext context(InventorySnapshot inventory, long tick) {
        return new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 4, Map.of(0, 36, 1, 37, 2, 38, 40, 45)),
            new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
    }

    private static InventorySlotSnapshot slot(
        int index,
        String key,
        int fingerprint,
        boolean protection
    ) {
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
