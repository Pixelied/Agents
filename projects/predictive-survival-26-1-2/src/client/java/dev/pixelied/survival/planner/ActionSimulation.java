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
    String reason
) {
    public ActionSimulation {
        action = Objects.requireNonNull(action, "action");
        result = Objects.requireNonNull(result, "result");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
