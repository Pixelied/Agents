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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAuthorityTrackerOffhandAuthorityTest {
    @Test
    void optimisticOffhandRemovalImmediatelyMakesProtectionNonGuaranteed() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(slot(40, "minecraft:totem_of_undying", true));
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            TimingSnapshot timing = timing();
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
            assertEquals(timing.containerPredictionSettleTick(), projection.pending().getFirst().authorityWindow().latest());
            assertFalse(projection.guaranteedDeathProtectionAt(503).anyHandAvailable(),
                "a locally removed Totem must stop being guaranteed before the server click settles");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void silentOptimisticOffhandRemovalSettlesAfterCorrectionReturnDeadline() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(slot(40, "minecraft:totem_of_undying", true));
            InventorySnapshot optimisticRemoval = inventory(slot(40, "minecraft:air", false));
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            TimingSnapshot timing = timing();

            tracker.observeUntrackedLocalSelection(optimisticRemoval, timing);

            long beforeSettle = timing.containerPredictionSettleTick() - 1;
            EquipmentAuthorityProjection uncertain = tracker.equipmentProjection(
                optimisticRemoval,
                MitigationSnapshot.none(),
                beforeSettle
            );
            assertEquals(1, uncertain.pending().size());
            assertFalse(uncertain.guaranteedDeathProtectionAt(beforeSettle).anyHandAvailable());

            EquipmentAuthorityProjection settled = tracker.equipmentProjection(
                optimisticRemoval,
                MitigationSnapshot.none(),
                timing.containerPredictionSettleTick()
            );
            assertTrue(settled.pending().isEmpty());
            assertEquals("minecraft:air", settled.confirmedOffHand().stackKey());
            assertFalse(settled.guaranteedDeathProtectionAt(timing.containerPredictionSettleTick()).anyHandAvailable());
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    @Test
    void oldOffhandServerEvidenceCannotEraseLaterOptimisticRemoval() {
        MinecraftServerStateEvidence.reset();
        try {
            InventorySnapshot initial = inventory(slot(40, "minecraft:totem_of_undying", true));
            InventorySnapshot optimisticRemoval = inventory(slot(40, "minecraft:air", false));
            ServerAuthorityTracker tracker = new ServerAuthorityTracker(initial, MitigationSnapshot.none());
            tracker.observeServerEvidence(
                new ServerStateEvidenceSnapshot(true, 1000L, Map.of(), Map.of(), Map.of()),
                initial
            );
            TimingSnapshot timing = timing();

            tracker.observeUntrackedLocalSelection(optimisticRemoval, timing);
            assertEquals(1, tracker.equipmentProjection(
                optimisticRemoval,
                MitigationSnapshot.none(),
                503
            ).pending().size());

            // With 200 ms RTT the correction-return deadline is tick 506. A matching Totem slot
            // packet observed at tick 503 can still have been sent before the server processed the
            // later removal click, so it cannot safely erase the adverse in-flight mutation.
            InventorySlotSnapshot previouslyAuthoritativeTotem = initial.slot(40).orElseThrow();
            tracker.observeServerEvidence(evidenceForOffhand(1001L, previouslyAuthoritativeTotem), initial);

            EquipmentAuthorityProjection stillUncertain = tracker.equipmentProjection(
                initial,
                MitigationSnapshot.none(),
                503
            );
            assertEquals(1, stillUncertain.pending().size(),
                "pre-settle evidence matching the old protected state must not erase a later removal");
            assertEquals("minecraft:totem_of_undying", stillUncertain.confirmedOffHand().stackKey());
            assertFalse(stillUncertain.guaranteedDeathProtectionAt(503).anyHandAvailable(),
                "the later local removal remains an adverse feasible state until correction-return settle");
        } finally {
            MinecraftServerStateEvidence.reset();
        }
    }

    private static TimingSnapshot timing() {
        return new TimingSnapshot(500, 200, 0, new TickWindow(502, 503));
    }

    private static InventorySnapshot inventory(InventorySlotSnapshot offhand) {
        return new InventorySnapshot(1, Map.of(
            1, slot(1, "minecraft:diamond_sword", false),
            40, offhand
        ), false);
    }

    private static ServerStateEvidenceSnapshot evidenceForOffhand(long revision, InventorySlotSnapshot slot) {
        return new ServerStateEvidenceSnapshot(
            true,
            revision,
            Map.of(40, new ServerStateEvidenceSnapshot.StackEvidence(
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
        int count = "minecraft:air".equals(key) ? 0 : 1;
        return new InventorySlotSnapshot(index, key, count, protection);
    }
}
