package dev.adrien.crystaloptimizer.timing;

public record CompletionDistribution(
    double sameTickProbability,
    double nextTickProbability,
    double laterProbability
) {
    public CompletionDistribution {
        requireProbability(sameTickProbability, "sameTickProbability");
        requireProbability(nextTickProbability, "nextTickProbability");
        requireProbability(laterProbability, "laterProbability");
        if (Math.abs(sameTickProbability + nextTickProbability + laterProbability - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException("completion probabilities must sum to 1.0");
        }
    }

    public static CompletionDistribution fromSameTickProbability(double sameTickProbability) {
        requireProbability(sameTickProbability, "sameTickProbability");
        double remaining = 1.0 - sameTickProbability;
        double next = remaining * 0.75;
        return new CompletionDistribution(sameTickProbability, next, remaining - next);
    }

    public double totalProbability() {
        return sameTickProbability + nextTickProbability + laterProbability;
    }

    private static void requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
