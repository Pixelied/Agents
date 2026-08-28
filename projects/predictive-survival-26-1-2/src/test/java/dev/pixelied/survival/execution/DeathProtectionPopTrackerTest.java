package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionPopTrackerTest {
    @Test
    void eachObservedLocalPopAdvancesGenerationImmediately() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();

        tracker.observeLocalTotemPop(100L, 7L);
        assertEquals(1L, tracker.generation());
        assertTrue(tracker.consumptionUnresolved());

        tracker.observeLocalTotemPop(100L, 7L);
        assertEquals(2L, tracker.generation(), "two real pop events in one client tick are two generations");
        assertTrue(tracker.consumptionUnresolved());
    }

    @Test
    void unresolvedDualHandPopConsumesMainBeforeOffLikeVanilla() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();
        InventorySlotSnapshot main = totem(0);
        InventorySlotSnapshot off = totem(40);
        EquipmentAuthorityProjection equipment = projection(main, off, List.of(), 0L);
        InventorySnapshot inventory = inventory(0, main, off);
        tracker.reconcile(equipment, inventory, evidence(1L, Map.of()), 99L);

        tracker.observeLocalTotemPop(100L, 1L);

        DeathProtectionSnapshot projected = tracker.projectedDeathProtectionAt(equipment, 100L);
        assertFalse(projected.mainHandAvailable(), "vanilla scans MAIN_HAND first");
        assertTrue(projected.offHandAvailable(), "authoritative offhand protection survives a main-hand pop");
        assertTrue(tracker.consumptionUnresolved(), "hand/resource identity still waits for inventory evidence");
    }

    @Test
    void ambiguousAuthorityNeverGuessesThatOffhandProtectionSurvived() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();
        InventorySlotSnapshot mainTotem = totem(0);
        InventorySlotSnapshot offTotem = totem(40);
        InventorySlotSnapshot sword = slot(1, "minecraft:diamond_sword", 11, 1, false);
        PendingEquipmentMutation restore = new PendingEquipmentMutation(
            SurvivalAction.Hand.MAIN_HAND,
            mainTotem,
            sword,
            new TickWindow(102L, 104L),
            PendingEquipmentMutation.Origin.RESTORE,
            1L
        );
        EquipmentAuthorityProjection equipment = projection(mainTotem, offTotem, List.of(restore), 1L);
        InventorySnapshot inventory = inventory(0, mainTotem, offTotem);
        tracker.reconcile(equipment, inventory, evidence(1L, Map.of()), 102L);

        tracker.observeLocalTotemPop(103L, 1L);

        DeathProtectionSnapshot projected = tracker.projectedDeathProtectionAt(equipment, 103L);
        assertFalse(projected.anyHandAvailable(),
            "inside the restore authority window either MAIN can pop leaving OFF, or OFF can pop after MAIN restored away");
    }

    @Test
    void eventBeforeInventoryCorrectionStaysUnresolvedUntilAuthoritativeRemovalEvidenceArrives() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();
        InventorySlotSnapshot mainTotem = totem(0);
        InventorySlotSnapshot offAir = air(40);
        EquipmentAuthorityProjection before = projection(mainTotem, offAir, List.of(), 0L);
        tracker.reconcile(before, inventory(0, mainTotem, offAir), evidence(10L, Map.of(
            0, stackEvidence(mainTotem, 10L)
        )), 100L);

        tracker.observeLocalTotemPop(101L, 10L);
        assertTrue(tracker.consumptionUnresolved());

        InventorySlotSnapshot mainAir = air(0);
        EquipmentAuthorityProjection after = projection(mainAir, offAir, List.of(), 0L);
        tracker.reconcile(after, inventory(0, mainAir, offAir), evidence(11L, Map.of(
            0, stackEvidence(mainAir, 11L)
        )), 102L);

        assertFalse(tracker.consumptionUnresolved());
        assertEquals(1L, tracker.generation());
    }

    @Test
    void inventoryCorrectionBeforeEntityEventAlsoResolvesTheSamePop() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();
        InventorySlotSnapshot mainTotem = totem(0);
        InventorySlotSnapshot offAir = air(40);
        EquipmentAuthorityProjection before = projection(mainTotem, offAir, List.of(), 0L);
        tracker.reconcile(before, inventory(0, mainTotem, offAir), evidence(20L, Map.of(
            0, stackEvidence(mainTotem, 20L)
        )), 200L);

        // The authoritative slot correction can be applied by vanilla before event 35 reaches the
        // packet handler. No runtime capture is required between those two clientbound packets.
        tracker.observeLocalTotemPop(201L, 21L);
        InventorySlotSnapshot mainAir = air(0);
        EquipmentAuthorityProjection after = projection(mainAir, offAir, List.of(), 0L);
        tracker.reconcile(after, inventory(0, mainAir, offAir), evidence(21L, Map.of(
            0, stackEvidence(mainAir, 21L)
        )), 201L);

        assertFalse(tracker.consumptionUnresolved(),
            "evidence newer than the last reconciled pre-pop revision but already present at event time must count");
    }

    @Test
    void unresolvedPopRemovesStaleHeldTotemFromRoutingButPreservesReplacementInventory() {
        DeathProtectionPopTracker tracker = new DeathProtectionPopTracker();
        InventorySlotSnapshot mainTotem = totem(0);
        InventorySlotSnapshot replacement = totem(2);
        InventorySlotSnapshot offAir = air(40);
        InventorySnapshot observed = new InventorySnapshot(0, Map.of(
            0, mainTotem,
            2, replacement,
            40, offAir
        ), false);
        EquipmentAuthorityProjection equipment = projection(mainTotem, offAir, List.of(), 0L);
        tracker.reconcile(equipment, observed, evidence(30L, Map.of()), 300L);
        tracker.observeLocalTotemPop(301L, 30L);

        InventorySnapshot projected = tracker.conservativeInventoryAfterPop(observed, equipment, 301L);
        assertFalse(projected.slot(0).orElseThrow().deathProtection(),
            "the just-consumed held Totem must not suppress replenishment as AlreadyInHand");
        assertTrue(projected.slot(2).orElseThrow().deathProtection(),
            "a separate physical replacement Totem must remain routable");
    }

    private static EquipmentAuthorityProjection projection(
        InventorySlotSnapshot main,
        InventorySlotSnapshot off,
        List<PendingEquipmentMutation> pending,
        long epoch
    ) {
        return new EquipmentAuthorityProjection(
            main.inventoryIndex(), main, off, pending, epoch, MitigationSnapshot.none()
        );
    }

    private static InventorySnapshot inventory(int selected, InventorySlotSnapshot main, InventorySlotSnapshot off) {
        return new InventorySnapshot(selected, Map.of(
            main.inventoryIndex(), main,
            40, off
        ), false);
    }

    private static ServerStateEvidenceSnapshot evidence(
        long revision,
        Map<Integer, ServerStateEvidenceSnapshot.StackEvidence> inventory
    ) {
        return new ServerStateEvidenceSnapshot(true, revision, inventory, Map.of(), Map.of());
    }

    private static ServerStateEvidenceSnapshot.StackEvidence stackEvidence(InventorySlotSnapshot slot, long revision) {
        return new ServerStateEvidenceSnapshot.StackEvidence(
            slot.stackKey(), slot.componentFingerprint(), slot.count(), revision
        );
    }

    private static InventorySlotSnapshot totem(int index) {
        return new InventorySlotSnapshot(
            index,
            "minecraft:totem_of_undying",
            77 + index,
            1,
            true,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            false
        );
    }

    private static InventorySlotSnapshot air(int index) {
        return slot(index, "minecraft:air", 0, 0, false);
    }

    private static InventorySlotSnapshot slot(int index, String key, int fingerprint, int count, boolean protection) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, count, protection,
            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), false
        );
    }
}
