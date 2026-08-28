package dev.pixelied.survival.execution;

import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerAuthorityTrackerMitigationAuthorityTest {
    @Test
    void optimisticMitigationDoesNotBecomeConfirmedWithoutServerAuthority() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot inventory = inventory();
            MitigationSnapshot confirmed = MitigationSnapshot.none();
            MitigationSnapshot optimisticArmor = new MitigationSnapshot(20f, 8f, false, 0, List.of());
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(inventory, confirmed);

            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                inventory,
                optimisticArmor,
                500
            );

            assertEquals(confirmed, projection.confirmedMitigation(),
                "local armor attributes must not become server-confirmed without authority evidence");
            assertEquals(List.of(confirmed), projection.feasibleMitigationAt(500),
                "a client-only optimistic armor state must not erase the last server-authoritative mitigation");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    private static InventorySnapshot inventory() {
        return new InventorySnapshot(1, Map.of(
            1, new InventorySlotSnapshot(1, "minecraft:diamond_sword", 1, false),
            40, new InventorySlotSnapshot(40, "minecraft:air", 0, false)
        ), false);
    }
}
