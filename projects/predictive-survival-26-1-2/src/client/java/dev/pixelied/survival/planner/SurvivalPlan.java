package dev.pixelied.survival.planner;

import java.util.List;
import java.util.Objects;

public record SurvivalPlan(
    SurvivalAction action,
    ActionSimulation simulation,
    int evaluatedCandidateCount,
    List<ActionSimulation> evaluated
) {
    public SurvivalPlan {
        action = Objects.requireNonNull(action, "action");
        simulation = Objects.requireNonNull(simulation, "simulation");
        evaluated = List.copyOf(Objects.requireNonNull(evaluated, "evaluated"));
        if (evaluatedCandidateCount < 0 || evaluatedCandidateCount > evaluated.size()) {
            throw new IllegalArgumentException("invalid evaluatedCandidateCount");
        }
    }
}
