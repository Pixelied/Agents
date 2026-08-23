package studio.pixelied.pearlcatch;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import studio.pixelied.pearlcatch.PearlCatchDebug.ShotTrace;
import studio.pixelied.pearlcatch.core.GeneralCatchSolver;
import studio.pixelied.pearlcatch.core.Rotation;
import studio.pixelied.pearlcatch.core.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Attempt state and entity bookkeeping only. No item input and no solver selection live here. */
final class CatchAttemptTracker {
    private CatchAttemptTracker() {}

    static WindCharge firstExistingWindHazard(
            ClientLevel level,
            Vec3d pearlStart,
            Vec3d pearlLaunchVelocity,
            int throughPearlTick
    ) {
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof WindCharge wind) || !wind.isAlive()) continue;
            if (GeneralCatchSolver.pathHitsExistingWind(
                    pearlStart, pearlLaunchVelocity, PearlCatchDebug.toCore(wind.position()),
                    PearlCatchDebug.toCore(wind.getDeltaMovement()), throughPearlTick)) {
                return wind;
            }
        }
        return null;
    }

    static boolean hasDebugAttempt(List<LegitPearlLaunch> launches, List<PendingCatch> pendingCatches, List<TrackingShot> activeShots) {
        for (LegitPearlLaunch launch : launches) if (launch.debug) return true;
        for (PendingCatch pending : pendingCatches) if (pending.debug) return true;
        for (TrackingShot shot : activeShots) if (shot.debug) return true;
        return false;
    }

    static boolean isPearlClaimed(int entityId, List<PendingCatch> pendingCatches, List<TrackingShot> activeShots) {
        if (entityId < 0) return false;
        for (PendingCatch pending : pendingCatches) if (pending.pearlId == entityId) return true;
        for (TrackingShot shot : activeShots) if (shot.pearlId == entityId) return true;
        return false;
    }

    static Set<Integer> claimedPearlIds(List<PendingCatch> pendingCatches, List<TrackingShot> activeShots) {
        Set<Integer> ids = new HashSet<>();
        for (PendingCatch pending : pendingCatches) if (pending.pearlId >= 0) ids.add(pending.pearlId);
        for (TrackingShot shot : activeShots) if (shot.pearlId >= 0) ids.add(shot.pearlId);
        return ids;
    }

    static Set<Integer> claimedWindIds(List<TrackingShot> activeShots) {
        Set<Integer> ids = new HashSet<>();
        for (TrackingShot shot : activeShots) if (shot.windId >= 0) ids.add(shot.windId);
        return ids;
    }

    static <T extends Entity> Set<Integer> entityIds(ClientLevel level, Class<T> type) {
        Set<Integer> ids = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) if (type.isInstance(e)) ids.add(e.getId());
        return ids;
    }

    static double approximateSegmentAabbGap(Vec3 from, Vec3 to, AABB box) {
        if (box.clip(from, to).isPresent()) return 0.0;
        double best = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 16; i++) {
            double t = i / 16.0;
            Vec3 p = from.lerp(to, t);
            double dx = Math.max(Math.max(box.minX - p.x, 0.0), p.x - box.maxX);
            double dy = Math.max(Math.max(box.minY - p.y, 0.0), p.y - box.maxY);
            double dz = Math.max(Math.max(box.minZ - p.z, 0.0), p.z - box.maxZ);
            best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return best;
    }

    static double angleDistance(Rotation a, Rotation b) {
        double y = Rotation.wrapYaw(b.yaw() - a.yaw());
        double p = b.pitch() - a.pitch();
        return Math.sqrt(y * y + p * p);
    }

    static final class LegitPearlLaunch {
        final long attemptId;
        final String label;
        final boolean debug;
        final long startClientTick;
        final Rotation target;
        final int previousSlot;
        final Set<Integer> existingPearls;
        final Set<Integer> existingWinds;
        final ShotTrace trace;
        int ageTicks;
        boolean waitingForPearl;
        long useRequestedClientTick = -1;
        boolean pearlOffhandSwapRequested;
        boolean pearlOffhandSwapped;
        int pearlSwapSlot = -1;
        Item pearlSwapOriginalSelectedItem;
        Rotation commandedPearlRotation;
        Vec3 launchEye;
        Vec3d launchInheritedMotion;
        List<Vec3> predictedPearl = List.of();
        GeneralCatchSolver.Plan latestPlan;
        boolean serverRotationNeedsRestore;

        LegitPearlLaunch(long attemptId, String label, boolean debug, long startClientTick, Rotation target,
                         int previousSlot, Set<Integer> existingPearls, Set<Integer> existingWinds, ShotTrace trace) {
            this.attemptId = attemptId;
            this.label = label;
            this.debug = debug;
            this.startClientTick = startClientTick;
            this.target = target;
            this.previousSlot = previousSlot;
            this.existingPearls = existingPearls;
            this.existingWinds = existingWinds;
            this.trace = trace;
        }
    }

    static final class LegitRestore {
        final long attemptId;
        final int previousSlot;
        int ownedSlot;
        final boolean restoreOffhand;
        final int swapSlot;
        final Item originalSelectedItem;
        boolean swapRequested;
        boolean offhandRestored;
        boolean slotRestoreRequested;

        LegitRestore(long attemptId, int previousSlot, int ownedSlot, boolean restoreOffhand,
                     int swapSlot, Item originalSelectedItem) {
            this.attemptId = attemptId;
            this.previousSlot = previousSlot;
            this.ownedSlot = ownedSlot;
            this.restoreOffhand = restoreOffhand;
            this.swapSlot = swapSlot;
            this.originalSelectedItem = originalSelectedItem;
        }
    }

    static final class PendingCatch {
        final String label;
        final boolean debug;
        final long startClientTick;
        final Rotation target;
        final Rotation pearlRotation;
        final int previousSlot;
        final Set<Integer> existingPearls;
        final Set<Integer> existingWinds;
        final Vec3 launchEye;
        final Vec3d launchInheritedMotion;
        final List<Vec3> predictedPearl;
        final ShotTrace trace;
        long attemptId;
        boolean legit;
        int ageTicks;
        int solveAttempts;
        int pearlId = -1;
        long pearlSeenClientTick = -1;
        boolean windUsed;
        boolean windUseRequested;
        long windUseRequestedClientTick = -1;
        int windId = -1;
        boolean pearlOffhandRestoreNeeded;
        boolean pearlRestoreSwapRequested;
        int pearlSwapSlot = -1;
        Item pearlSwapOriginalSelectedItem;
        boolean windOffhandSwapRequested;
        boolean windOffhandSwapped;
        int windSwapSlot = -1;
        Item windSwapOriginalSelectedItem;
        int executorOwnedSlot = -1;
        Rotation queuedWindRotation;
        Vec3d actualPearlLaunchVelocity;
        int actualPearlObservedEntityTick = -1;
        GeneralCatchSolver.Plan latestPlan;
        boolean serverRotationNeedsRestore;

        PendingCatch(String label, boolean debug, long startClientTick, Rotation target,
                     Rotation pearlRotation, int previousSlot,
                     Set<Integer> existingPearls, Set<Integer> existingWinds, Vec3 launchEye,
                     Vec3d launchInheritedMotion, List<Vec3> predictedPearl, ShotTrace trace) {
            this.label = label;
            this.debug = debug;
            this.startClientTick = startClientTick;
            this.target = target;
            this.pearlRotation = pearlRotation;
            this.previousSlot = previousSlot;
            this.existingPearls = existingPearls;
            this.existingWinds = existingWinds;
            this.launchEye = launchEye;
            this.launchInheritedMotion = launchInheritedMotion;
            this.predictedPearl = predictedPearl;
            this.trace = trace;
        }
    }

    static final class TrackingShot {
        final long attemptId;
        final String label;
        final boolean debug;
        final long startClientTick;
        final Rotation target;
        final GeneralCatchSolver.Plan plan;
        final String execution;
        final int previousSlot;
        final Set<Integer> existingPearls;
        final Set<Integer> existingWinds;
        final List<Vec3> predictedPearl;
        final List<Vec3> predictedWind;
        final List<Vec3> actualPearl = new ArrayList<>();
        final List<Vec3> actualWind = new ArrayList<>();
        final ShotTrace trace;
        final Vec3 startEye;
        int ageTicks;
        int pearlId = -1;
        int windId = -1;
        boolean pearlSeen;
        boolean windSeen;
        int pearlMissingTicks;
        int lastObservedPearlEntityTick = -1;
        Vec3 previousPearlForGap;
        Vec3 lastPearlPosition;
        double closestGap = Double.POSITIVE_INFINITY;
        double closestCenterGap = Double.POSITIVE_INFINITY;
        int closestClientTick = -1;
        Vec3 closestPearl;
        Vec3 closestWind;
        boolean clientInterpolatedClipHint;
        int firstClientClipHintTick = -1;
        Vec3 firstClientClipHintPoint;

        TrackingShot(long attemptId, String label, boolean debug, long startClientTick, Rotation target, GeneralCatchSolver.Plan plan,
                     String execution, int previousSlot, Set<Integer> existingPearls, Set<Integer> existingWinds,
                     List<Vec3> predictedPearl, List<Vec3> predictedWind, ShotTrace trace) {
            this.attemptId = attemptId;
            this.label = label;
            this.debug = debug;
            this.startClientTick = startClientTick;
            this.target = target;
            this.plan = plan;
            this.execution = execution;
            this.previousSlot = previousSlot;
            this.existingPearls = existingPearls;
            this.existingWinds = existingWinds;
            this.predictedPearl = predictedPearl;
            this.predictedWind = predictedWind;
            this.trace = trace;
            this.startEye = new Vec3(trace.playerStart.eye().x(), trace.playerStart.eye().y(), trace.playerStart.eye().z());
        }
    }
}
