package dev.pixelied.survival.core;

/**
 * Monotonic logical client tick clock keyed by the observed player tick. Multiple captures of the
 * same Minecraft tick therefore do not age deadlines/effects twice.
 */
final class CaptureTickClock {
    private long clientTick;
    private int lastObservedPlayerTick;
    private boolean hasObservation;

    boolean observe(int playerTick) {
        if (hasObservation && lastObservedPlayerTick == playerTick) return false;
        hasObservation = true;
        lastObservedPlayerTick = playerTick;
        if (clientTick < Long.MAX_VALUE) clientTick++;
        return true;
    }

    long clientTick() {
        return clientTick;
    }

    void resetObservation() {
        hasObservation = false;
    }
}
