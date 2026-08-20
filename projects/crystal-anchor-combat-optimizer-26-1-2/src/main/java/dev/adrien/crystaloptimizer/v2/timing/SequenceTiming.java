package dev.adrien.crystaloptimizer.v2.timing;

public record SequenceTiming(
    double expectedMillis,
    double p90Millis,
    int hardFeedbackBoundaries,
    double confidence
) {
    public SequenceTiming {
        boolean finitePair = Double.isFinite(expectedMillis) && Double.isFinite(p90Millis);
        boolean infinitePair = Double.isInfinite(expectedMillis) && Double.isInfinite(p90Millis)
            && expectedMillis > 0.0 && p90Millis > 0.0;
        if (!finitePair && !infinitePair) {
            throw new IllegalArgumentException("timing bounds must both be finite or +infinity");
        }
        if (finitePair && (expectedMillis < 0.0 || p90Millis < expectedMillis)) {
            throw new IllegalArgumentException("invalid timing bounds");
        }
        if (hardFeedbackBoundaries < 0) {
            throw new IllegalArgumentException("hardFeedbackBoundaries must be non-negative");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence outside [0,1]");
        }
    }

    public static SequenceTiming immediate() {
        return new SequenceTiming(0.0, 0.0, 0, 1.0);
    }

    public static SequenceTiming unknown(int boundaries) {
        return new SequenceTiming(
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            boundaries,
            0.0
        );
    }
}
