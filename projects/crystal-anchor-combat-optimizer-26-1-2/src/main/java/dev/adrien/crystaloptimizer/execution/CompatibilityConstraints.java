package dev.adrien.crystaloptimizer.execution;

/** Server-facing restrictions that may only make execution more conservative than vanilla. */
public record CompatibilityConstraints(
    boolean requireVisibleFace,
    boolean requireFullRotation,
    long minimumSwapSpacingNanos,
    long minimumInteractionSpacingNanos
) {
    public CompatibilityConstraints {
        if (minimumSwapSpacingNanos < 0L || minimumInteractionSpacingNanos < 0L) {
            throw new IllegalArgumentException("compatibility spacing must be non-negative");
        }
    }

    public static CompatibilityConstraints vanilla() {
        return new CompatibilityConstraints(false, false, 0L, 0L);
    }

    public CompatibilityConstraints tightenedWith(CompatibilityConstraints other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return new CompatibilityConstraints(
            requireVisibleFace || other.requireVisibleFace,
            requireFullRotation || other.requireFullRotation,
            Math.max(minimumSwapSpacingNanos, other.minimumSwapSpacingNanos),
            Math.max(minimumInteractionSpacingNanos, other.minimumInteractionSpacingNanos)
        );
    }
}
