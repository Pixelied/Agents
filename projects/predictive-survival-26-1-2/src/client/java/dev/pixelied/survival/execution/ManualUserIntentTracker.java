package dev.pixelied.survival.execution;

import java.util.OptionalInt;

/** Monotonic observation of explicit user hotbar intent, independent of server packet completion. */
public final class ManualUserIntentTracker {
    private static final ManualUserIntentTracker GLOBAL = new ManualUserIntentTracker();

    private long generation;
    private int latestHotbarIndex = -1;

    public static ManualUserIntentTracker global() {
        return GLOBAL;
    }

    public synchronized long observeHotbarSelection(int hotbarIndex) {
        if (hotbarIndex < 0 || hotbarIndex > 8) {
            throw new IllegalArgumentException("hotbarIndex must be in [0, 8]");
        }
        generation = generation == Long.MAX_VALUE ? Long.MAX_VALUE : generation + 1L;
        latestHotbarIndex = hotbarIndex;
        return generation;
    }

    public synchronized long generation() {
        return generation;
    }

    public synchronized OptionalInt latestHotbarIndex() {
        return latestHotbarIndex < 0 ? OptionalInt.empty() : OptionalInt.of(latestHotbarIndex);
    }
}
