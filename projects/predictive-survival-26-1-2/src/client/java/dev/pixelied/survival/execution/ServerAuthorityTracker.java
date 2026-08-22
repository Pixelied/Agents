package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;

import java.util.Objects;

public final class ServerAuthorityTracker {
    private static final double EPSILON = 1.0E-9d;

    private int confirmedSelectedSlot;
    private Integer pendingSelectedSlot;
    private long pendingSelectedConfirmTick = Long.MAX_VALUE;
    private SurvivalAction.Hand pendingUseHand;
    private long pendingUseServerStartTick = Long.MAX_VALUE;

    public ServerAuthorityTracker(int initialSelectedSlot) {
        validateHotbar(initialSelectedSlot);
        this.confirmedSelectedSlot = initialSelectedSlot;
    }

    public void sentHotbarSelection(int targetSlot, TimingSnapshot timing) {
        validateHotbar(targetSlot);
        Objects.requireNonNull(timing, "timing");
        pendingSelectedSlot = targetSlot;
        pendingSelectedConfirmTick = timing.nextPacketProcessingWindow().latest();
    }

    public void observeUntrackedLocalSelection(int localSelectedSlot, TimingSnapshot timing) {
        validateHotbar(localSelectedSlot);
        Objects.requireNonNull(timing, "timing");
        if (localSelectedSlot == confirmedSelectedSlot || pendingSelectedSlot != null) return;
        pendingSelectedSlot = localSelectedSlot;
        pendingSelectedConfirmTick = timing.nextPacketProcessingWindow().latest();
    }

    public int confirmedSelectedSlot(int localSelectedSlot, long currentTick) {
        validateHotbar(localSelectedSlot);
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");

        if (pendingSelectedSlot == null) return confirmedSelectedSlot;
        if (currentTick < pendingSelectedConfirmTick) return confirmedSelectedSlot;

        int target = pendingSelectedSlot;
        pendingSelectedSlot = null;
        pendingSelectedConfirmTick = Long.MAX_VALUE;
        if (localSelectedSlot == target) confirmedSelectedSlot = target;
        return confirmedSelectedSlot;
    }

    public void sentUseItem(SurvivalAction.Hand hand, TimingSnapshot timing) {
        pendingUseHand = Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(timing, "timing");
        pendingUseServerStartTick = timing.nextPacketProcessingWindow().latest();
    }

    public boolean confirmedUsingItem(
        boolean localUsing,
        SurvivalAction.Hand localHand,
        long currentTick
    ) {
        if (currentTick < 0L) throw new IllegalArgumentException("currentTick must be non-negative");
        if (pendingUseHand == null) return false;

        // Once the conservative server-start tick has passed, a stopped or different local use
        // proves that this tracked use session is over. Do not leave the old start tick around for
        // a later use of the same hand or it would appear to have hundreds of warm-up ticks.
        if (currentTick >= pendingUseServerStartTick
            && (!localUsing || localHand == null || localHand != pendingUseHand)) {
            clearUseSession();
            return false;
        }

        return localUsing && localHand == pendingUseHand && currentTick >= pendingUseServerStartTick;
    }

    public int confirmedUseTicks(
        boolean localUsing,
        SurvivalAction.Hand localHand,
        long currentTick
    ) {
        if (!confirmedUsingItem(localUsing, localHand, currentTick)) return 0;
        long elapsed = currentTick - pendingUseServerStartTick;
        return elapsed >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) elapsed;
    }

    public void reset() {
        pendingSelectedSlot = null;
        pendingSelectedConfirmTick = Long.MAX_VALUE;
        clearUseSession();
    }

    private void clearUseSession() {
        pendingUseHand = null;
        pendingUseServerStartTick = Long.MAX_VALUE;
    }

    public static boolean withinHorizontalBlockAngle(
        Vec3Snapshot playerPosition,
        float headYawDegrees,
        Vec3Snapshot sourcePosition,
        float blockAngleDegrees
    ) {
        Objects.requireNonNull(playerPosition, "playerPosition");
        Objects.requireNonNull(sourcePosition, "sourcePosition");
        if (!Float.isFinite(headYawDegrees) || !Float.isFinite(blockAngleDegrees)
            || blockAngleDegrees < 0f || blockAngleDegrees > 180f) {
            return false;
        }

        double dx = sourcePosition.x() - playerPosition.x();
        double dz = sourcePosition.z() - playerPosition.z();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length <= EPSILON) return false;
        dx /= length;
        dz /= length;

        double yaw = Math.toRadians(headYawDegrees);
        double viewX = -Math.sin(yaw);
        double viewZ = Math.cos(yaw);
        double dot = Math.max(-1d, Math.min(1d, viewX * dx + viewZ * dz));
        double minimumDot = Math.cos(Math.toRadians(blockAngleDegrees));
        return dot + EPSILON >= minimumDot;
    }

    private static void validateHotbar(int slot) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("hotbar slot must be in [0, 8]");
    }
}
