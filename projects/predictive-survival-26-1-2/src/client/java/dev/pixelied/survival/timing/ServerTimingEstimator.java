package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;

import java.util.ArrayDeque;
import java.util.Deque;

public final class ServerTimingEstimator {
    private static final int SAMPLE_LIMIT = 20;
    private static final double DEFAULT_RTT_MS = 250d;
    private static final double DEFAULT_JITTER_MS = 100d;
    private static final double SERVER_TICK_MS = 50d;
    private static final long NO_LOGICAL_TICK = Long.MIN_VALUE;

    private final Deque<Double> rttSamplesMs = new ArrayDeque<>();
    private final Deque<Double> tickSamplesMs = new ArrayDeque<>();
    private long lastRttLogicalTick = NO_LOGICAL_TICK;

    public void observeRttMillis(int rttMillis) {
        validateRttMillis(rttMillis);
        addBounded(rttSamplesMs, (double) rttMillis);
        lastRttLogicalTick = NO_LOGICAL_TICK;
    }

    public void observeRttMillis(long logicalTick, int rttMillis) {
        if (logicalTick < 0) throw new IllegalArgumentException("logicalTick must be non-negative");
        validateRttMillis(rttMillis);

        if (lastRttLogicalTick == logicalTick && !rttSamplesMs.isEmpty()) {
            rttSamplesMs.removeLast();
            rttSamplesMs.addLast((double) rttMillis);
            return;
        }

        addBounded(rttSamplesMs, (double) rttMillis);
        lastRttLogicalTick = logicalTick;
    }

    public void observeClientTickNanos(long nanos) {
        if (nanos <= 0) throw new IllegalArgumentException("client tick duration must be positive");
        addBounded(tickSamplesMs, nanos / 1_000_000d);
    }

    public void reset() {
        rttSamplesMs.clear();
        tickSamplesMs.clear();
        lastRttLogicalTick = NO_LOGICAL_TICK;
    }

    public TimingSnapshot snapshot(long clientTick) {
        if (clientTick < 0) throw new IllegalArgumentException("clientTick must be non-negative");

        double rtt = rttSamplesMs.isEmpty() ? DEFAULT_RTT_MS : mean(rttSamplesMs);
        double jitter = rttSamplesMs.isEmpty() ? DEFAULT_JITTER_MS : maxAbsoluteDeviation(rttSamplesMs, rtt);
        double clientTickMs = tickSamplesMs.isEmpty() ? SERVER_TICK_MS : mean(tickSamplesMs);
        double clientSchedulingOverrunMs = Math.max(0d, clientTickMs - SERVER_TICK_MS);

        double oneWayCenterMs = rtt / 2d;
        double earliestMs = Math.max(0d, oneWayCenterMs - jitter);
        double latestMs = oneWayCenterMs + jitter + clientSchedulingOverrunMs;

        long earliestTicks = floorServerTicks(earliestMs);
        long latestTicks = ceilServerTicks(latestMs) + 1L;
        TickWindow processing = new TickWindow(
            saturatingAdd(clientTick, earliestTicks),
            saturatingAdd(clientTick, latestTicks)
        );
        return new TimingSnapshot(clientTick, rtt, jitter, processing);
    }

    private static void validateRttMillis(int rttMillis) {
        if (rttMillis < 0) throw new IllegalArgumentException("rttMillis must be non-negative");
    }

    private static void addBounded(Deque<Double> samples, double value) {
        if (samples.size() == SAMPLE_LIMIT) samples.removeFirst();
        samples.addLast(value);
    }

    private static double mean(Deque<Double> samples) {
        double sum = 0d;
        for (double sample : samples) sum += sample;
        return sum / samples.size();
    }

    private static double maxAbsoluteDeviation(Deque<Double> samples, double mean) {
        double max = 0d;
        for (double sample : samples) max = Math.max(max, Math.abs(sample - mean));
        return max;
    }

    private static long floorServerTicks(double millis) {
        return Math.max(0L, (long) Math.floor(millis / SERVER_TICK_MS));
    }

    private static long ceilServerTicks(double millis) {
        return Math.max(0L, (long) Math.ceil(millis / SERVER_TICK_MS));
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
