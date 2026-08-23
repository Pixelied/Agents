package studio.pixelied.pearlcatch.core;

public final class VanillaProjectilePhysics {
    public static final double PROJECTILE_POWER = 1.5;
    public static final double PEARL_GRAVITY = 0.03;
    public static final double PEARL_AIR_INERTIA = 0.99;
    public static final double PEARL_SPAWN_Y_OFFSET = -0.1;
    public static final double WIND_WIDTH = 0.3125;
    public static final double WIND_HEIGHT = 0.3125;
    public static final double WIND_BOX_Y_OFFSET = -0.15;
    public static final double RANDOM_TRIANGLE_DEVIATION = 0.0172275;

    private VanillaProjectilePhysics() {}

    public static Vec3d lookDirection(Rotation rotation) {
        double yaw = Math.toRadians(rotation.yaw());
        double pitch = Math.toRadians(rotation.pitch());
        double cosPitch = Math.cos(pitch);
        return new Vec3d(-Math.sin(yaw) * cosPitch, -Math.sin(pitch), Math.cos(yaw) * cosPitch).normalize();
    }

    public static Rotation rotationForDirection(Vec3d direction) {
        Vec3d d = direction.normalize();
        double horizontal = Math.sqrt(d.x() * d.x() + d.z() * d.z());
        double yaw = Math.toDegrees(Math.atan2(-d.x(), d.z()));
        double pitch = Math.toDegrees(Math.atan2(-d.y(), horizontal));
        return new Rotation(yaw, pitch);
    }

    public static Vec3d inheritedMotion(Vec3d knownMovement, boolean onGround) {
        return new Vec3d(knownMovement.x(), onGround ? 0.0 : knownMovement.y(), knownMovement.z());
    }

    public static Vec3d nominalLaunchVelocity(Rotation rotation, Vec3d inheritedMotion) {
        return lookDirection(rotation).scale(PROJECTILE_POWER).add(inheritedMotion);
    }

    /**
     * Infers the movement inherited from the thrower by subtracting the commanded 1.5-speed
     * projectile component from an observed launch velocity. The remaining small error is vanilla
     * launch spread / network quantization.
     */
    public static Vec3d inferInheritedMotion(Rotation rotation, Vec3d observedLaunchVelocity) {
        if (rotation == null || observedLaunchVelocity == null) throw new IllegalArgumentException("rotation/observedLaunchVelocity");
        return observedLaunchVelocity.subtract(lookDirection(rotation).scale(PROJECTILE_POWER));
    }

    public static Vec3d perturbedLaunchVelocity(Rotation rotation, Vec3d inheritedMotion, Vec3d perturbation) {
        // Projectile#getMovementToShoot normalizes the base direction, adds triangle noise, then scales.
        // It deliberately does not renormalize after the random offsets.
        return lookDirection(rotation).add(perturbation).scale(PROJECTILE_POWER).add(inheritedMotion);
    }

    public static Vec3d pearlVelocityAfterTick(Vec3d currentVelocity) {
        return currentVelocity.add(0.0, -PEARL_GRAVITY, 0.0).scale(PEARL_AIR_INERTIA);
    }

    public static Vec3d pearlVelocityBeforeTick(Vec3d velocityAfterTick) {
        if (velocityAfterTick == null) throw new IllegalArgumentException("velocityAfterTick");
        return velocityAfterTick.scale(1.0 / PEARL_AIR_INERTIA).add(0.0, PEARL_GRAVITY, 0.0);
    }

    public static Vec3d pearlPositionAfterTicks(Vec3d start, Vec3d launchVelocity, int ticks) {
        if (start == null || launchVelocity == null) throw new IllegalArgumentException("start/launchVelocity");
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");
        if (ticks == 0) return start;
        double a = PEARL_AIR_INERTIA;
        double aPow = Math.pow(a, ticks);
        double velocitySum = a * (1.0 - aPow) / (1.0 - a);
        double gravityScale = a / (1.0 - a) * (ticks - velocitySum);
        Vec3d gravityDisplacement = new Vec3d(0.0, -PEARL_GRAVITY * gravityScale, 0.0);
        return start.add(launchVelocity.scale(velocitySum)).add(gravityDisplacement);
    }

    public static Vec3d requiredPearlLaunchVelocity(Vec3d start, Vec3d target, int ticks) {
        if (start == null || target == null) throw new IllegalArgumentException("start/target");
        if (ticks <= 0) throw new IllegalArgumentException("ticks must be > 0");
        double a = PEARL_AIR_INERTIA;
        double aPow = Math.pow(a, ticks);
        double velocitySum = a * (1.0 - aPow) / (1.0 - a);
        double gravityScale = a / (1.0 - a) * (ticks - velocitySum);
        Vec3d gravityDisplacement = new Vec3d(0.0, -PEARL_GRAVITY * gravityScale, 0.0);
        return target.subtract(start).subtract(gravityDisplacement).scale(1.0 / velocitySum);
    }

    public static Vec3d requiredWindLaunchVelocity(Vec3d start, Vec3d target, int completedTicks) {
        if (start == null || target == null) throw new IllegalArgumentException("start/target");
        if (completedTicks <= 0) throw new IllegalArgumentException("completedTicks must be > 0");
        return target.subtract(start).scale(1.0 / completedTicks);
    }

    public static Vec3d reconstructPearlLaunchVelocity(Vec3d observedVelocity, int completedTicks) {
        if (completedTicks < 0) throw new IllegalArgumentException("completedTicks must be >= 0");
        Vec3d velocity = observedVelocity;
        for (int i = 0; i < completedTicks; i++) {
            velocity = pearlVelocityBeforeTick(velocity);
        }
        return velocity;
    }

    public static double collisionMargin(int sourceTickCountBeforeCollision) {
        return Math.max(0.0, Math.min(0.3, (sourceTickCountBeforeCollision - 2) / 20.0));
    }

    /**
     * Margin that was used by the movement segment which has just completed. ThrowableProjectile calls
     * ProjectileUtil before Entity#tick increments tickCount, while END_CLIENT_TICK observes the incremented value.
     */
    public static double collisionMarginForCompletedSegment(int observedTickCountAfterTick) {
        return collisionMargin(observedTickCountAfterTick - 1);
    }

    public static Aabb3d windChargeBox(Vec3d position, double projectileMargin) {
        double halfWidth = WIND_WIDTH * 0.5;
        return new Aabb3d(
                position.x() - halfWidth,
                position.y() + WIND_BOX_Y_OFFSET,
                position.z() - halfWidth,
                position.x() + halfWidth,
                position.y() + WIND_BOX_Y_OFFSET + WIND_HEIGHT,
                position.z() + halfWidth
        ).inflate(projectileMargin);
    }
}
