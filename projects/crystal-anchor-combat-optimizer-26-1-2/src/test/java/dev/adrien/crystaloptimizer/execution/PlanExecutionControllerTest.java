package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlanScore;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutionControllerTest {
    @Test
    void lethalZeroFeedbackPlanFreezesUntilAllActionsAreSent() {
        PlanExecutionController controller = controller();
        CombatPlan lethal = lethal(List.of(new Wait(1), new Wait(2)));

        controller.offer(lethal);

        assertEquals(CommitPhase.COMMITTED, controller.phase());
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());

        controller.offer(pressure(List.of(new Wait(9))));
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());

        controller.report(ExecutionFeedback.sent());
        assertEquals(new Wait(2), controller.nextAction().orElseThrow());

        controller.report(ExecutionFeedback.sent());
        assertEquals(CommitPhase.RECONCILING, controller.phase());
        assertTrue(controller.nextAction().isEmpty());

        controller.reconciliationComplete();
        assertEquals(CommitPhase.NORMAL, controller.phase());
    }

    @Test
    void pressurePlanDispatchesOnlyFirstActionThenReturnsToReplanning() {
        PlanExecutionController controller = controller();
        CombatPlan pressure = pressure(List.of(new Wait(1), new Wait(2)));

        controller.offer(pressure);

        assertEquals(CommitPhase.NORMAL, controller.phase());
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());

        controller.report(ExecutionFeedback.sent());

        assertTrue(controller.nextAction().isEmpty());
        assertEquals(CommitPhase.NORMAL, controller.phase());
    }

    @Test
    void deferredCommittedDispatchDoesNotAdvanceTheFrozenBurst() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2))));

        controller.report(ExecutionFeedback.deferred());

        assertEquals(0, controller.sentActionCount());
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());
    }

    @Test
    void committedWaitConsumesLocalTicksBeforeAdvancing() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(2), new Wait(1))));

        controller.report(ExecutionFeedback.waiting(2));
        assertTrue(controller.nextAction().isEmpty());

        controller.tick();
        assertTrue(controller.nextAction().isEmpty());
        assertEquals(0, controller.sentActionCount());

        controller.tick();
        assertEquals(1, controller.sentActionCount());
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());
    }

    @Test
    void hardDispatchFailureAbortsCommittedBurst() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2))));

        controller.report(ExecutionFeedback.failed());

        assertEquals(CommitPhase.NORMAL, controller.phase());
        assertEquals(CommitAbortReason.ACTION_DISPATCH_FAILED, controller.lastAbortReason().orElseThrow());
        assertTrue(controller.nextAction().isEmpty());
    }

    @Test
    void replanningCanReplaceADeferredPressureAction() {
        PlanExecutionController controller = controller();
        controller.offer(pressure(List.of(new Wait(1))));
        controller.report(ExecutionFeedback.deferred());

        controller.offer(pressure(List.of(new Wait(3))));

        assertEquals(new Wait(3), controller.nextAction().orElseThrow());
    }

    private static PlanExecutionController controller() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        CommitPolicy policy = new CommitPolicy(0.90, 0.80, 0.85);
        return new PlanExecutionController(scheduler, policy);
    }

    private static CombatPlan lethal(List<CombatAction> actions) {
        PacketDependencyGraph graph = PacketDependencyGraph.fromActions(actions);
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.97, 0.94, actions.size(), 1.0, 0.95, 0, 0.10, 0.5, actions.size()),
            graph,
            true,
            0.95
        );
    }

    private static CombatPlan pressure(List<CombatAction> actions) {
        PacketDependencyGraph graph = PacketDependencyGraph.fromActions(actions);
        return new CombatPlan(
            actions,
            new PlanScore(false, 0.0, 0.0, Integer.MAX_VALUE, 0.35, 0.95, 0, 0.05, 0.4, actions.size()),
            graph,
            false,
            0.95
        );
    }
}
