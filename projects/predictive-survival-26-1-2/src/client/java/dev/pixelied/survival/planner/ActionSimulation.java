package dev.pixelied.survival.planner;

import dev.pixelied.survival.timeline.TimelineResult;

import java.util.Objects;

public record ActionSimulation(
    SurvivalAction action,
    TimelineResult result,
    boolean feasible,
    double reliability,
    int consumableCost,
    int disruptionCost,
    String reason,
    DeadlineStatus deadlineStatus
) {
    public ActionSimulation {
        action = Objects.requireNonNull(action, "action");
        result = Objects.requireNonNull(result, "result");
        reason = Objects.requireNonNull(reason, "reason");
        deadlineStatus = Objects.requireNonNull(deadlineStatus, "deadlineStatus");
    }

    public ActionSimulation(
        SurvivalAction action,
        TimelineResult result,
        boolean feasible,
        double reliability,
        int consumableCost,
        int disruptionCost,
        String reason
    ) {
        this(action, result, feasible, reliability, consumableCost, disruptionCost, reason,
            action instanceof SurvivalAction.NoAction ? DeadlineStatus.NOT_APPLICABLE : DeadlineStatus.GUARANTEED);
    }
}
