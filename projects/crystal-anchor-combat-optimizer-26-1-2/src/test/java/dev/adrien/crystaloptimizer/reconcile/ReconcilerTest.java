package dev.adrien.crystaloptimizer.reconcile;

import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReconcilerTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000041");

    @Test
    void unrelatedWorldChangeDoesNotInvalidatePlan() {
        PlanAssumption requiredBlock = PlanAssumption.blockState(
            new BlockPos(2, 64, 2),
            "minecraft:obsidian"
        );
        Reconciler reconciler = new Reconciler(List.of(requiredBlock));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.blockState(
                new BlockPos(40, 70, 40),
                "minecraft:air",
                101L
            )
        );

        assertTrue(result.valid());
        assertTrue(result.failures().isEmpty());
    }

    @Test
    void missingRequiredCrystalInvalidatesAsStateRace() {
        Reconciler reconciler = new Reconciler(List.of(PlanAssumption.crystalExists(829)));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.crystalPresence(829, false, 102L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.STATE_RACE, result.failures().getFirst().kind());
    }

    @Test
    void inventoryDesyncIsResourceFailure() {
        Reconciler reconciler = new Reconciler(List.of(
            PlanAssumption.inventorySlot(4, "minecraft:end_crystal", 8)
        ));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.inventorySlot(4, "minecraft:end_crystal", 2, 103L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.RESOURCE_FAILURE, result.failures().getFirst().kind());
    }

    @Test
    void targetLeavingRobustGeometryIsTargetDivergence() {
        Reconciler reconciler = new Reconciler(List.of(
            PlanAssumption.targetWithin(TARGET, new Vec3(0.5, 64.0, 0.5), 2.0)
        ));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.targetPosition(TARGET, new Vec3(5.0, 64.0, 0.5), 104L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.TARGET_DIVERGENCE, result.failures().getFirst().kind());
    }

    @Test
    void degradedTimingConfidenceIsNetworkUncertainty() {
        Reconciler reconciler = new Reconciler(List.of(
            PlanAssumption.minimumTimingConfidence(0.70)
        ));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.timingConfidence(0.35, 105L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.NETWORK_UNCERTAINTY, result.failures().getFirst().kind());
    }

    @Test
    void dimensionChangeInvalidatesAllWorldAssumptions() {
        Reconciler reconciler = new Reconciler(List.of(
            PlanAssumption.dimension("minecraft:overworld"),
            PlanAssumption.crystalExists(829)
        ));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.dimension("minecraft:the_nether", 106L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.LEGALITY_FAILURE, result.failures().getFirst().kind());
        assertTrue(result.clearAllPredictions());
    }

    @Test
    void simulatorDisagreementIsNeverSilentlyCompensated() {
        Reconciler reconciler = new Reconciler(List.of(
            PlanAssumption.simulatedScalar("target-health-after-hit", 4.0, 0.25)
        ));

        ReconciliationResult result = reconciler.accept(
            ReconciliationEvent.simulatedScalar("target-health-after-hit", 7.0, 107L)
        );

        assertFalse(result.valid());
        assertEquals(FailureKind.SIMULATION_MISMATCH, result.failures().getFirst().kind());
    }

    @Test
    void pendingActionLedgerTracksSequenceAndOnlyRelevantConfirmation() {
        PendingActionLedger ledger = new PendingActionLedger();
        ledger.add(new PendingAction(
            "place-anchor-1",
            22,
            1_000L,
            List.of(PlanAssumption.blockState(new BlockPos(3, 64, 3), "minecraft:respawn_anchor"))
        ));

        ledger.observe(ReconciliationEvent.blockState(new BlockPos(8, 64, 8), "minecraft:stone", 1_050L));
        assertEquals(PendingAction.Status.WAITING, ledger.require("place-anchor-1").status());

        ledger.observe(ReconciliationEvent.blockState(new BlockPos(3, 64, 3), "minecraft:respawn_anchor", 1_060L));
        assertEquals(PendingAction.Status.CONFIRMED, ledger.require("place-anchor-1").status());
        assertEquals(22, ledger.require("place-anchor-1").interactionSequence());
    }
}
