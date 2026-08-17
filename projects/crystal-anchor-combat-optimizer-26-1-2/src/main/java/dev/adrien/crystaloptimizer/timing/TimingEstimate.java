package dev.adrien.crystaloptimizer.timing;

import java.util.Objects;

public record TimingEstimate(
    double medianAckDelayMillis,
    double jitterMillis,
    double sameTickProbability,
    int sampleCount,
    double confidence,
    CompletionDistribution completionDistribution
) {
    public TimingEstimate {
        if (!Double.isFinite(medianAckDelayMillis) || medianAckDelayMillis < 0.0) {
            throw new IllegalArgumentException("medianAckDelayMillis must be non-negative and finite");
        }
        if (!Double.isFinite(jitterMillis) || jitterMillis < 0.0) {
            throw new IllegalArgumentException("jitterMillis must be non-negative and finite");
        }
        requireProbability(sameTickProbability, "sameTickProbability");
        if (sampleCount < 0) {
            throw new IllegalArgumentException("sampleCount must be non-negative");
        }
        requireProbability(confidence, "confidence");
        Objects.requireNonNull(completionDistribution, "completionDistribution");
    }

    public static TimingEstimate unknown() {
        return new TimingEstimate(
            0.0,
            0.0,
            0.0,
            0,
            0.0,
            CompletionDistribution.fromSameTickProbability(0.0)
        );
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
