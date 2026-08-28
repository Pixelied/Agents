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
    void optimisticSelectedSlotContentsDoNotBecomeConfirmedWithoutInboundEvidence() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(1);
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            InventorySnapshot optimisticSwap = optimisticTotem();

            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                optimisticSwap,
                MitigationSnapshot.none(),
                400
            );

            assertEquals("minecraft:diamond_sword", projection.confirmedMainHand().stackKey());
            assertFalse(
                projection.guaranteedDeathProtectionAt(400).anyHandAvailable(),
                "client-predicted container contents must not manufacture server-confirmed protection"
            );
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void inboundSelectedSlotEvidenceIsAppliedBeforeClassifyingContentDelta() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(1);
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            tracker.observeServerEvidence(
                new ServerStateEvidenceSnapshot(true, 1000L, Map.of(), Map.of(), Map.of()),
                initial
            );

            InventorySlotSnapshot emptyMain = slot(1, "minecraft:air", false);
            InventorySnapshot consumed = new InventorySnapshot(1, Map.of(
                1, emptyMain,
                2, slot(2, "minecraft:diamond_pickaxe", false),
                5, slot(5, "minecraft:totem_of_undying", true),
                40, slot(40, "minecraft:air", false)
            ), false);
            tracker.observeServerEvidence(
                evidenceForSlot(1001L, emptyMain),
                consumed
            );
            tracker.observeUntrackedLocalSelection(
                consumed,
                new TimingSnapshot(500, 100, 0, new TickWindow(502, 503))
            );

            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                consumed,
                MitigationSnapshot.none(),
                500
            );
            assertEquals("minecraft:air", projection.confirmedMainHand().stackKey());
            assertTrue(
                projection.pending().isEmpty(),
                "fresh inbound selected-slot evidence must not be reclassified as a new user prediction"
            );
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void optimisticSelectedSlotContentsStayUncertainUntilCorrectionReturnDeadline() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(1);
            InventorySnapshot optimistic = optimisticTotem();
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            TimingSnapshot timing = new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));

            tracker.observeUntrackedLocalSelection(optimistic, timing);

            EquipmentAuthorityProjection beforeSettle = tracker.equipmentProjection(
                optimistic,
                MitigationSnapshot.none(),
                timing.containerPredictionSettleTick() - 1
            );
            assertEquals(1, beforeSettle.pending().size());
            assertEquals(
                timing.containerPredictionSettleTick(),
                beforeSettle.pending().getFirst().authorityWindow().latest(),
                "silent container prediction cannot settle before a correction could have returned"
            );
            assertFalse(beforeSettle.guaranteedDeathProtectionAt(
                timing.containerPredictionSettleTick() - 1
            ).anyHandAvailable());

            EquipmentAuthorityProjection settled = tracker.equipmentProjection(
                optimistic,
                MitigationSnapshot.none(),
                timing.containerPredictionSettleTick()
            );
            assertTrue(settled.pending().isEmpty());
            assertTrue(settled.guaranteedDeathProtectionAt(timing.containerPredictionSettleTick()).mainHandAvailable());
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void serverCorrectionPreventsRejectedOptimisticMainHandFromSettling() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(1);
            InventorySnapshot optimistic = optimisticTotem();
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            tracker.observeServerEvidence(
                new ServerStateEvidenceSnapshot(true, 2000L, Map.of(), Map.of(), Map.of()),
                initial
            );
            TimingSnapshot timing = new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));
            tracker.observeUntrackedLocalSelection(optimistic, timing);

            InventorySlotSnapshot authoritativeSword = initial.slot(1).orElseThrow();
            tracker.observeServerEvidence(
                evidenceForSlot(2001L, authoritativeSword),
                initial
            );

            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                initial,
                MitigationSnapshot.none(),
                timing.containerPredictionSettleTick()
            );
            assertEquals("minecraft:diamond_sword", projection.confirmedMainHand().stackKey());
            assertFalse(projection.guaranteedDeathProtectionAt(
                timing.containerPredictionSettleTick()
            ).anyHandAvailable());
            assertTrue(
                projection.pending().isEmpty(),
                "matching server correction must collapse the rejected optimistic content mutation"
            );
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void correctionForEarlierSameSlotPredictionPreservesLaterInFlightMutation() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(1);
            InventorySnapshot firstPrediction = optimisticTotem();
            InventorySlotSnapshot laterMain = slot(1, "minecraft:stick", false);
            InventorySnapshot laterPrediction = new InventorySnapshot(1, Map.of(
                1, laterMain,
                2, slot(2, "minecraft:diamond_pickaxe", false),
                5, slot(5, "minecraft:diamond_sword", false),
                40, slot(40, "minecraft:air", false)
            ), false);
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            tracker.observeServerEvidence(
                new ServerStateEvidenceSnapshot(true, 3000L, Map.of(), Map.of(), Map.of()),
                initial
            );
            TimingSnapshot firstTiming = new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));
            TimingSnapshot secondTiming = new TimingSnapshot(501, 200, 0, new TickWindow(503, 504));

            tracker.observeUntrackedLocalSelection(firstPrediction, firstTiming);
            tracker.observeUntrackedLocalSelection(laterPrediction, secondTiming);

            assertEquals(2, tracker.equipmentProjection(
                laterPrediction,
                MitigationSnapshot.none(),
                503
            ).pending().size());

            InventorySlotSnapshot authoritativeTotem = firstPrediction.slot(1).orElseThrow();
            tracker.observeServerEvidence(
                evidenceForSlot(3001L, authoritativeTotem),
                firstPrediction
            );

            EquipmentAuthorityProjection projection = tracker.equipmentProjection(
                firstPrediction,
                MitigationSnapshot.none(),
                firstTiming.containerPredictionSettleTick()
            );
            assertEquals("minecraft:totem_of_undying", projection.confirmedMainHand().stackKey());
            assertEquals(1, projection.pending().size(),
                "evidence for the earlier click must not erase a later same-slot packet still in flight");
            assertEquals("minecraft:stick", projection.pending().getFirst().after().stackKey());
            assertFalse(projection.guaranteedDeathProtectionAt(
                firstTiming.containerPredictionSettleTick()
            ).anyHandAvailable(),
                "a later in-flight removal must keep protection non-guaranteed");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
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

    private static InventorySnapshot optimisticTotem() {
        return new InventorySnapshot(1, Map.of(
            1, slot(1, "minecraft:totem_of_undying", true),
            2, slot(2, "minecraft:diamond_pickaxe", false),
            5, slot(5, "minecraft:diamond_sword", false),
            40, slot(40, "minecraft:air", false)
        ), false);
    }

    private static ServerStateEvidenceSnapshot evidenceForSlot(long revision, InventorySlotSnapshot slot) {
        return new ServerStateEvidenceSnapshot(
            true,
            revision,
            Map.of(slot.inventoryIndex(), new ServerStateEvidenceSnapshot.StackEvidence(
                slot.stackKey(),
                slot.componentFingerprint(),
                slot.count(),
                revision
            )),
            Map.of(),
            Map.of()
        );
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, protection ? 1 : ("minecraft:air".equals(key) ? 0 : 1), protection);
    }
}
