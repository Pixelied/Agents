package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class HurtWindowTracker {
    private static final long MAX_EVIDENCE_AGE_NANOS = 1_000_000_000L;
    private final Map<UUID, Evidence> evidence = new HashMap<>();

    public synchronized void observeAttributedIncoming(
        UUID targetId,
        float incoming,
        int invulnerableTime,
        long nowNanos
    ) {
        Objects.requireNonNull(targetId, "targetId");
        if (!Float.isFinite(incoming) || incoming < 0.0f) {
            throw new IllegalArgumentException("incoming must be finite and non-negative");
        }
        if (invulnerableTime <= 10) {
            evidence.remove(targetId);
            return;
        }
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        evidence.put(targetId, new Evidence(incoming, invulnerableTime, nowNanos));
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

        Evidence known = evidence.get(targetId);
        if (known == null
            || nowNanos < known.observedAtNanos()
            || nowNanos - known.observedAtNanos() > MAX_EVIDENCE_AGE_NANOS
            || observedInvulnerableTime > known.invulnerableTime()) {
            return HurtThresholdEstimate.unknownProtected();
        }
        return HurtThresholdEstimate.exact(known.incoming());
    }

    public synchronized void clear(UUID targetId) {
        if (targetId != null) {
            evidence.remove(targetId);
        }
    }

    public synchronized void clear() {
        evidence.clear();
    }

    private record Evidence(float incoming, int invulnerableTime, long observedAtNanos) {
    }
}
