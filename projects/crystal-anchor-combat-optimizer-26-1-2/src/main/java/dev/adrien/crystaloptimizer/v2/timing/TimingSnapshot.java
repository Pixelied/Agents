package dev.adrien.crystaloptimizer.v2.timing;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable timing evidence captured on the client thread for worker use. */
public record TimingSnapshot(
    long capturedAtNanos,
    Map<TimingTransition, TimingDistribution> distributions
) {
    public TimingSnapshot {
        if (capturedAtNanos < 0L) {
            throw new IllegalArgumentException("capturedAtNanos must be non-negative");
        }
        Objects.requireNonNull(distributions, "distributions");
        EnumMap<TimingTransition, TimingDistribution> copy = new EnumMap<>(TimingTransition.class);
        distributions.forEach((transition, distribution) -> copy.put(
            Objects.requireNonNull(transition, "timing transition"),
            Objects.requireNonNull(distribution, "timing distribution")
        ));
        distributions = Map.copyOf(copy);
    }

    public static TimingSnapshot capture(TimingEngine engine, long nowNanos) {
        Objects.requireNonNull(engine, "engine");
        EnumMap<TimingTransition, TimingDistribution> captured = new EnumMap<>(TimingTransition.class);
        for (TimingTransition transition : TimingTransition.values()) {
            captured.put(transition, engine.distribution(transition, nowNanos));
        }
        return new TimingSnapshot(nowNanos, captured);
    }

    public static TimingSnapshot empty(long capturedAtNanos) {
        EnumMap<TimingTransition, TimingDistribution> empty = new EnumMap<>(TimingTransition.class);
        for (TimingTransition transition : TimingTransition.values()) {
            empty.put(
                transition,
                transition == TimingTransition.IMMEDIATE
                    ? TimingDistribution.immediate(capturedAtNanos)
                    : TimingDistribution.unknown()
            );
        }
        return new TimingSnapshot(capturedAtNanos, empty);
    }

    public TimingDistribution distribution(TimingTransition transition) {
        Objects.requireNonNull(transition, "transition");
        return distributions.getOrDefault(
            transition,
            transition == TimingTransition.IMMEDIATE
                ? TimingDistribution.immediate(capturedAtNanos)
                : TimingDistribution.unknown()
        );
    }

    public SequenceTiming estimateSequence(List<TimingTransition> transitions) {
        Objects.requireNonNull(transitions, "transitions");
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException("sequence must contain at least one transition");
        }

        int hardBoundaries = 0;
        double expectedMillis = 0.0;
        double p90Millis = 0.0;
        double confidence = 1.0;
        for (TimingTransition transition : transitions) {
            Objects.requireNonNull(transition, "transition");
            if (transition.hardFeedback()) {
                hardBoundaries++;
            }
            if (transition == TimingTransition.IMMEDIATE) {
                continue;
            }
            TimingDistribution distribution = distribution(transition);
            if (transition.hardFeedback()
                && (distribution.sampleCount() == 0 || distribution.confidence() <= 0.0)) {
                return SequenceTiming.unknown(hardBoundaries);
            }
            if (distribution.sampleCount() == 0) {
                confidence = 0.0;
                continue;
            }
            expectedMillis += distribution.p50Millis();
            p90Millis += distribution.p90Millis();
            confidence = Math.min(confidence, distribution.confidence());
        }
        return new SequenceTiming(expectedMillis, p90Millis, hardBoundaries, confidence);
    }
}
