package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public final class WorldBorderPredictor extends PeriodicDamagePredictor {
    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        double currentSignedDistance = doubleState(
            context,
            "border_distance_plus_safe_zone",
            Double.POSITIVE_INFINITY
        );
        double damagePerBlock = Math.max(0d, doubleState(context, "border_damage_per_block", 0.2d));
        BorderGeometry geometry = BorderGeometry.capture(context);

        if (!(currentSignedDistance < 0d) && geometry == null) return List.of();

        List<ThreatEvent> events = new ArrayList<>();
        long horizon = horizon(context);
        for (long tick = 1; tick <= horizon; tick++) {
            double signedDistance = currentSignedDistance;
            if (geometry != null) {
                // World-border damage runs during the entity base tick while both player movement
                // and a lerping border can change between observations. Use the more dangerous of
                // the projected pre/post-movement positions and pre/post-border-tick extents. This
                // never credits observed inward movement or border expansion as guaranteed safety.
                signedDistance = Math.min(
                    signedDistance,
                    geometry.worstSignedDistance(context.player().position(), context.player().velocity(), tick)
                );
            }
            if (!(signedDistance < 0d)) continue;

            double rawDouble = Math.max(1d, Math.floor(-signedDistance * damagePerBlock));
            float raw = !Double.isFinite(rawDouble) || rawDouble >= Float.MAX_VALUE
                ? Float.MAX_VALUE
                : (float) rawDouble;
            events.add(event(
                "env:outside_border:" + tick,
                tick,
                raw,
                "minecraft:outside_border",
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                0f,
                Confidence.POTENTIAL
            ));
        }
        return List.copyOf(events);
    }

    private record BorderGeometry(
        double safeZone,
        double staticMinX,
        double staticMaxX,
        double staticMinZ,
        double staticMaxZ,
        double centerX,
        double centerZ,
        double currentSize,
        long lerpTicks,
        double lerpTargetSize,
        double absoluteMaxSize,
        boolean movingGeometryAvailable
    ) {
        static BorderGeometry capture(PredictionContext context) {
            double minX = doubleState(context, "border_min_x", Double.NaN);
            double maxX = doubleState(context, "border_max_x", Double.NaN);
            double minZ = doubleState(context, "border_min_z", Double.NaN);
            double maxZ = doubleState(context, "border_max_z", Double.NaN);
            if (!finiteBounds(minX, maxX, minZ, maxZ)) return null;

            double centerX = doubleState(context, "border_center_x", Double.NaN);
            double centerZ = doubleState(context, "border_center_z", Double.NaN);
            double size = doubleState(context, "border_size", Double.NaN);
            double target = doubleState(context, "border_lerp_target_size", Double.NaN);
            double absoluteMax = doubleState(context, "border_absolute_max_size", Double.NaN);
            long lerpTicks = Math.max(0L, longState(context, "border_lerp_ticks", 0L));
            boolean moving = Double.isFinite(centerX)
                && Double.isFinite(centerZ)
                && Double.isFinite(size) && size >= 0d
                && Double.isFinite(target) && target >= 0d
                && Double.isFinite(absoluteMax) && absoluteMax > 0d;

            return new BorderGeometry(
                Math.max(0d, doubleState(context, "border_safe_zone", 0d)),
                minX, maxX, minZ, maxZ,
                centerX, centerZ, size, lerpTicks, target, absoluteMax, moving
            );
        }

        double worstSignedDistance(Vec3Snapshot position, Vec3Snapshot velocity, long tick) {
            double beforeT = Math.max(0d, tick - 1d);
            double afterT = Math.max(0d, tick);
            double beforeX = position.x() + velocity.x() * beforeT;
            double beforeZ = position.z() + velocity.z() * beforeT;
            double afterX = position.x() + velocity.x() * afterT;
            double afterZ = position.z() + velocity.z() * afterT;

            BorderBounds beforeBounds = boundsAt(beforeT);
            BorderBounds afterBounds = boundsAt(afterT);
            double worst = signedDistance(beforeX, beforeZ, beforeBounds) + safeZone;
            worst = Math.min(worst, signedDistance(afterX, afterZ, beforeBounds) + safeZone);
            worst = Math.min(worst, signedDistance(beforeX, beforeZ, afterBounds) + safeZone);
            return Math.min(worst, signedDistance(afterX, afterZ, afterBounds) + safeZone);
        }

        private BorderBounds boundsAt(double elapsedTicks) {
            if (!movingGeometryAvailable || lerpTicks <= 0L) {
                return new BorderBounds(staticMinX, staticMaxX, staticMinZ, staticMaxZ);
            }

            double fraction = Math.min(1d, Math.max(0d, elapsedTicks / (double) lerpTicks));
            double size = currentSize + (lerpTargetSize - currentSize) * fraction;
            double half = Math.max(0d, size) * 0.5d;
            return new BorderBounds(
                clamp(centerX - half, -absoluteMaxSize, absoluteMaxSize),
                clamp(centerX + half, -absoluteMaxSize, absoluteMaxSize),
                clamp(centerZ - half, -absoluteMaxSize, absoluteMaxSize),
                clamp(centerZ + half, -absoluteMaxSize, absoluteMaxSize)
            );
        }

        private static boolean finiteBounds(double minX, double maxX, double minZ, double maxZ) {
            return Double.isFinite(minX) && Double.isFinite(maxX)
                && Double.isFinite(minZ) && Double.isFinite(maxZ)
                && maxX >= minX && maxZ >= minZ;
        }

        private static double signedDistance(double x, double z, BorderBounds bounds) {
            double min = Math.min(x - bounds.minX(), bounds.maxX() - x);
            min = Math.min(min, z - bounds.minZ());
            return Math.min(min, bounds.maxZ() - z);
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    private record BorderBounds(double minX, double maxX, double minZ, double maxZ) {
    }
}
