package dev.adrien.crystaloptimizer.timing;

public record TimingSample(int sequence, long sentNanos, long ackNanos) {
    public TimingSample {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        if (sentNanos < 0L || ackNanos < sentNanos) {
            throw new IllegalArgumentException("ack must not precede send");
        }
    }

    public double ackDelayMillis() {
        return (ackNanos - sentNanos) / 1_000_000.0;
    }
}
