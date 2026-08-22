package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HurtWindowTracker {
    private static final long MAX_EVIDENCE_AGE_NANOS = 1_000_000_000L;
    private static final double BOUNDED_CONFIDENCE = 0.65;
    private final Map<UUID, DamageWindowEvidence> evidence = new HashMap<>();

    public synchronized void observeEvidence(UUID targetId, DamageWindowEvidence observation) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(observation, "observation");
        if (observation.invulnerableTime() <= 10
            || observation.confidence() == DamageWindowEvidence.Confidence.UNKNOWN) {
            evidence.remove(targetId);
            return;
        }
        evidence.put(targetId, observation);
    }

    public synchronized HurtThresholdEstimate estimate(
        UUID targetId,
        int observedInvulnerableTime,
        long nowNanos
    ) {
        Objects.requireNonNull(targetId, "targetId");
        if (observedInvulnerableTime < 0 || nowNanos < 0L) {
            throw new IllegalArgumentException("observed state must be non-negative");
        }
        if (observedInvulnerableTime <= 10) {
            evidence.remove(targetId);
            return HurtThresholdEstimate.unprotected();
        }

        DamageWindowEvidence known = evidence.get(targetId);
        if (known == null) {
            return HurtThresholdEstimate.unknownProtected();
        }
        if (nowNanos < known.observedAtNanos()
            || nowNanos - known.observedAtNanos() > MAX_EVIDENCE_AGE_NANOS
            || observedInvulnerableTime > known.invulnerableTime()) {
            evidence.remove(targetId);
            return HurtThresholdEstimate.unknownProtected();
        }

        return switch (known.confidence()) {
            case EXACT -> HurtThresholdEstimate.exact(known.expectedIncoming());
            case BOUNDED -> HurtThresholdEstimate.bounded(
                known.lowerIncoming(),
                known.expectedIncoming(),
                known.upperIncoming(),
                BOUNDED_CONFIDENCE
            );
            case UNKNOWN -> HurtThresholdEstimate.unknownProtected();
        };
    }

    public synchronized void clear(UUID targetId) {
        if (targetId != null) {
            evidence.remove(targetId);
        }
    }

    public synchronized void clear() {
        evidence.clear();
    }
}
