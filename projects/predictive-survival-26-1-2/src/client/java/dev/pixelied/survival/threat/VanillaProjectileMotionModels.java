package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;

public final class VanillaProjectileMotionModels {
    private VanillaProjectileMotionModels() {
    }

    public static ProjectileMotionModel arrowLike(double drag, double gravity) {
        validateUnitDrag(drag);
        validateGravity(gravity);
        return current -> {
            Vec3Snapshot nextPosition = add(current.position(), current.velocity());
            Vec3Snapshot nextVelocity = scale(current.velocity(), drag);
            nextVelocity = new Vec3Snapshot(nextVelocity.x(), nextVelocity.y() - gravity, nextVelocity.z());
            return next(current, nextPosition, nextVelocity);
        };
    }

    public static ProjectileMotionModel throwable(double drag, double gravity) {
        validateUnitDrag(drag);
        validateGravity(gravity);
        return current -> {
            Vec3Snapshot accelerated = new Vec3Snapshot(
                current.velocity().x(),
                current.velocity().y() - gravity,
                current.velocity().z()
            );
            Vec3Snapshot nextVelocity = scale(accelerated, drag);
            return next(current, add(current.position(), nextVelocity), nextVelocity);
        };
    }

    public static ProjectileMotionModel llamaSpit(double drag, double gravity) {
        return arrowLike(drag, gravity);
    }

    public static ProjectileMotionModel hurtingProjectile(double inertia, double accelerationPower) {
        validateUnitDrag(inertia);
        if (!Double.isFinite(accelerationPower) || accelerationPower < 0d) {
            throw new IllegalArgumentException("accelerationPower must be finite and non-negative");
        }
        return current -> {
            Vec3Snapshot direction = normalize(current.velocity());
            Vec3Snapshot accelerated = add(current.velocity(), scale(direction, accelerationPower));
            Vec3Snapshot nextVelocity = scale(accelerated, inertia);
            return next(current, add(current.position(), nextVelocity), nextVelocity);
        };
    }

    public static ProjectileMotionModel constantVelocity() {
        return current -> next(current, add(current.position(), current.velocity()), current.velocity());
    }

    public static ProjectileMotionModel firework(boolean horizontalCollision) {
        return current -> {
            double horizontalMultiplier = horizontalCollision ? 1d : 1.15d;
            Vec3Snapshot nextVelocity = new Vec3Snapshot(
                current.velocity().x() * horizontalMultiplier,
                current.velocity().y() + 0.04d,
                current.velocity().z() * horizontalMultiplier
            );
            return next(current, add(current.position(), nextVelocity), nextVelocity);
        };
    }

    private static ProjectileStep next(ProjectileStep current, Vec3Snapshot position, Vec3Snapshot velocity) {
        if (current.tick() == Long.MAX_VALUE) throw new IllegalStateException("projectile tick overflow");
        return new ProjectileStep(position, velocity, current.tick() + 1L);
    }

    private static Vec3Snapshot add(Vec3Snapshot a, Vec3Snapshot b) {
        return new Vec3Snapshot(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static Vec3Snapshot scale(Vec3Snapshot value, double scale) {
        return new Vec3Snapshot(value.x() * scale, value.y() * scale, value.z() * scale);
    }

    private static Vec3Snapshot normalize(Vec3Snapshot value) {
        double length = length(value);
        if (length < 1.0E-12d) return new Vec3Snapshot(0d, 0d, 0d);
        return scale(value, 1d / length);
    }

    public static double length(Vec3Snapshot value) {
        return Math.sqrt(value.x() * value.x() + value.y() * value.y() + value.z() * value.z());
    }

    private static void validateUnitDrag(double value) {
        if (!Double.isFinite(value) || value < 0d || value > 1.5d) {
            throw new IllegalArgumentException("drag/inertia must be finite and in [0, 1.5]");
        }
    }

    private static void validateGravity(double value) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("gravity must be finite and non-negative");
        }
    }
}
