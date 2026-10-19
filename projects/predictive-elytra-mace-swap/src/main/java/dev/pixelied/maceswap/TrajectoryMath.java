package dev.pixelied.maceswap;

/** Small pure-Java mechanics helpers, unit-tested independently of Minecraft. */
public final class TrajectoryMath {
    private TrajectoryMath() {}
    public static boolean maceEligible(double fallDistance, boolean gliding) {
        // Vanilla 26.3 MaceItem.SMASH_ATTACK_FALL_THRESHOLD is strictly greater than 1.5.
        return fallDistance > 1.5 && !gliding;
    }
    public static double fallDistanceAfterMovement(double previous, double actualVerticalDisplacement) {
        return actualVerticalDisplacement < 0 ? previous - actualVerticalDisplacement : previous;
    }
    public static double airVelocityY(double velocityY, double gravity, double drag) {
        // LivingEntity#travelInAir applies gravity and then vertical drag after movement.
        return (velocityY - gravity) * drag;
    }
    public static double dampedTargetMotion(double velocity, int ticks, double damping) {
        if (ticks <= 0) return 0;
        if (damping == 1) return velocity * ticks;
        return velocity * (1 - Math.pow(damping, ticks)) / (1 - damping);
    }
    /** True only when the predicted smash is close enough after glide exit to justify swapping now. */
    public static boolean withinSwapTimingWindow(int smashTick, int exitTick, int swapTimingTicks) {
        return smashTick > exitTick && smashTick <= exitTick + Math.max(1, swapTimingTicks);
    }
    public static int latencyTicks(int pingMs, int floorMs) {
        // Approximate one-way packet travel, plus one server scheduling tick.
        return Math.max(1, (int)Math.ceil(Math.max(pingMs, floorMs) / 100.0) + 1);
    }
}
