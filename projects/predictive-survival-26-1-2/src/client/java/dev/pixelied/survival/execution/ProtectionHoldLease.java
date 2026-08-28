package dev.pixelied.survival.execution;

import dev.pixelied.survival.timing.TimingSnapshot;

import java.util.Objects;

/**
 * Temporal safety lease that prevents automatic restoration until lethal evidence has remained
 * absent through the client-observation and packet/correction uncertainty that could make danger
 * flicker out for one local frame.
 *
 * <p>The lease deliberately derives its release bound from {@link TimingSnapshot}; it does not use
 * a fixed grace constant. A new danger requirement, an unsafe/ambiguous safe observation, or a new
 * death-protection pop generation invalidates the previous continuous-safe interval.</p>
 */
public final class ProtectionHoldLease {
    private boolean required;
    private long safeSinceServerTick = -1L;
    private long releaseNotBeforeServerTick = Long.MAX_VALUE;
    private long popGeneration = -1L;

    public void require(ProtectionRequirement requirement, TimingSnapshot timing, long popGeneration) {
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(timing, "timing");
        validateGeneration(popGeneration);
        if (!requirement.required()) return;

        required = true;
        this.popGeneration = popGeneration;
        safeSinceServerTick = -1L;
        releaseNotBeforeServerTick = Long.MAX_VALUE;
    }

    public void observeSafe(SafeEvidence evidence, TimingSnapshot timing, long popGeneration) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(timing, "timing");
        validateGeneration(popGeneration);
        if (!required) return;

        if (this.popGeneration != popGeneration) {
            this.popGeneration = popGeneration;
            safeSinceServerTick = -1L;
            releaseNotBeforeServerTick = Long.MAX_VALUE;
        }

        if (!evidence.safeForRelease()) {
            safeSinceServerTick = -1L;
            releaseNotBeforeServerTick = Long.MAX_VALUE;
            return;
        }

        if (safeSinceServerTick < 0L) {
            safeSinceServerTick = timing.clientTick();
            releaseNotBeforeServerTick = saturatingAdd(safeSinceServerTick, uncertaintyTicks(timing));
            return;
        }

        // Preserve a continuous-safe start rather than creating a sliding grace period. If fresh
        // timing evidence widens the uncertainty, extend from that same start tick conservatively.
        releaseNotBeforeServerTick = Math.max(
            releaseNotBeforeServerTick,
            saturatingAdd(safeSinceServerTick, uncertaintyTicks(timing))
        );
    }

    public boolean blocksRestoration(long currentServerTick) {
        if (currentServerTick < 0L) throw new IllegalArgumentException("currentServerTick must be non-negative");
        return required && currentServerTick < releaseNotBeforeServerTick;
    }

    public long releaseNotBeforeServerTick() {
        return required ? releaseNotBeforeServerTick : -1L;
    }

    public void invalidate() {
        required = false;
        safeSinceServerTick = -1L;
        releaseNotBeforeServerTick = Long.MAX_VALUE;
        popGeneration = -1L;
    }

    private static long uncertaintyTicks(TimingSnapshot timing) {
        long observation = timing.observationAgeWindow().latest();
        long outbound = Math.max(0L, timing.nextPacketProcessingWindow().latest() - timing.clientTick());
        long correction = timing.serverCorrectionReturnTicks();
        return saturatingAdd(saturatingAdd(observation, outbound), correction);
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }

    private static void validateGeneration(long popGeneration) {
        if (popGeneration < 0L) throw new IllegalArgumentException("popGeneration must be non-negative");
    }

    public record ProtectionRequirement(
        boolean lethalActualThreat,
        boolean lethalOpportunity,
        boolean relevantObservationOverflow,
        boolean rescueOrRestorePending,
        boolean unprocessedPop
    ) {
        public boolean required() {
            return lethalActualThreat
                || lethalOpportunity
                || relevantObservationOverflow
                || rescueOrRestorePending
                || unprocessedPop;
        }

        public static ProtectionRequirement lethalThreat() {
            return new ProtectionRequirement(true, false, false, false, false);
        }

        public static ProtectionRequirement lethalOpportunity() {
            return new ProtectionRequirement(false, true, false, false, false);
        }

        public static ProtectionRequirement rescuePending() {
            return new ProtectionRequirement(false, false, false, true, false);
        }
    }

    public record SafeEvidence(
        boolean noLethalActualThreat,
        boolean noLethalOpportunity,
        boolean noRelevantObservationOverflow,
        boolean noRescueOrRestorePending,
        boolean noUnprocessedPop,
        boolean threatDisappearanceSupported
    ) {
        public boolean safeForRelease() {
            return noLethalActualThreat
                && noLethalOpportunity
                && noRelevantObservationOverflow
                && noRescueOrRestorePending
                && noUnprocessedPop
                && threatDisappearanceSupported;
        }

        public static SafeEvidence clean() {
            return new SafeEvidence(true, true, true, true, true, true);
        }
    }
}
