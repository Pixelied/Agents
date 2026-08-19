package dev.pixelied.survival.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EmergencyInventoryTransactionTest {
    @Test
    void staleStateIdWaitsForAuthoritativeFullReconcile() {
        EmergencyInventoryTransaction tx = sentTransaction().observeStateIdMismatch();
        assertEquals(EmergencyInventoryTransaction.State.AWAITING_RECONCILE, tx.state());

        tx = tx.reconcile(
            slot(17, "minecraft:shield", 1, false),
            slot(40, "minecraft:totem_of_undying", 1, true)
        );
        assertEquals(EmergencyInventoryTransaction.State.CONFIRMED, tx.state());
    }

    @Test
    void contradictoryReconcileIsNotTrusted() {
        EmergencyInventoryTransaction tx = sentTransaction().observeStateIdMismatch().reconcile(
            slot(17, "minecraft:air", 0, false),
            slot(40, "minecraft:shield", 1, false)
        );
        assertEquals(EmergencyInventoryTransaction.State.CONTRADICTED, tx.state());
    }

    @Test
    void restorationWaitsWhileLethalThreatIsPending() {
        EmergencyInventoryTransaction tx = sentTransaction().reconcile(
            slot(17, "minecraft:shield", 1, false),
            slot(40, "minecraft:totem_of_undying", 1, true)
        );
        assertEquals(EmergencyInventoryTransaction.State.CONFIRMED, tx.attemptRestore(true).state());
        assertEquals(EmergencyInventoryTransaction.State.RESTORING, tx.attemptRestore(false).state());
    }

    @Test
    void consumedProtectionInvalidatesSavedRestoreStack() {
        EmergencyInventoryTransaction tx = sentTransaction().reconcile(
            slot(17, "minecraft:shield", 1, false),
            slot(40, "minecraft:totem_of_undying", 1, true)
        ).markConsumed();

        assertEquals(EmergencyInventoryTransaction.State.CONSUMED, tx.state());
        assertFalse(tx.canRestoreOriginalDestinationStack());
    }

    private static EmergencyInventoryTransaction sentTransaction() {
        DeathProtectionRoute.ContainerSwap route = new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        );
        return EmergencyInventoryTransaction.planned(
            route,
            0,
            7,
            slot(17, "minecraft:totem_of_undying", 1, true),
            slot(40, "minecraft:shield", 1, false),
            100,
            103
        ).markSent();
    }

    private static InventorySlotSnapshot slot(int index, String key, int count, boolean protection) {
        return new InventorySlotSnapshot(index, key, count, protection);
    }
}
