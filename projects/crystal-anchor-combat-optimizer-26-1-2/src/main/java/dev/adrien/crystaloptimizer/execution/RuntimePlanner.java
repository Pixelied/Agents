package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.planner.CombatPlan;

@FunctionalInterface
public interface RuntimePlanner {
    CombatPlan plan(RuntimeFrame frame);
}
