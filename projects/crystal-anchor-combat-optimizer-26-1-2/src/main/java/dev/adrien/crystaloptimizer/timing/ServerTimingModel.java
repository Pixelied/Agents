package dev.adrien.crystaloptimizer.timing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ServerTimingModel {
    private static final double MILLIS_PER_SERVER_TICK = 50.0;
    private static final double SAMPLE_STALE_AFTER_MILLIS = 5_000.0;

    private final int sampleCapacity;
    private final Map<Integer, Long> pendingSends = new HashMap<>();
    private final Deque<TimingSample> samples = new ArrayDeque<>();

    public ServerTimingModel(int sampleCapacity) {
        if (sampleCapacity <= 0) {
            throw new IllegalArgumentException("sampleCapacity must be positive");
        }
        this.sampleCapacity = sampleCapacity;
    }

    public synchronized void recordSend(int sequence, long nanos) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (nanos < 0L) {
            throw new IllegalArgumentException("nanos must be non-negative");
        }
        pendingSends.put(sequence, nanos);
    }

    public synchronized void recordAck(int sequence, long nanos) {
        if (sequence < 0 || nanos < 0L) {
            throw new IllegalArgumentException("sequence and nanos must be non-negative");
        }
        Long sent = pendingSends.remove(sequence);
        if (sent == null || nanos < sent) {
            return;
        }

        samples.addLast(new TimingSample(sequence, sent, nanos));
        while (samples.size() > sampleCapacity) {
            samples.removeFirst();
        }
    }

    public synchronized TimingEstimate estimateBurst(long nowNanos, int actionCount) {
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        if (actionCount <= 0) {
            throw new IllegalArgumentException("actionCount must be positive");
        }
        if (samples.isEmpty()) {
            return TimingEstimate.unknown();
        }

        List<Double> delays = samples.stream()
            .map(TimingSample::ackDelayMillis)
            .sorted()
            .toList();
        double median = median(delays);
        List<Double> deviations = delays.stream()
            .map(value -> Math.abs(value - median))
            .sorted()
            .toList();
        double jitter = median(deviations);

        TimingSample latest = samples.stream()
            .max(Comparator.comparingLong(TimingSample::ackNanos))
            .orElseThrow();
        double ageMillis = Math.max(0.0, (nowNanos - latest.ackNanos()) / 1_000_000.0);
        double freshness = Math.exp(-ageMillis / SAMPLE_STALE_AFTER_MILLIS);
        double sampleConfidence = Math.min(1.0, samples.size() / 4.0);
        double confidence = clamp01(sampleConfidence * freshness);

        double jitterPenalty = Math.exp(-jitter / MILLIS_PER_SERVER_TICK);
        double latencyPenalty = Math.exp(-Math.max(0.0, median - MILLIS_PER_SERVER_TICK) / 150.0);
        double burstPenalty = Math.exp(-0.08 * Math.max(0, actionCount - 1));
        double sameTickProbability = clamp01(confidence * jitterPenalty * latencyPenalty * burstPenalty);
        CompletionDistribution completion = CompletionDistribution.fromSameTickProbability(sameTickProbability);

        return new TimingEstimate(
            median,
            jitter,
            sameTickProbability,
            samples.size(),
            confidence,
            completion
        );
    }

    public synchronized List<TimingSample> samples() {
        return List.copyOf(samples);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        int middle = values.size() / 2;
        if ((values.size() & 1) == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) * 0.5;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
