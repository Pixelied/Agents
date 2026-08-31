package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reconciles authoritative health/absorption observations with predicted vanilla damage. */
public final class ServerDamageStateReconciler {
    private static final float EPSILON = 0.0001f;
    private static final String EXPLOSION = "minecraft:explosion";
    private static final String PLAYER_EXPLOSION = "minecraft:player_explosion";
    private final DamageSimulator damageSimulator;

    public ServerDamageStateReconciler() {
        this(new DamageSimulator());
    }

    ServerDamageStateReconciler(DamageSimulator damageSimulator) {
        this.damageSimulator = Objects.requireNonNull(damageSimulator, "damageSimulator");
    }

    public HurtState reconcile(
        PlayerSnapshot before,
        List<ThreatEvent> candidateEvents,
        float observedHealth,
        float observedAbsorption,
        List<DamageEventObservation> damageEvents
    ) {
        Objects.requireNonNull(before, "before");
        Objects.requireNonNull(candidateEvents, "candidateEvents");
        Objects.requireNonNull(damageEvents, "damageEvents");
        if (!Float.isFinite(observedHealth) || observedHealth < 0f) {
            throw new IllegalArgumentException("observedHealth must be finite and non-negative");
        }
        if (!Float.isFinite(observedAbsorption) || observedAbsorption < 0f) {
            throw new IllegalArgumentException("observedAbsorption must be finite and non-negative");
        }

        List<HurtState> matches = new ArrayList<>();
        for (ThreatEvent event : candidateEvents) {
            if (event == null || !exactRawDamage(event)) continue;
            DamageResult result = damageSimulator.simulate(before, event.damage());
            if (result.rejected() || !matchesObservedState(result.after(), observedHealth, observedAbsorption)) {
                continue;
            }

            boolean differential = isDifferential(before.hurtState(), event.damage());
            if (differential) {
                if (!damageEvents.isEmpty()) continue;
            } else if (!hasUniqueMatchingDamageEvent(event, damageEvents)) {
                continue;
            }
            HurtState state = result.after().hurtState();
            matches.add(new HurtState(state.lastHurt(), state.invulnerableTime(), Confidence.MATCHED));
        }

        if (matches.size() == 1) return matches.getFirst();
        if (matches.size() > 1) return HurtState.unknown();

        boolean unchanged = nearlyEqual(before.health(), observedHealth)
            && nearlyEqual(before.absorption(), observedAbsorption);
        if (unchanged && damageEvents.isEmpty()) return before.hurtState();
        return HurtState.unknown();
    }

    private static boolean exactRawDamage(ThreatEvent event) {
        return Float.compare(event.damage().rawDamage().min(), event.damage().rawDamage().max()) == 0;
    }

    private static boolean matchesObservedState(PlayerSnapshot after, float health, float absorption) {
        return nearlyEqual(after.health(), health) && nearlyEqual(after.absorption(), absorption);
    }

    private static boolean isDifferential(HurtState prior, DamageSourceSnapshot source) {
        boolean trusted = prior.confidence() == Confidence.EXACT || prior.confidence() == Confidence.MATCHED;
        return trusted
            && prior.invulnerableTime() > 10
            && !source.has(DamageFlag.BYPASSES_COOLDOWN)
            && source.rawDamage().max() > prior.lastHurt().min();
    }

    private static boolean hasUniqueMatchingDamageEvent(
        ThreatEvent event,
        List<DamageEventObservation> observations
    ) {
        if (observations.size() != 1) return false;
        DamageEventObservation observation = observations.getFirst();
        return observation != null
            && compatibleSourceKey(event.damage().sourceKey(), observation.sourceKey())
            && event.impact().overlaps(observation.observedAt());
    }

    private static boolean compatibleSourceKey(String predicted, String observed) {
        if (predicted.equals(observed)) return true;
        return (EXPLOSION.equals(predicted) && PLAYER_EXPLOSION.equals(observed))
            || (PLAYER_EXPLOSION.equals(predicted) && EXPLOSION.equals(observed));
    }

    private static boolean nearlyEqual(float first, float second) {
        return Math.abs(first - second) <= EPSILON;
    }

    public record DamageEventObservation(String sourceKey, TickWindow observedAt) {
        public DamageEventObservation {
            sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
            observedAt = Objects.requireNonNull(observedAt, "observedAt");
        }
    }
}
