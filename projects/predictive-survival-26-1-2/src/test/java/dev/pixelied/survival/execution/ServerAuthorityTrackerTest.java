package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAuthorityTrackerTest {
    @Test
    void heldSlotCannotConfirmBeforeLatestPacketProcessingTick() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(1);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentHotbarSelection(5, timing);

        assertEquals(1, tracker.confirmedSelectedSlot(5, 103));
        assertEquals(5, tracker.confirmedSelectedSlot(5, 104));
    }

    @Test
    void heldSlotDoesNotAdvanceWhenLocalSelectionWasContradicted() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(1);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentHotbarSelection(5, timing);

        assertEquals(1, tracker.confirmedSelectedSlot(2, 104));
    }

    @Test
    void richProjectionCarriesHotbarArmUncertaintyUntilLatestAuthorityTick() {
        InventorySnapshot initial = inventory(1);
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentHotbarSelection(
            5,
            timing,
            initial,
            PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION
        );

        EquipmentAuthorityProjection during = tracker.equipmentProjection(initial, MitigationSnapshot.none(), 103);
        assertEquals(2, during.feasibleDeathProtectionAt(103).size());
        assertTrue(during.feasibleDeathProtectionAt(103).stream().anyMatch(state -> !state.anyHandAvailable()));
        assertTrue(during.feasibleDeathProtectionAt(103).stream().anyMatch(state -> state.mainHandAvailable()));

        InventorySnapshot selectedTotem = inventory(5);
        assertEquals(5, tracker.confirmedSelectedSlot(5, 104));
        EquipmentAuthorityProjection after = tracker.equipmentProjection(selectedTotem, MitigationSnapshot.none(), 104);
        assertTrue(after.pending().isEmpty());
        assertTrue(after.guaranteedDeathProtectionAt(104).mainHandAvailable());
    }

    @Test
    void richProjectionCarriesRestoreAwayAsAdversePendingTransition() {
        InventorySnapshot initial = inventory(5);
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
        TimingSnapshot timing = new TimingSnapshot(200, 100, 10, new TickWindow(202, 204));

        tracker.sentHotbarSelection(
            1,
            timing,
            initial,
            PendingEquipmentMutation.Origin.RESTORE
        );

        EquipmentAuthorityProjection during = tracker.equipmentProjection(inventory(1), MitigationSnapshot.none(), 203);
        assertTrue(during.feasibleDeathProtectionAt(203).stream().anyMatch(state -> state.mainHandAvailable()));
        assertTrue(during.feasibleDeathProtectionAt(203).stream().anyMatch(state -> !state.anyHandAvailable()));
        assertTrue(during.pending().stream().anyMatch(mutation -> mutation.origin() == PendingEquipmentMutation.Origin.RESTORE));
    }

    @Test
    void manualSelectionDuringEmergencySelectionBecomesNewUserMutation() {
        InventorySnapshot initial = inventory(1);
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
        TimingSnapshot emergencyTiming = new TimingSnapshot(300, 100, 10, new TickWindow(302, 304));
        TimingSnapshot userTiming = new TimingSnapshot(301, 100, 10, new TickWindow(303, 305));

        tracker.sentHotbarSelection(
            5,
            emergencyTiming,
            initial,
            PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION
        );
        tracker.observeUntrackedLocalSelection(inventory(2), userTiming);

        EquipmentAuthorityProjection projection = tracker.equipmentProjection(inventory(2), MitigationSnapshot.none(), 303);
        assertEquals(2, projection.pending().size());
        assertEquals(PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION, projection.pending().get(0).origin());
        assertEquals(PendingEquipmentMutation.Origin.USER, projection.pending().get(1).origin());
        assertEquals(2, projection.pending().get(1).after().inventoryIndex());
    }

    @Test
    void shieldWarmupStartsAtConservativeServerProcessingTick() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentUseItem(SurvivalAction.Hand.OFF_HAND, timing);

        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 103));
        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 104));
        assertEquals(4, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 108));
        assertEquals(5, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 109));
    }

    @Test
    void mismatchedUseHandNeverAccumulatesConfirmedWarmup() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        tracker.sentUseItem(
            SurvivalAction.Hand.OFF_HAND,
            new TimingSnapshot(10, 50, 0, new TickWindow(11, 11))
        );

        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.MAIN_HAND, 20));
    }

    @Test
    void endedUseSessionCannotBeReusedByLaterUseOfSameHand() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        tracker.sentUseItem(
            SurvivalAction.Hand.OFF_HAND,
            new TimingSnapshot(100, 50, 0, new TickWindow(104, 104))
        );

        assertEquals(5, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 109));
        assertEquals(0, tracker.confirmedUseTicks(false, null, 120));
        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 500),
            "a new local use must never inherit warmup from a completed old server-use session");
    }

    @Test
    void defaultShieldAngleAcceptsFrontAndRejectsBehind() {
        Vec3Snapshot player = new Vec3Snapshot(0, 64, 0);

        assertTrue(ServerAuthorityTracker.withinHorizontalBlockAngle(player, 0f, new Vec3Snapshot(0, 64, 5), 90f));
        assertFalse(ServerAuthorityTracker.withinHorizontalBlockAngle(player, 0f, new Vec3Snapshot(0, 64, -5), 90f));
    }

    private static InventorySnapshot inventory(int selected) {
        return new InventorySnapshot(selected, Map.of(
            1, slot(1, "minecraft:diamond_sword", false),
            2, slot(2, "minecraft:diamond_pickaxe", false),
            5, slot(5, "minecraft:totem_of_undying", true),
            40, slot(40, "minecraft:air", false)
        ), false);
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, protection ? 1 : ("minecraft:air".equals(key) ? 0 : 1), protection);
    }
}
