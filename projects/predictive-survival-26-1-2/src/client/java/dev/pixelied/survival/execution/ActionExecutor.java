package dev.pixelied.survival.execution;

import dev.pixelied.survival.planner.SurvivalAction;

public interface ActionExecutor<A extends SurvivalAction> {
    ExecutionStatus begin(A action, ExecutionContext context);
    ExecutionStatus observe(ExecutionContext context);
}
