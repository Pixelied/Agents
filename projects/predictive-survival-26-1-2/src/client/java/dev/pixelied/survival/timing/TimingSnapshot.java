package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;

import java.util.Objects;

public record TimingSnapshot(
    long clientTick,
    double rttMs,
    double jitterMs,
    TickWindow nextPacketProcessingWindow
) {
    public TimingSnapshot {
        if (clientTick < 0) throw new IllegalArgumentException("clientTick must be non-negative");
        if (!Double.isFinite(rttMs) || rttMs < 0d) throw new IllegalArgumentException("rttMs must be finite and non-negative");
        if (!Double.isFinite(jitterMs) || jitterMs < 0d) throw new IllegalArgumentException("jitterMs must be finite and non-negative");
        nextPacketProcessingWindow = Objects.requireNonNull(nextPacketProcessingWindow, "nextPacketProcessingWindow");
        if (nextPacketProcessingWindow.earliest() < clientTick) {
            throw new IllegalArgumentException("packet-processing window cannot precede snapshot tick");
        }
    }

    public boolean canCompleteBefore(long requiredServerTicks, TickWindow impact) {
        return deadline(requiredServerTicks).completesBefore(validateImpact(impact));
    }

    public Deadline deadline(long requiredServerTicks) {
        if (requiredServerTicks < 0) {
            throw new IllegalArgumentException("requiredServerTicks must be non-negative");
        }
        return new Deadline(new TickWindow(
            saturatingAdd(nextPacketProcessingWindow.earliest(), requiredServerTicks),
            saturatingAdd(nextPacketProcessingWindow.latest(), requiredServerTicks)
        ));
    }

    private TickWindow validateImpact(TickWindow impact) {
        Objects.requireNonNull(impact, "impact");
        if (impact.latest() < clientTick) {
            throw new IllegalArgumentException("impact window precedes timing snapshot");
        }
        return impact;
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
