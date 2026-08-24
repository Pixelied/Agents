package dev.adrien.crystaloptimizer.v2.timing;

public record TimingDistribution(
    int sampleCount,
    double p50Millis,
    double p90Millis,
    double medianAbsoluteDeviationMillis,
    double confidence,
    long newestSampleNanos
) {
    public TimingDistribution {
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        if (sampleCount == 0) {
            boolean unknown = Double.isInfinite(p50Millis)
                && p50Millis > 0.0
                && Double.isInfinite(p90Millis)
                && p90Millis > 0.0
                && confidence == 0.0;
            boolean immediate = p50Millis == 0.0
                && p90Millis == 0.0
                && confidence == 1.0;
            if (!unknown && !immediate) {
                throw new IllegalArgumentException("invalid empty timing distribution");
            }
        } else if (!Double.isFinite(p50Millis)
            || !Double.isFinite(p90Millis)
            || p50Millis < 0.0
            || p90Millis < p50Millis) {
            throw new IllegalArgumentException("invalid timing percentiles");
        }
        if (!Double.isFinite(medianAbsoluteDeviationMillis)
            || medianAbsoluteDeviationMillis < 0.0) {
            throw new IllegalArgumentException("invalid timing dispersion");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence outside [0,1]");
        }
    }

    public static TimingDistribution immediate(long nowNanos) {
        return new TimingDistribution(0, 0.0, 0.0, 0.0, 1.0, nowNanos);
    }

    public static TimingDistribution unknown() {
        return new TimingDistribution(
            0,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            0.0,
            0.0,
            0L
        );
    }
}
