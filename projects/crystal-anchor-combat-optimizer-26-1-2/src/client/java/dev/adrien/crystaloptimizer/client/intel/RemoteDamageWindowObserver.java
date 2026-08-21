package dev.adrien.crystaloptimizer.client.intel;

import dev.adrien.crystaloptimizer.v2.strategy.DamageWindowEvidence;
import dev.adrien.crystaloptimizer.v2.strategy.HurtWindowTracker;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class RemoteDamageWindowObserver {
    static final int MAX_CANDIDATES = 16;
    static final long MAX_AGE_NANOS = 1_000_000_000L;
    private static final RemoteDamageWindowObserver INSTANCE = new RemoteDamageWindowObserver();

    private final ArrayDeque<ExplosionCandidate> candidates = new ArrayDeque<>();
    private HurtWindowTracker tracker;

    public static RemoteDamageWindowObserver instance() {
        return INSTANCE;
    }

    public synchronized void bind(HurtWindowTracker nextTracker) {
        tracker = Objects.requireNonNull(nextTracker, "nextTracker");
        candidates.clear();
    }

    public synchronized void onExplosionCandidate(
        UUID targetId,
        float postMitigationIncoming,
        long timestampNanos
    ) {
        Objects.requireNonNull(targetId, "targetId");
        if (!Float.isFinite(postMitigationIncoming) || postMitigationIncoming < 0.0f) {
            throw new IllegalArgumentException("postMitigationIncoming must be finite and non-negative");
        }
        requireTimestamp(timestampNanos);
        prune(timestampNanos);
        candidates.addLast(new ExplosionCandidate(targetId, postMitigationIncoming, timestampNanos));
        while (candidates.size() > MAX_CANDIDATES) {
            candidates.removeFirst();
        }
    }

    public synchronized void onObservedTargetState(
        UUID targetId,
        float health,
        int invulnerableTime,
        long timestampNanos
    ) {
        Objects.requireNonNull(targetId, "targetId");
        if (!Float.isFinite(health) || health < 0.0f || invulnerableTime < 0) {
            throw new IllegalArgumentException("observed target state must be finite and non-negative");
        }
        requireTimestamp(timestampNanos);
        prune(timestampNanos);
        HurtWindowTracker sink = tracker;
        if (sink == null) {
            return;
        }
        if (invulnerableTime <= 10) {
            sink.clear(targetId);
            removeTarget(targetId);
            return;
        }

        List<ExplosionCandidate> plausible = candidates.stream()
            .filter(candidate -> candidate.targetId().equals(targetId))
            .filter(candidate -> candidate.timestampNanos() <= timestampNanos)
            .toList();
        if (plausible.isEmpty()) {
            return;
        }

        DamageWindowEvidence evidence;
        if (plausible.size() == 1) {
            evidence = DamageWindowEvidence.exact(
                plausible.getFirst().postMitigationIncoming(),
                invulnerableTime,
                timestampNanos
            );
        } else {
            float lower = Float.POSITIVE_INFINITY;
            float upper = Float.NEGATIVE_INFINITY;
            double total = 0.0;
            for (ExplosionCandidate candidate : plausible) {
                lower = Math.min(lower, candidate.postMitigationIncoming());
                upper = Math.max(upper, candidate.postMitigationIncoming());
                total += candidate.postMitigationIncoming();
            }
            evidence = DamageWindowEvidence.bounded(
                lower,
                (float) (total / plausible.size()),
                upper,
                invulnerableTime,
                timestampNanos
            );
        }
        sink.observeEvidence(targetId, evidence);
        removeTarget(targetId);
    }

    public synchronized void clear() {
        candidates.clear();
        if (tracker != null) {
            tracker.clear();
        }
    }

    private void prune(long nowNanos) {
        Iterator<ExplosionCandidate> iterator = candidates.iterator();
        while (iterator.hasNext()) {
            ExplosionCandidate candidate = iterator.next();
            if (candidate.timestampNanos() > nowNanos
                || nowNanos - candidate.timestampNanos() > MAX_AGE_NANOS) {
                iterator.remove();
            }
        }
    }

    private void removeTarget(UUID targetId) {
        List<ExplosionCandidate> keep = new ArrayList<>();
        for (ExplosionCandidate candidate : candidates) {
            if (!candidate.targetId().equals(targetId)) {
                keep.add(candidate);
            }
        }
        candidates.clear();
        candidates.addAll(keep);
    }

    private static void requireTimestamp(long timestampNanos) {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
    }

    private record ExplosionCandidate(
        UUID targetId,
        float postMitigationIncoming,
        long timestampNanos
    ) {
    }

    private RemoteDamageWindowObserver() {
    }
}
