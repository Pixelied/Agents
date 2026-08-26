package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;

import java.util.Objects;

public record TimingSnapshot(
    long clientTick,
    double rttMs,
    double jitterMs,
    TickWindow nextPacketProcessingWindow
) {
    private static final double SERVER_TICK_MS = 50d;

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

    /**
     * Conservative age of an inbound synchronized observation relative to current server state.
     * The center is one-way latency; jitter widens both sides and the latest side carries one
     * additional scheduling/tick-phase safety tick.
     */
    public TickWindow observationAgeWindow() {
        double centerMs = rttMs / 2d;
        long earliest = floorServerTicks(Math.max(0d, centerMs - jitterMs));
        long latest = saturatingAdd(ceilServerTicks(centerMs + jitterMs), 1L);
        return new TickWindow(earliest, Math.max(earliest, latest));
    }

    /**
     * Conservative server-to-client return time used when a vanilla optimistic container click can
     * succeed silently. Minecraft 26.1.2 sends corrections on disagreement but no ACK for an exact
     * client prediction, so silence is meaningful only after the correction path could have arrived.
     */
    public int serverCorrectionReturnTicks() {
        double latestReturnMs = rttMs / 2d + jitterMs;
        long ticks = ceilServerTicks(latestReturnMs) + 1L;
        return ticks >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ticks;
    }

    /** Latest client tick at which a correction for a click sent from this snapshot should arrive. */
    public long containerPredictionSettleTick() {
        return saturatingAdd(nextPacketProcessingWindow.latest(), serverCorrectionReturnTicks());
    }

    /**
     * Extra server ticks charged after TimingSnapshot's initial outbound packet window when a
     * container-routed item must wait for the correction window and then send a follow-up packet.
     */
    public int containerFollowupRouteTicks() {
        long outboundTicks = Math.max(0L, nextPacketProcessingWindow.latest() - clientTick);
        long total = saturatingAdd(outboundTicks, serverCorrectionReturnTicks());
        return total >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private TickWindow validateImpact(TickWindow impact) {
        Objects.requireNonNull(impact, "impact");
        if (impact.latest() < clientTick) {
            throw new IllegalArgumentException("impact window precedes timing snapshot");
        }
        return impact;
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
