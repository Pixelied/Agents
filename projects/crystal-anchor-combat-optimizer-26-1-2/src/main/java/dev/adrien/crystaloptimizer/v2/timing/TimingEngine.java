package dev.adrien.crystaloptimizer.v2.timing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TimingEngine {
    private final int sampleCapacity;
    private final long staleAfterNanos;
    private final Map<TimingCorrelation, Long> pending = new HashMap<>();
    private final EnumMap<TimingTransition, Deque<Sample>> completed =
        new EnumMap<>(TimingTransition.class);

    public TimingEngine(int sampleCapacity, long staleAfterNanos) {
        if (sampleCapacity <= 0) {
            throw new IllegalArgumentException("sampleCapacity must be positive");
        }
        if (staleAfterNanos <= 0L) {
            throw new IllegalArgumentException("staleAfterNanos must be positive");
        }
        this.sampleCapacity = sampleCapacity;
        this.staleAfterNanos = staleAfterNanos;
        for (TimingTransition transition : TimingTransition.values()) {
            completed.put(transition, new ArrayDeque<>());
        }
    }

    public synchronized void recordStart(TimingCorrelation correlation, long startNanos) {
        Objects.requireNonNull(correlation, "correlation");
        if (startNanos < 0L) {
            throw new IllegalArgumentException("startNanos must be non-negative");
        }
        if (correlation.transition() == TimingTransition.IMMEDIATE) {
            throw new IllegalArgumentException("IMMEDIATE does not require correlation");
        }
        pending.put(correlation, startNanos);
    }

    public synchronized boolean recordEnd(TimingCorrelation correlation, long endNanos) {
        Objects.requireNonNull(correlation, "correlation");
        if (endNanos < 0L) {
            throw new IllegalArgumentException("endNanos must be non-negative");
        }
        Long startNanos = pending.remove(correlation);
        if (startNanos == null || endNanos < startNanos) {
            return false;
        }

        Deque<Sample> samples = completed.get(correlation.transition());
        samples.addLast(new Sample(endNanos - startNanos, endNanos));
        while (samples.size() > sampleCapacity) {
            samples.removeFirst();
        }
        return true;
    }

    public synchronized TimingDistribution distribution(
        TimingTransition transition,
        long nowNanos
    ) {
        Objects.requireNonNull(transition, "transition");
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        if (transition == TimingTransition.IMMEDIATE) {
            return new TimingDistribution(0, 0.0, 0.0, 0.0, 1.0, nowNanos);
        }

        Deque<Sample> samples = completed.get(transition);
        if (samples.isEmpty()) {
            return TimingDistribution.unknown();
        }

        List<Double> millis = samples.stream()
            .map(sample -> sample.durationNanos() / 1_000_000.0)
            .sorted()
            .toList();
        double p50 = percentile(millis, 0.50);
        double p90 = percentile(millis, 0.90);
        List<Double> deviations = millis.stream()
            .map(value -> Math.abs(value - p50))
            .sorted()
            .toList();
        double mad = percentile(deviations, 0.50);

        long newest = samples.stream()
            .max(Comparator.comparingLong(Sample::endNanos))
            .orElseThrow()
            .endNanos();
        long ageNanos = Math.max(0L, nowNanos - newest);
        double freshness = Math.max(
            0.0,
            1.0 - Math.min(1.0, ageNanos / (double) staleAfterNanos)
        );
        double sampleConfidence = Math.min(1.0, samples.size() / 8.0);
        double confidence = sampleConfidence * freshness;

        return new TimingDistribution(
            samples.size(),
            p50,
            p90,
            mad,
            confidence,
            newest
        );
    }

    public synchronized SequenceTiming estimateSequence(
        List<TimingTransition> transitions,
        long nowNanos
    ) {
        Objects.requireNonNull(transitions, "transitions");
        if (transitions.isEmpty()) {
            throw new IllegalArgumentException("sequence must contain at least one transition");
        }
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }

        int hardBoundaries = 0;
        double expectedMillis = 0.0;
        double p90Millis = 0.0;
        double confidence = 1.0;

        for (TimingTransition transition : transitions) {
            Objects.requireNonNull(transition, "transition");
            if (transition == TimingTransition.IMMEDIATE) {
                continue;
            }
            if (transition.hardFeedback()) {
                hardBoundaries++;
            }

            TimingDistribution distribution = distribution(transition, nowNanos);
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

    public synchronized int pendingCount() {
        return pending.size();
    }

    private static double percentile(List<Double> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            throw new IllegalArgumentException("cannot take percentile of empty list");
        }
        if (sortedValues.size() == 1) {
            return sortedValues.getFirst();
        }
        double index = percentile * (sortedValues.size() - 1);
        int lower = (int) Math.floor(index);
        int upper = (int) Math.ceil(index);
        if (lower == upper) {
            return sortedValues.get(lower);
        }
        double fraction = index - lower;
        return sortedValues.get(lower)
            + (sortedValues.get(upper) - sortedValues.get(lower)) * fraction;
    }

    private record Sample(long durationNanos, long endNanos) {
    }
}
