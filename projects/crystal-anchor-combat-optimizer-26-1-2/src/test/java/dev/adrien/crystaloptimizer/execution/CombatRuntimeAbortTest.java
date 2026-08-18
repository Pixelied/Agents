package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
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
import org.junit.jupiter.api.Test;
import net.minecraft.world.phys.Vec3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatRuntimeAbortTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000811");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000812");

    @Test
    void explicitAbortClearsPinnedCommittedContext() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        PlanExecutionController controller = new PlanExecutionController(
            scheduler,
            new CommitPolicy(0.90, 0.80, 0.85)
        );
        CombatRuntimeEngine engine = new CombatRuntimeEngine(controller, ignored -> lethal());
        RuntimeFrame frame = frame();

        engine.tick(frame, ignored -> ExecutionFeedback.deferred(), 1_000_000_000L, 8);
        assertEquals(CommitPhase.COMMITTED, engine.phase());
        assertEquals(TARGET, engine.pinnedTargetId().orElseThrow());

        engine.abort(CommitAbortReason.TARGET_OUTSIDE_VIABLE_GEOMETRY);

        assertEquals(CommitPhase.NORMAL, engine.phase());
        assertTrue(engine.pinnedTargetId().isEmpty());
        assertEquals(
            CommitAbortReason.TARGET_OUTSIDE_VIABLE_GEOMETRY,
            engine.lastAbortReason().orElseThrow()
        );
    }

    private static RuntimeFrame frame() {
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, SimCombatant.testPlayer(20.0f)),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown()
        );
        return new RuntimeFrame(
            snapshot,
            TARGET,
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

    private static CombatPlan lethal() {
        List<CombatAction> actions = List.of(new SelectHotbarSlot(1));
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.97, 0.94, 1, 1.0, 0.95, 0, 0.10, 0.5, 1.0),
            PacketDependencyGraph.fromActions(actions),
            true,
            0.95
        );
    }
}
