package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Rotate;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlanScore;
import dev.adrien.crystaloptimizer.prediction.PositionHypothesis;
import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatRuntimeEngineTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final UUID TARGET_A = UUID.fromString("00000000-0000-0000-0000-000000000802");
    private static final UUID TARGET_B = UUID.fromString("00000000-0000-0000-0000-000000000803");

    @Test
    void pressurePlanExecutesOnceAndReturnsToReplanning() {
        AtomicInteger plans = new AtomicInteger();
        CombatRuntimeEngine engine = engine(frame -> {
            plans.incrementAndGet();
            return pressure(List.of(new SelectHotbarSlot(1), new SelectHotbarSlot(2)));
        });

        int attempts = engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 20.0f, 0L)),
            ignored -> ExecutionFeedback.sent(),
            1_000_000_000L,
            8
        );

        assertEquals(1, attempts);
        assertEquals(1, plans.get());
        assertEquals(CommitPhase.NORMAL, engine.phase());
        assertTrue(engine.pinnedTargetId().isEmpty());
    }

    @Test
    void committedBurstPinsOriginalTargetAndDrainsSentActions() {
        CombatRuntimeEngine engine = engine(frame -> lethal(List.of(
            new Rotate(15.0f, 5.0f),
            new SelectHotbarSlot(2)
        )));

        int attempts = engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 20.0f, 0L)),
            ignored -> ExecutionFeedback.sent(),
            2_000_000_000L,
            8
        );

        assertEquals(2, attempts);
        assertEquals(CommitPhase.RECONCILING, engine.phase());
        assertEquals(TARGET_A, engine.pinnedTargetId().orElseThrow());
    }

    @Test
    void reconciliationHoldoffRejectsImmediateClientPredictedEvidence() {
        long started = 3_000_000_000L;
        CombatRuntimeEngine engine = engine(frame -> lethal(List.of(new SelectHotbarSlot(1))));
        engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 20.0f, 0L)),
            ignored -> ExecutionFeedback.sent(),
            started,
            8
        );
        assertEquals(CommitPhase.RECONCILING, engine.phase());

        engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 10.0f, 1L)),
            ignored -> ExecutionFeedback.sent(),
            started + 10_000_000L,
            8
        );

        assertEquals(CommitPhase.RECONCILING, engine.phase());

        engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 10.0f, 2L)),
            ignored -> ExecutionFeedback.sent(),
            started + 50_000_000L,
            8
        );

        assertEquals(CommitPhase.NORMAL, engine.phase());
        assertEquals(dev.adrien.crystaloptimizer.reconcile.ReconciliationGate.Status.CONFIRMED,
            engine.lastReconciliationStatus().orElseThrow());
        assertTrue(engine.pinnedTargetId().isEmpty());
    }

    @Test
    void reconciliationTimeoutReturnsToRealityWithoutReplayingOldPlan() {
        AtomicInteger plans = new AtomicInteger();
        long started = 4_000_000_000L;
        CombatRuntimeEngine engine = engine(frame -> {
            plans.incrementAndGet();
            return lethal(List.of(new SelectHotbarSlot(1)));
        });
        RuntimeFrame unchanged = frame(TARGET_A, snapshot(TARGET_A, 20.0f, 0L));
        engine.tick(unchanged, ignored -> ExecutionFeedback.sent(), started, 8);
        assertEquals(1, plans.get());

        int attempts = engine.tick(
            unchanged,
            ignored -> ExecutionFeedback.sent(),
            started + 250_000_000L,
            8
        );

        assertEquals(0, attempts);
        assertEquals(1, plans.get(), "timeout tick must reconcile only; planning resumes next tick");
        assertEquals(CommitPhase.NORMAL, engine.phase());
        assertEquals(dev.adrien.crystaloptimizer.reconcile.ReconciliationGate.Status.TIMED_OUT,
            engine.lastReconciliationStatus().orElseThrow());
    }

    @Test
    void reconciliationUsesPinnedTargetEvenWhenAnotherCandidateBecomesPreferred() {
        long started = 5_000_000_000L;
        CombatRuntimeEngine engine = engine(frame -> lethal(List.of(new SelectHotbarSlot(1))));
        engine.tick(
            frame(TARGET_A, snapshot(TARGET_A, 20.0f, 0L)),
            ignored -> ExecutionFeedback.sent(),
            started,
            8
        );

        assertEquals(TARGET_A, engine.pinnedTargetId().orElseThrow());
        assertTrue(engine.acceptsTarget(TARGET_A));
        assertTrue(!engine.acceptsTarget(TARGET_B));
    }

    private static CombatRuntimeEngine engine(RuntimePlanner planner) {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        PlanExecutionController controller = new PlanExecutionController(
            scheduler,
            new CommitPolicy(0.90, 0.80, 0.85)
        );
        return new CombatRuntimeEngine(controller, planner);
    }

    private static RuntimeFrame frame(UUID targetId, CombatSnapshot snapshot) {
        return new RuntimeFrame(
            snapshot,
            targetId,
            new PredictionSet(
                List.of(new PositionHypothesis(
                    PositionHypothesis.Kind.LIKELY,
                    Vec3.ZERO,
                    Vec3.ZERO,
                    1.0
                )),
                1.0
            )
        );
    }

    private static CombatSnapshot snapshot(UUID targetId, float targetHealth, long revision) {
        return new CombatSnapshot(
            revision,
            SELF,
            CombatRegion.empty(),
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                targetId, SimCombatant.testPlayer(targetHealth)
            ),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            new TimingState(-1L, 0.0, 0.0, 0.0)
        );
    }

    private static CombatPlan lethal(List<CombatAction> actions) {
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.97, 0.94, actions.size(), 1.0, 0.95, 0, 0.10, 0.5, actions.size()),
            PacketDependencyGraph.fromActions(actions),
            true,
            0.95
        );
    }

    private static CombatPlan pressure(List<CombatAction> actions) {
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.0, 0.0, Integer.MAX_VALUE, 0.35, 0.95, 0, 0.05, 0.4, actions.size()),
            PacketDependencyGraph.fromActions(actions),
            false,
            0.95
        );
    }
}
