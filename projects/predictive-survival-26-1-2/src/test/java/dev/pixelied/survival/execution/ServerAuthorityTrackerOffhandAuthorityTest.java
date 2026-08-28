package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerAuthorityTrackerOffhandAuthorityTest {
    @Test
    void optimisticOffhandRemovalImmediatelyMakesProtectionNonGuaranteed() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(slot(40, "minecraft:totem_of_undying", true));
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            TimingSnapshot timing = new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));
            InventorySnapshot optimisticRemoval = inventory(slot(40, "minecraft:air", false));

            tracker.observeUntrackedLocalSelection(optimisticRemoval, timing);
            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                optimisticRemoval,
                MitigationSnapshot.none(),
                503
            );

            assertEquals(1, projection.pending().size(),
                "an optimistic offhand content change must enter the equipment authority queue");
            assertEquals(SurvivalAction.Hand.OFF_HAND, projection.pending().getFirst().hand());
            assertFalse(projection.guaranteedDeathProtectionAt(503).anyHandAvailable(),
                "a locally removed Totem must stop being guaranteed before the server click settles");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    private static InventorySnapshot inventory(InventorySlotSnapshot offhand) {
        return new InventorySnapshot(1, Map.of(
            1, slot(1, "minecraft:diamond_sword", false),
            40, offhand
        ), false);
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        int count = "minecraft:air".equals(key) ? 0 : 1;
        return new InventorySlotSnapshot(index, key, count, protection);
    }
}
