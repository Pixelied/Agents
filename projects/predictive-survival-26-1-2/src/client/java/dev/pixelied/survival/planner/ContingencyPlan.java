package dev.pixelied.survival.planner;

import dev.pixelied.survival.timeline.TimelineResult;

import java.util.List;
import java.util.Objects;

public record ContingencyPlan(
    List<PlannedStep> steps,
    TimelineResult result,
    boolean guaranteed,
    int evaluatedSequenceCount,
    boolean truncated,
    String reason
) {
    public ContingencyPlan {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
        result = Objects.requireNonNull(result, "result");
        reason = Objects.requireNonNull(reason, "reason");
        if (evaluatedSequenceCount < 0) throw new IllegalArgumentException("evaluatedSequenceCount must be non-negative");
        if (guaranteed && !result.survived()) {
            throw new IllegalArgumentException("a guaranteed contingency must survive its modeled timeline");
        }
    }

    public static ContingencyPlan baseline(TimelineResult result) {
        return new ContingencyPlan(List.of(), result, result.survived(), 0, false,
            result.survived() ? "baseline survives" : "no guaranteed rescue sequence");
    }
}
