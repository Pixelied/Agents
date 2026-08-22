package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.Objects;

public record DamageWindowEvidence(
    float lowerIncoming,
    float expectedIncoming,
    float upperIncoming,
    int invulnerableTime,
    long observedAtNanos,
    Confidence confidence
) {
    public enum Confidence {
        EXACT,
        BOUNDED,
        UNKNOWN
    }

    public DamageWindowEvidence {
        Objects.requireNonNull(confidence, "confidence");
        if (!Float.isFinite(lowerIncoming)
            || !Float.isFinite(expectedIncoming)
            || !Float.isFinite(upperIncoming)) {
            throw new IllegalArgumentException("incoming bounds must be finite");
        }
        if (lowerIncoming < 0.0f
            || lowerIncoming > expectedIncoming
            || expectedIncoming > upperIncoming) {
            throw new IllegalArgumentException("unordered incoming bounds");
        }
        if (invulnerableTime < 0) {
            throw new IllegalArgumentException("invulnerableTime must be non-negative");
        }
        if (observedAtNanos < 0L) {
            throw new IllegalArgumentException("observedAtNanos must be non-negative");
        }
        if (confidence == Confidence.EXACT
            && (Float.compare(lowerIncoming, expectedIncoming) != 0
                || Float.compare(expectedIncoming, upperIncoming) != 0)) {
            throw new IllegalArgumentException("exact evidence must collapse to one incoming value");
        }
    }

    public static DamageWindowEvidence exact(
        float incoming,
        int invulnerableTime,
        long nowNanos
    ) {
        return new DamageWindowEvidence(
            incoming,
            incoming,
            incoming,
            invulnerableTime,
            nowNanos,
            Confidence.EXACT
        );
    }

    public static DamageWindowEvidence bounded(
        float lower,
        float expected,
        float upper,
        int invulnerableTime,
        long nowNanos
    ) {
        return new DamageWindowEvidence(
            lower,
            expected,
            upper,
            invulnerableTime,
            nowNanos,
            Confidence.BOUNDED
        );
    }
}
