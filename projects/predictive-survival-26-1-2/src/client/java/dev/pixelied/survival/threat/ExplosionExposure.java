package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;

public final class ExplosionExposure {
    public float seenPercent(AabbSnapshot target, Vec3Snapshot center, OcclusionView world) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(center, "center");
        Objects.requireNonNull(world, "world");

        double xs = 1.0 / ((target.maxX() - target.minX()) * 2.0 + 1.0);
        double ys = 1.0 / ((target.maxY() - target.minY()) * 2.0 + 1.0);
        double zs = 1.0 / ((target.maxZ() - target.minZ()) * 2.0 + 1.0);
        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) return 0f;

        int visible = 0;
        int count = 0;
        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    Vec3Snapshot from = new Vec3Snapshot(
                        lerp(xx, target.minX(), target.maxX()) + xOffset,
                        lerp(yy, target.minY(), target.maxY()),
                        lerp(zz, target.minZ(), target.maxZ()) + zOffset
                    );
                    if (!world.blocksExplosionRay(from, center)) visible++;
                    count++;
                }
            }
        }
        return count == 0 ? 0f : (float) visible / count;
    }

    public float rawEntityDamage(float radius, double distance, float exposure) {
        if (!Float.isFinite(radius) || radius < 0f) throw new IllegalArgumentException("radius must be finite and non-negative");
        if (!Double.isFinite(distance) || distance < 0d) throw new IllegalArgumentException("distance must be finite and non-negative");
        if (!Float.isFinite(exposure) || exposure < 0f || exposure > 1f) throw new IllegalArgumentException("exposure must be in [0, 1]");
        if (radius < 1.0E-5f) return 0f;

        float doubleRadius = radius * 2f;
        double normalizedDistance = distance / doubleRadius;
        if (normalizedDistance > 1.0) return 0f;
        double power = (1.0 - normalizedDistance) * exposure;
        return (float) (((power * power + power) / 2.0) * 7.0 * doubleRadius + 1.0);
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }
}
