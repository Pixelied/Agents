package dev.adrien.crystaloptimizer.planner;

public record PlannerBudget(int beamWidth, int maxDepth, long maxNanos) {
    public PlannerBudget {
        if (beamWidth <= 0) {
            throw new IllegalArgumentException("beamWidth must be positive");
        }
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (maxNanos <= 0L) {
            throw new IllegalArgumentException("maxNanos must be positive");
        }
    }
}
