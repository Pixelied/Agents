package dev.pixelied.survival.execution;

import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerEquipmentEvidenceAuthorityTest {
    @Test
    void newerMatchingServerEvidenceCanConfirmAnOffhandChange() {
        InventorySnapshot initial = inventory(air(40));
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());

        // First observation establishes the evidence baseline represented by the constructor state.
        tracker.observeServerEvidence(new ServerStateEvidenceSnapshot(
            true,
            10L,
            Map.of(40, evidence(air(40), 10L)),
            Map.of(),
            Map.of()
        ), initial);

        InventorySlotSnapshot totem = slot(40, "minecraft:totem_of_undying", 1, true);
        InventorySnapshot observedTotem = inventory(totem);
        tracker.observeServerEvidence(new ServerStateEvidenceSnapshot(
            true,
            11L,
            Map.of(40, evidence(totem, 11L)),
            Map.of(),
            Map.of()
        ), observedTotem);

        assertTrue(tracker.equipmentProjection(observedTotem, MitigationSnapshot.none(), 20L)
            .guaranteedDeathProtectionAt(20L)
            .offHandAvailable());
    }

    private static InventorySnapshot inventory(InventorySlotSnapshot offhand) {
        return new InventorySnapshot(0, Map.of(
            0, slot(0, "minecraft:stick", 1, false),
            1, slot(1, "minecraft:totem_of_undying", 1, true),
            40, offhand
        ), false);
    }

    private static InventorySlotSnapshot air(int index) {
        return slot(index, "minecraft:air", 0, false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int count, boolean protection) {
        return new InventorySlotSnapshot(index, key, count, protection);
    }

    private static ServerStateEvidenceSnapshot.StackEvidence evidence(InventorySlotSnapshot slot, long revision) {
        return new ServerStateEvidenceSnapshot.StackEvidence(
            slot.stackKey(), slot.componentFingerprint(), slot.count(), revision
        );
    }
}
