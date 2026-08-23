package studio.pixelied.pearlcatch.core;

/**
 * Mirrors the position-delta part of LocalPlayer#sendPosition closely enough to predict the
 * ServerPlayer#lastKnownClientMovement value used by Projectile#shootFromRotation.
 *
 * The estimator deliberately tracks packet-space displacement, not LocalPlayer deltaMovement.
 * During elytra flight those values can differ materially.
 */
public final class ServerKnownMovementEstimator {
    private static final double MOVE_EPSILON_SQR = 2.0E-4 * 2.0E-4;

    private Vec3d tickStart;
    private Vec3d lastSentPosition;
    private Vec3d currentKnownMovement = Vec3d.ZERO;
    private Vec3d currentTickDisplacement = Vec3d.ZERO;
    private int positionReminder;
    private boolean initialized;

    public void reset() {
        tickStart = null;
        lastSentPosition = null;
        currentKnownMovement = Vec3d.ZERO;
        currentTickDisplacement = Vec3d.ZERO;
        positionReminder = 0;
        initialized = false;
    }

    public void beginTick(Vec3d position) {
        if (position == null) throw new IllegalArgumentException("position");
        if (!initialized) {
            lastSentPosition = position;
            initialized = true;
        }
        tickStart = position;
    }

    public void endTick(Vec3d position) {
        if (position == null) throw new IllegalArgumentException("position");
        if (!initialized) {
            lastSentPosition = position;
            initialized = true;
            currentKnownMovement = Vec3d.ZERO;
            currentTickDisplacement = Vec3d.ZERO;
            tickStart = null;
            return;
        }

        currentTickDisplacement = tickStart == null ? Vec3d.ZERO : position.subtract(tickStart);
        positionReminder++;
        Vec3d sinceLastSent = position.subtract(lastSentPosition);
        boolean sendsPosition = sinceLastSent.lengthSquared() > MOVE_EPSILON_SQR || positionReminder >= 20;
        if (sendsPosition) {
            currentKnownMovement = sinceLastSent;
            lastSentPosition = position;
            positionReminder = 0;
        } else {
            // ServerGamePacketListenerImpl#handleClientTickEnd sets known movement to zero if no movement packet arrived.
            currentKnownMovement = Vec3d.ZERO;
        }
        tickStart = null;
    }

    /**
     * Best estimate at an arbitrary point during the client tick. If the end-of-tick sample has
     * already been captured, this is exactly currentKnownMovement().
     */
    public Vec3d estimateAtPosition(Vec3d position) {
        if (position == null) throw new IllegalArgumentException("position");
        if (!initialized) return Vec3d.ZERO;
        if (tickStart == null) return currentKnownMovement;

        Vec3d sinceLastSent = position.subtract(lastSentPosition);
        boolean wouldSendPosition = sinceLastSent.lengthSquared() > MOVE_EPSILON_SQR || positionReminder + 1 >= 20;
        return wouldSendPosition ? sinceLastSent : Vec3d.ZERO;
    }

    public Vec3d currentKnownMovement() {
        return currentKnownMovement;
    }

    public Vec3d currentTickDisplacement() {
        return currentTickDisplacement;
    }
}
