package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.Objects;

/**
 * Execution compatibility policy. Profiles may only add constraints to vanilla-authoritative
 * behavior; they never manufacture reach, visibility, damage, or packet acceptance.
 */
public enum ServerCompatibilityProfile {
    VANILLA {
        @Override
        public CompatibilityConstraints constraints(TimingSnapshot timing, int observedRejections) {
            validate(timing, observedRejections);
            return CompatibilityConstraints.vanilla();
        }
    },
    ADAPTIVE {
        @Override
        public CompatibilityConstraints constraints(TimingSnapshot timing, int observedRejections) {
            validate(timing, observedRejections);
            if (observedRejections == 0) {
                return VANILLA.constraints(timing, 0);
            }
            long evidenceSpacing = observedCadenceNanos(timing);
            long rejectionFloor = saturatingMultiply(
                Math.min(observedRejections, 10),
                5_000_000L
            );
            long spacing = Math.max(evidenceSpacing, rejectionFloor);
            return new CompatibilityConstraints(false, false, spacing, spacing);
        }
    },
    STRICT {
        @Override
        public CompatibilityConstraints constraints(TimingSnapshot timing, int observedRejections) {
            validate(timing, observedRejections);
            long evidenceSpacing = observedCadenceNanos(timing);
            long rejectionFloor = saturatingMultiply(
                Math.max(1, Math.min(observedRejections, 10)),
                10_000_000L
            );
            long spacing = Math.max(evidenceSpacing, rejectionFloor);
            return new CompatibilityConstraints(true, true, spacing, spacing);
        }
    };

    public abstract CompatibilityConstraints constraints(
        TimingSnapshot timing,
        int observedRejections
    );

    private static void validate(TimingSnapshot timing, int observedRejections) {
        Objects.requireNonNull(timing, "timing");
        if (observedRejections < 0) {
            throw new IllegalArgumentException("observedRejections must be non-negative");
        }
    }

    private static long observedCadenceNanos(TimingSnapshot timing) {
        TimingDistribution cadence = timing.distribution(TimingTransition.SERVER_UPDATE_CADENCE);
        if (cadence.sampleCount() == 0 || !Double.isFinite(cadence.p90Millis())) {
            return 0L;
        }
        double nanos = Math.ceil(Math.max(0.0, cadence.p90Millis()) * 1_000_000.0);
        return nanos >= Long.MAX_VALUE ? Long.MAX_VALUE : (long) nanos;
    }

    private static long saturatingMultiply(int multiplier, long value) {
        if (multiplier <= 0 || value == 0L) {
            return 0L;
        }
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }
}
