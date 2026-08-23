package dev.pixelied.survival;

/** Coalesces any number of relevant client packet updates into one optional extra analysis pass. */
public final class ThreatDirtyTracker {
    private boolean dirty;

    public void markDirty() {
        dirty = true;
    }

    public boolean consumeDirty() {
        boolean result = dirty;
        dirty = false;
        return result;
    }

    public void reset() {
        dirty = false;
    }
}
