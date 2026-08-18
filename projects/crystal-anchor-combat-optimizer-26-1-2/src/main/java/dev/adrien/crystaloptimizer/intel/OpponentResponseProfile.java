package dev.adrien.crystaloptimizer.intel;

import java.util.List;

public record OpponentResponseProfile(List<Long> refillLatencyNanos) {
    public OpponentResponseProfile {
        refillLatencyNanos = List.copyOf(refillLatencyNanos);
        for (long latency : refillLatencyNanos) {
            if (latency < 0L) {
                throw new IllegalArgumentException("refill latency samples must be non-negative");
            }
        }
    }

    public static OpponentResponseProfile empty() {
        return new OpponentResponseProfile(List.of());
    }

    public boolean hasRefillSamples() {
        return !refillLatencyNanos.isEmpty();
    }

    public long medianRefillLatencyNanos() {
        if (refillLatencyNanos.isEmpty()) {
            throw new IllegalStateException("no refill latency samples are available");
        }
        List<Long> sorted = refillLatencyNanos.stream().sorted().toList();
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) {
            return sorted.get(middle);
        }
        return (sorted.get(middle - 1) + sorted.get(middle)) / 2L;
    }
}
