package dev.adrien.crystaloptimizer.v2.strategy;

/** Absolute-deadline budget for one immutable strategic sequence search. */
public record PlanningBudget(
    int maxDepth,
    int beamWidth,
    int maxBranchesPerNode,
    long deadlineNanos
) {
    public PlanningBudget {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be positive");
        }
        if (beamWidth <= 0) {
            throw new IllegalArgumentException("beamWidth must be positive");
        }
        if (maxBranchesPerNode <= 0) {
            throw new IllegalArgumentException("maxBranchesPerNode must be positive");
        }
        if (deadlineNanos < 0L) {
            throw new IllegalArgumentException("deadlineNanos must be non-negative");
        }
    }

    public static PlanningBudget defaults(long deadlineNanos) {
        return new PlanningBudget(3, 12, 24, deadlineNanos);
    }

    public long remainingNanos(long nowNanos) {
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        if (deadlineNanos <= nowNanos) {
            return 1L;
        }
        return deadlineNanos - nowNanos;
    }
}
