package dev.adrien.crystaloptimizer.world;

import java.util.Objects;

public record ExplosionTerrainOutcome(
    BlockDeltaOverlay overlay,
    boolean exact,
    double weight
) {
    public ExplosionTerrainOutcome {
        Objects.requireNonNull(overlay, "overlay");
        if (!Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be finite and in (0, 1]");
        }
    }

    public static ExplosionTerrainOutcome unobserved(BlockDeltaOverlay overlay) {
        return new ExplosionTerrainOutcome(overlay, false, 1.0);
    }
}
