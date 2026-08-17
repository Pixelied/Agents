package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.List;
import java.util.Objects;

public record CombatPlan(
    List<CombatAction> actions,
    PlanScore score,
    PacketDependencyGraph dependencyGraph,
    boolean lethal,
    double robustness
) {
    public CombatPlan {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(score, "score");
        Objects.requireNonNull(dependencyGraph, "dependencyGraph");
        if (!Double.isFinite(robustness) || robustness < 0.0 || robustness > 1.0) {
            throw new IllegalArgumentException("robustness must be in [0, 1]");
        }
        actions = List.copyOf(actions);
    }
}
