package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import java.util.Objects;
import java.util.function.Function;

public final class PlanExecutionDriver {
    private final PlanExecutionController controller;

    public PlanExecutionDriver(PlanExecutionController controller) {
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    public int drive(Function<CombatAction, ExecutionFeedback> dispatcher, int maxDispatches) {
        Objects.requireNonNull(dispatcher, "dispatcher");
        if (maxDispatches <= 0) {
            throw new IllegalArgumentException("maxDispatches must be positive");
        }

        int attempts = 0;
        while (attempts < maxDispatches) {
            var next = controller.nextAction();
            if (next.isEmpty()) {
                break;
            }

            ExecutionFeedback feedback = Objects.requireNonNull(
                dispatcher.apply(next.orElseThrow()),
                "dispatcher feedback"
            );
            attempts++;
            controller.report(feedback);
            if (feedback.status() != ExecutionFeedback.Status.SENT) {
                break;
            }
        }
        return attempts;
    }
}
