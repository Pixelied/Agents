package dev.adrien.crystaloptimizer.v2.strategy;

public record HurtThresholdEstimate(
    float lowerBound,
    float expected,
    float upperBound,
    double confidence
) {
    public static final float MAX_CRYSTAL_HARD_INCOMING = 127.5f;

    public HurtThresholdEstimate {
        if (!Float.isFinite(lowerBound) || !Float.isFinite(expected) || !Float.isFinite(upperBound)) {
            throw new IllegalArgumentException("hurt threshold bounds must be finite");
        }
        if (lowerBound < 0.0f || lowerBound > expected || expected > upperBound) {
            throw new IllegalArgumentException("unordered hurt threshold bounds");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence outside [0,1]");
        }
    }

    public static HurtThresholdEstimate exact(float threshold) {
        return new HurtThresholdEstimate(threshold, threshold, threshold, 1.0);
    }

    public static HurtThresholdEstimate bounded(
        float lowerBound,
        float expected,
        float upperBound,
        double confidence
    ) {
        if (confidence <= 0.0 || confidence >= 1.0) {
            throw new IllegalArgumentException("bounded confidence must be strictly between 0 and 1");
        }
        return new HurtThresholdEstimate(lowerBound, expected, upperBound, confidence);
    }

    public static HurtThresholdEstimate unprotected() {
        return exact(0.0f);
    }

    public static HurtThresholdEstimate unknownProtected() {
        return new HurtThresholdEstimate(
            0.0f,
            MAX_CRYSTAL_HARD_INCOMING,
            MAX_CRYSTAL_HARD_INCOMING,
            0.0
        );
    }

    public boolean exact() {
        return confidence == 1.0
            && Float.compare(lowerBound, expected) == 0
            && Float.compare(expected, upperBound) == 0;
    }
}
