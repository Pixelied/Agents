package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.TickWindow;

import java.util.Objects;

/** Converts client-observed synchronized countdowns into conservative current-server deadlines. */
public final class ExplosionTiming {
    private ExplosionTiming() {
    }

    public static TickWindow ageCountdown(long observedTicksRemaining, TickWindow observationAge) {
        if (observedTicksRemaining < 0) {
            throw new IllegalArgumentException("observedTicksRemaining must be non-negative");
        }
        Objects.requireNonNull(observationAge, "observationAge");
        long earliest = saturatingSubtractToZero(observedTicksRemaining, observationAge.latest());
        long latest = saturatingSubtractToZero(observedTicksRemaining, observationAge.earliest());
        return new TickWindow(earliest, Math.max(earliest, latest));
    }

    private static long saturatingSubtractToZero(long value, long decrement) {
        return decrement >= value ? 0L : value - decrement;
    }
}
