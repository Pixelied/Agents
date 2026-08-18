package dev.adrien.crystaloptimizer.reconcile;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReconciliationGateTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000082");

    @Test
    void worldRevisionChurnAloneDoesNotConfirmServerEffect() {
        CombatSnapshot before = snapshot(100L, 20.0f, List.of(new KnownCrystal(801, new Vec3(1.0, 64.0, 1.0))), Map.of());
        CombatSnapshot later = snapshot(101L, 20.0f, List.of(new KnownCrystal(801, new Vec3(1.0, 64.0, 1.0))), Map.of());
        ReconciliationGate gate = ReconciliationGate.start(before, TARGET, 1_000L, 500L);

        assertEquals(ReconciliationGate.Status.WAITING, gate.evaluate(later, TARGET, 1_200L));
    }

    @Test
    void targetHealthChangeConfirmsMaterialServerEvidence() {
        CombatSnapshot before = snapshot(100L, 20.0f, List.of(), Map.of());
        CombatSnapshot later = snapshot(101L, 8.0f, List.of(), Map.of());
        ReconciliationGate gate = ReconciliationGate.start(before, TARGET, 1_000L, 500L);

        assertEquals(ReconciliationGate.Status.CONFIRMED, gate.evaluate(later, TARGET, 1_100L));
    }

    @Test
    void crystalRemovalConfirmsMaterialServerEvidence() {
        CombatSnapshot before = snapshot(100L, 20.0f, List.of(new KnownCrystal(801, new Vec3(1.0, 64.0, 1.0))), Map.of());
        CombatSnapshot later = snapshot(101L, 20.0f, List.of(), Map.of());
        ReconciliationGate gate = ReconciliationGate.start(before, TARGET, 1_000L, 500L);

        assertEquals(ReconciliationGate.Status.CONFIRMED, gate.evaluate(later, TARGET, 1_100L));
    }

    @Test
    void anchorStateChangeConfirmsMaterialServerEvidence() {
        BlockPos anchor = new BlockPos(2, 64, 2);
        CombatSnapshot before = snapshot(100L, 20.0f, List.of(), Map.of(anchor, new AnchorState(1)));
        CombatSnapshot later = snapshot(101L, 20.0f, List.of(), Map.of());
        ReconciliationGate gate = ReconciliationGate.start(before, TARGET, 1_000L, 500L);

        assertEquals(ReconciliationGate.Status.CONFIRMED, gate.evaluate(later, TARGET, 1_100L));
    }

    @Test
    void unchangedCombatEvidenceEventuallyTimesOut() {
        CombatSnapshot before = snapshot(100L, 20.0f, List.of(), Map.of());
        ReconciliationGate gate = ReconciliationGate.start(before, TARGET, 1_000L, 500L);

        assertEquals(ReconciliationGate.Status.TIMED_OUT, gate.evaluate(before, TARGET, 1_500L));
    }

    private static CombatSnapshot snapshot(
        long revision,
        float targetHealth,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors
    ) {
        return new CombatSnapshot(
            revision,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, SimCombatant.testPlayer(targetHealth)),
            crystals,
            anchors,
            InventoryState.empty(),
            TimingState.unknown()
        );
    }
}
