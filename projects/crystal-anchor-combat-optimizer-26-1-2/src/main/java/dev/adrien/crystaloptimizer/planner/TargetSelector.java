package dev.adrien.crystaloptimizer.planner;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TargetSelector {
    private final double hysteresisMargin;
    private final double threatWeight;

    public TargetSelector(double hysteresisMargin, double threatWeight) {
        if (!Double.isFinite(hysteresisMargin) || hysteresisMargin < 0.0) {
            throw new IllegalArgumentException("hysteresisMargin must be non-negative and finite");
        }
        if (!Double.isFinite(threatWeight) || threatWeight < 0.0) {
            throw new IllegalArgumentException("threatWeight must be non-negative and finite");
        }
        this.hysteresisMargin = hysteresisMargin;
        this.threatWeight = threatWeight;
    }

    public TargetPriority select(UUID previousTarget, List<TargetPriority> candidates, RiskBudget riskBudget) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(riskBudget, "riskBudget");
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("at least one target candidate is required");
        }

        TargetPriority best = candidates.stream()
            .max(Comparator.comparingDouble(this::score))
            .orElseThrow();
        if (previousTarget == null) {
            return best;
        }

        TargetPriority previous = candidates.stream()
            .filter(candidate -> candidate.targetId().equals(previousTarget))
            .findFirst()
            .orElse(null);
        if (previous == null) {
            return best;
        }

        return score(best) - score(previous) <= hysteresisMargin ? previous : best;
    }

    private double score(TargetPriority priority) {
        return priority.killOpportunity() + priority.threat() * threatWeight - priority.distance() * 0.001;
    }
}
