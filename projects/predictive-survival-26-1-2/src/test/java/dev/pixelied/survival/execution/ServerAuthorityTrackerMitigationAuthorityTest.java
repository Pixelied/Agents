package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAuthorityTrackerMitigationAuthorityTest {
    @Test
    void optimisticMitigationIncreaseRemainsUncertainUntilCorrectionReturnDeadline() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot inventory = inventory();
            MitigationSnapshot confirmed = MitigationSnapshot.none();
            MitigationSnapshot optimisticArmor = armored();
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(inventory, confirmed);
            TimingSnapshot timing = timing();

            tracker.observeUntrackedLocalMitigation(optimisticArmor, timing);

            EquipmentAuthorityProjection uncertain = tracker.equipmentProjection(
                inventory,
                optimisticArmor,
                503
            );
            assertEquals(confirmed, uncertain.confirmedMitigation(),
                "local armor attributes must not become server-confirmed before container authority settles");
            assertEquals(List.of(confirmed, optimisticArmor), uncertain.feasibleMitigationAt(503));
            assertEquals(MitigationSnapshot.none(), uncertain.conservativeMitigationAt(503),
                "ambiguous mitigation must fail closed instead of crediting optimistic armor");
            assertEquals(1, uncertain.pending().size());
            assertTrue(uncertain.pending().getFirst().mitigationAfter().isPresent());
            assertEquals(timing.containerPredictionSettleTick(), uncertain.pending().getFirst().authorityWindow().latest());

            EquipmentAuthorityProjection settled = tracker.equipmentProjection(
                inventory,
                optimisticArmor,
                timing.containerPredictionSettleTick()
            );
            assertTrue(settled.pending().isEmpty());
            assertEquals(optimisticArmor, settled.confirmedMitigation(),
                "a silent accepted armor prediction must become authoritative after correction-return settle");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void optimisticMitigationRemovalFailsClosedUntilCorrectionReturnDeadline() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot inventory = inventory();
            MitigationSnapshot confirmedArmor = armored();
            MitigationSnapshot optimisticRemoval = MitigationSnapshot.none();
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(inventory, confirmedArmor);
            TimingSnapshot timing = timing();

            tracker.observeUntrackedLocalMitigation(optimisticRemoval, timing);

            EquipmentAuthorityProjection uncertain = tracker.equipmentProjection(
                inventory,
                optimisticRemoval,
                503
            );
            assertEquals(confirmedArmor, uncertain.confirmedMitigation());
            assertEquals(List.of(confirmedArmor, optimisticRemoval), uncertain.feasibleMitigationAt(503));
            assertEquals(MitigationSnapshot.none(), uncertain.conservativeMitigationAt(503),
                "a possible armor removal must never leave old armor credited as guaranteed");

            EquipmentAuthorityProjection settled = tracker.equipmentProjection(
                inventory,
                optimisticRemoval,
                timing.containerPredictionSettleTick()
            );
            assertTrue(settled.pending().isEmpty());
            assertEquals(optimisticRemoval, settled.confirmedMitigation());
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    private static TimingSnapshot timing() {
        return new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));
    }

    private static MitigationSnapshot armored() {
        return new MitigationSnapshot(20f, 8f, false, 0, List.of());
    }

    private static InventorySnapshot inventory() {
        return new InventorySnapshot(1, Map.of(
            1, new InventorySlotSnapshot(1, "minecraft:diamond_sword", 1, false),
            40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
        ), false);
    }
}
