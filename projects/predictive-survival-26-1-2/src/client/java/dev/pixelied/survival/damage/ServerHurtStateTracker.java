package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.TickWindow;

import java.util.Objects;

public final class ServerHurtStateTracker {
    private HurtState current = HurtState.unknown();
    private TickWindow pendingPredictedWindow;

    public HurtState current() {
        return current;
    }

    public void tick(int elapsedServerTicks) {
        if (elapsedServerTicks < 0) {
            throw new IllegalArgumentException("elapsedServerTicks must be non-negative");
        }
        current = new HurtState(
            current.lastHurt(),
            Math.max(0, current.invulnerableTime() - elapsedServerTicks),
            current.confidence()
        );
    }

    public void recordPredictedApplied(float preArmorLastHurt, TickWindow appliedAt) {
        Objects.requireNonNull(appliedAt, "appliedAt");
        if (preArmorLastHurt < 0f || !Float.isFinite(preArmorLastHurt)) {
            throw new IllegalArgumentException("preArmorLastHurt must be finite and non-negative");
        }

        Confidence confidence = appliedAt.earliest() == appliedAt.latest()
            ? Confidence.EXACT
            : Confidence.BOUNDED;
        current = new HurtState(DamageRange.exact(preArmorLastHurt), 20, confidence);
        pendingPredictedWindow = appliedAt;
    }

    public void recordObservedHealthDelta(float healthDelta, TickWindow observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        if (healthDelta < 0f || !Float.isFinite(healthDelta)) {
            throw new IllegalArgumentException("healthDelta must be finite and non-negative");
        }

        if (pendingPredictedWindow != null && pendingPredictedWindow.overlaps(observedAt)) {
            current = new HurtState(current.lastHurt(), current.invulnerableTime(), Confidence.MATCHED);
            pendingPredictedWindow = null;
            return;
        }

        invalidate();
    }

    public void invalidate() {
        current = HurtState.unknown();
        pendingPredictedWindow = null;
    }

    public HurtState conservativeForLethalDecision() {
        boolean trusted = (current.confidence() == Confidence.EXACT || current.confidence() == Confidence.MATCHED)
            && current.invulnerableTime() > 10;
        if (trusted) {
            return current;
        }
        return new HurtState(DamageRange.exact(0f), current.invulnerableTime(), current.confidence());
    }
}
