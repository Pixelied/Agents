package dev.adrien.crystaloptimizer.prediction;

import java.util.Objects;
import net.minecraft.world.phys.Vec3;

/**
 * Legacy point-only hypothesis retained for compatibility. V3 prediction
 * publishes PredictedSpatialState so collision-constrained AABBs travel with
 * every hypothesis.
 */
public record PositionHypothesis(Kind kind, Vec3 position, Vec3 velocity, double weight) {
    public PositionHypothesis {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(velocity, "velocity");
        if (!Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be in (0, 1]");
        }
    }

    public enum Kind {
        LIKELY_INERTIAL,
        BRAKING,
        TURN_OR_REVERSAL,
        /** @deprecated use LIKELY_INERTIAL */
        @Deprecated LIKELY,
        /** @deprecated use BRAKING */
        @Deprecated SLOWED_OR_REVERSAL,
        /** @deprecated use TURN_OR_REVERSAL */
        @Deprecated CONSERVATIVE_BOUND
    }
}
