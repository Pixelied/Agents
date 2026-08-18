package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlanScore;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanExecutionDriverTest {
    @Test
    void committedSentActionsDrainInOneDriveUntilReconciliation() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2), new Wait(3))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);
        List<CombatAction> dispatched = new ArrayList<>();

        int attempts = driver.drive(action -> {
            dispatched.add(action);
            return ExecutionFeedback.sent();
        }, 8);

        assertEquals(3, attempts);
        assertEquals(List.of(new Wait(1), new Wait(2), new Wait(3)), dispatched);
        assertEquals(CommitPhase.RECONCILING, controller.phase());
    }

    @Test
    void pressurePlanDispatchesOnlyItsFirstAction() {
        PlanExecutionController controller = controller();
        controller.offer(pressure(List.of(new Wait(1), new Wait(2))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);
        List<CombatAction> dispatched = new ArrayList<>();

        int attempts = driver.drive(action -> {
            dispatched.add(action);
            return ExecutionFeedback.sent();
        }, 8);

        assertEquals(1, attempts);
        assertEquals(List.of(new Wait(1)), dispatched);
        assertEquals(CommitPhase.NORMAL, controller.phase());
        assertTrue(controller.nextAction().isEmpty());
    }

    @Test
    void deferredDispatchStopsCommittedDrainWithoutAdvancing() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);

        int attempts = driver.drive(action -> ExecutionFeedback.deferred(), 8);

        assertEquals(1, attempts);
        assertEquals(0, controller.sentActionCount());
        assertEquals(new Wait(1), controller.nextAction().orElseThrow());
    }

    @Test
    void waitingDispatchStopsCommittedDrain() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(2), new Wait(1))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);

        int attempts = driver.drive(action -> ExecutionFeedback.waiting(2), 8);

        assertEquals(1, attempts);
        assertEquals(0, controller.sentActionCount());
        assertTrue(controller.nextAction().isEmpty());
    }

    @Test
    void failedDispatchStopsAndAbortsCommittedBurst() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);

        int attempts = driver.drive(action -> ExecutionFeedback.failed(), 8);

        assertEquals(1, attempts);
        assertEquals(CommitPhase.NORMAL, controller.phase());
        assertEquals(CommitAbortReason.ACTION_DISPATCH_FAILED, controller.lastAbortReason().orElseThrow());
    }

    @Test
    void dispatchBudgetBoundsCommittedBurstWorkPerClientTick() {
        PlanExecutionController controller = controller();
        controller.offer(lethal(List.of(new Wait(1), new Wait(2), new Wait(3))));
        PlanExecutionDriver driver = new PlanExecutionDriver(controller);

        int attempts = driver.drive(action -> ExecutionFeedback.sent(), 2);

        assertEquals(2, attempts);
        assertEquals(2, controller.sentActionCount());
        assertEquals(new Wait(3), controller.nextAction().orElseThrow());
    }

    private static PlanExecutionController controller() {
        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        return new PlanExecutionController(scheduler, new CommitPolicy(0.90, 0.80, 0.85));
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
