package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Replays the decision-critical 26.1.2 Elytra movement/collision ordering used by
 * LivingEntity#travelFallFlying. The solver deliberately reports only positive
 * horizontal-collision damage; a vertical landing is not fly_into_wall damage.
 */
public final class ElytraFlightCollisionSolver {
    private static final double COLLISION_EPSILON = 1.0E-7d;
    private static final double DEFAULT_GRAVITY = 0.08d;
    private static final double HORIZONTAL_DRAG = 0.99d;
    private static final double VERTICAL_DRAG = 0.98d;

    public Optional<CollisionPrediction> solve(PredictionContext context) {
        Objects.requireNonNull(context, "context");
        PlayerSnapshot player = context.player();
        if (!Boolean.parseBoolean(value(player.state("fall_flying"), "false"))) {
            return Optional.empty();
        }

        List<AabbSnapshot> colliders = collisionBoxes(context.world().blocks());
        if (colliders.isEmpty()) return Optional.empty();

        Vec3Snapshot velocity = player.velocity();
        AabbSnapshot box = player.boundingBox();
        LookState look = lookState(player, velocity);

        for (long tick = 1L; tick <= context.limits().maxProjectileHorizonTicks(); tick++) {
            double preCollisionHorizontalSpeed = horizontalLength(velocity);
            double gravity = effectiveGravity(player, velocity, tick);
            Vec3Snapshot requestedMovement = updateFallFlyingMovement(velocity, look, gravity);
            Vec3Snapshot resolvedMovement = collide(requestedMovement, box, colliders);

            boolean xCollision = differs(requestedMovement.x(), resolvedMovement.x());
            boolean yCollision = differs(requestedMovement.y(), resolvedMovement.y());
            boolean zCollision = differs(requestedMovement.z(), resolvedMovement.z());
            boolean horizontalCollision = xCollision || zCollision;

            Vec3Snapshot postCollisionVelocity = new Vec3Snapshot(
                xCollision ? 0d : requestedMovement.x(),
                yCollision ? 0d : requestedMovement.y(),
                zCollision ? 0d : requestedMovement.z()
            );
            double postCollisionHorizontalSpeed = horizontalLength(postCollisionVelocity);

            if (horizontalCollision) {
                float raw = rawFlyIntoWallDamage(preCollisionHorizontalSpeed, postCollisionHorizontalSpeed);
                if (raw > 0f) {
                    return Optional.of(new CollisionPrediction(
                        tick,
                        preCollisionHorizontalSpeed,
                        postCollisionHorizontalSpeed,
                        DamageRange.exact(raw),
                        move(box, resolvedMovement),
                        requestedMovement,
                        resolvedMovement,
                        Confidence.POTENTIAL
                    ));
                }
            }

            box = move(box, resolvedMovement);
            velocity = postCollisionVelocity;

            // A downward Y collision makes the entity on-ground for the next server tick.
            // 26.1.2 canGlide() then clears fall-flying before another flight travel step.
            if (yCollision && requestedMovement.y() < 0d) break;
        }
        return Optional.empty();
    }

    static float rawFlyIntoWallDamage(double preCollisionHorizontalSpeed, double postCollisionHorizontalSpeed) {
        if (!Double.isFinite(preCollisionHorizontalSpeed) || !Double.isFinite(postCollisionHorizontalSpeed)
            || preCollisionHorizontalSpeed < 0d || postCollisionHorizontalSpeed < 0d) {
            return Float.MAX_VALUE;
        }
        double raw = (preCollisionHorizontalSpeed - postCollisionHorizontalSpeed) * 10d - 3d;
        if (raw <= 0d) return 0f;
        if (!Double.isFinite(raw) || raw >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float) raw;
    }

    private static Vec3Snapshot updateFallFlyingMovement(
        Vec3Snapshot movement,
        LookState look,
        double gravity
    ) {
        double lookHorLength = Math.sqrt(look.vector().x() * look.vector().x() + look.vector().z() * look.vector().z());
        double moveHorLength = horizontalLength(movement);
        double leanAngle = Math.toRadians(look.pitchDegrees());
        double liftForce = square(Math.cos(leanAngle));

        Vec3Snapshot result = add(movement, 0d, gravity * (-1d + liftForce * 0.75d), 0d);
        if (result.y() < 0d && lookHorLength > 0d) {
            double convert = result.y() * -0.1d * liftForce;
            result = add(
                result,
                look.vector().x() * convert / lookHorLength,
                convert,
                look.vector().z() * convert / lookHorLength
            );
        }
        if (leanAngle < 0d && lookHorLength > 0d) {
            double convert = moveHorLength * -Math.sin(leanAngle) * 0.04d;
            result = add(
                result,
                -look.vector().x() * convert / lookHorLength,
                convert * 3.2d,
                -look.vector().z() * convert / lookHorLength
            );
        }
        if (lookHorLength > 0d) {
            result = add(
                result,
                (look.vector().x() / lookHorLength * moveHorLength - result.x()) * 0.1d,
                0d,
                (look.vector().z() / lookHorLength * moveHorLength - result.z()) * 0.1d
            );
        }
        return new Vec3Snapshot(
            result.x() * HORIZONTAL_DRAG,
            result.y() * VERTICAL_DRAG,
            result.z() * HORIZONTAL_DRAG
        );
    }

    private static Vec3Snapshot collide(
        Vec3Snapshot movement,
        AabbSnapshot box,
        List<AabbSnapshot> colliders
    ) {
        double x = 0d;
        double y = 0d;
        double z = 0d;

        y = clipAxis(Axis.Y, box, x, y, z, colliders, movement.y());
        if (Math.abs(movement.x()) < Math.abs(movement.z())) {
            z = clipAxis(Axis.Z, box, x, y, z, colliders, movement.z());
            x = clipAxis(Axis.X, box, x, y, z, colliders, movement.x());
        } else {
            x = clipAxis(Axis.X, box, x, y, z, colliders, movement.x());
            z = clipAxis(Axis.Z, box, x, y, z, colliders, movement.z());
        }
        return new Vec3Snapshot(x, y, z);
    }

    private static double clipAxis(
        Axis axis,
        AabbSnapshot original,
        double resolvedX,
        double resolvedY,
        double resolvedZ,
        List<AabbSnapshot> colliders,
        double requested
    ) {
        if (Math.abs(requested) < COLLISION_EPSILON) return 0d;
        AabbSnapshot moving = move(original, new Vec3Snapshot(resolvedX, resolvedY, resolvedZ));
        double allowed = requested;
        for (AabbSnapshot collider : colliders) {
            allowed = switch (axis) {
                case X -> clipX(moving, collider, allowed);
                case Y -> clipY(moving, collider, allowed);
                case Z -> clipZ(moving, collider, allowed);
            };
            if (Math.abs(allowed) < COLLISION_EPSILON) return 0d;
        }
        return allowed;
    }

    private static double clipX(AabbSnapshot moving, AabbSnapshot obstacle, double distance) {
        if (!overlaps(moving.minY(), moving.maxY(), obstacle.minY(), obstacle.maxY())
            || !overlaps(moving.minZ(), moving.maxZ(), obstacle.minZ(), obstacle.maxZ())) {
            return distance;
        }
        if (distance > 0d && moving.maxX() <= obstacle.minX() + COLLISION_EPSILON) {
            return Math.min(distance, Math.max(0d, obstacle.minX() - moving.maxX()));
        }
        if (distance < 0d && moving.minX() >= obstacle.maxX() - COLLISION_EPSILON) {
            return Math.max(distance, Math.min(0d, obstacle.maxX() - moving.minX()));
        }
        return distance;
    }

    private static double clipY(AabbSnapshot moving, AabbSnapshot obstacle, double distance) {
        if (!overlaps(moving.minX(), moving.maxX(), obstacle.minX(), obstacle.maxX())
            || !overlaps(moving.minZ(), moving.maxZ(), obstacle.minZ(), obstacle.maxZ())) {
            return distance;
        }
        if (distance > 0d && moving.maxY() <= obstacle.minY() + COLLISION_EPSILON) {
            return Math.min(distance, Math.max(0d, obstacle.minY() - moving.maxY()));
        }
        if (distance < 0d && moving.minY() >= obstacle.maxY() - COLLISION_EPSILON) {
            return Math.max(distance, Math.min(0d, obstacle.maxY() - moving.minY()));
        }
        return distance;
    }

    private static double clipZ(AabbSnapshot moving, AabbSnapshot obstacle, double distance) {
        if (!overlaps(moving.minX(), moving.maxX(), obstacle.minX(), obstacle.maxX())
            || !overlaps(moving.minY(), moving.maxY(), obstacle.minY(), obstacle.maxY())) {
            return distance;
        }
        if (distance > 0d && moving.maxZ() <= obstacle.minZ() + COLLISION_EPSILON) {
            return Math.min(distance, Math.max(0d, obstacle.minZ() - moving.maxZ()));
        }
        if (distance < 0d && moving.minZ() >= obstacle.maxZ() - COLLISION_EPSILON) {
            return Math.max(distance, Math.min(0d, obstacle.maxZ() - moving.minZ()));
        }
        return distance;
    }

    private static boolean overlaps(double minA, double maxA, double minB, double maxB) {
        return maxA > minB + COLLISION_EPSILON && minA < maxB - COLLISION_EPSILON;
    }

    private static List<AabbSnapshot> collisionBoxes(List<WorldSnapshot.BlockSnapshot> blocks) {
        List<AabbSnapshot> boxes = new ArrayList<>();
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!block.collision()) continue;
            if (!block.collisionBoxes().isEmpty()) {
                boxes.addAll(block.collisionBoxes());
                continue;
            }
            AabbSnapshot conservative = FallLandingSolver.conservativeCollisionBox(block);
            if (conservative != null) boxes.add(conservative);
        }
        return List.copyOf(boxes);
    }

    private static double effectiveGravity(PlayerSnapshot player, Vec3Snapshot velocity, long futureTick) {
        try {
            return FallLandingSolver.effectiveGravityForFutureMovementTick(player, velocity, futureTick);
        } catch (RuntimeException ignored) {
            return DEFAULT_GRAVITY;
        }
    }

    private static LookState lookState(PlayerSnapshot player, Vec3Snapshot velocity) {
        Double pitch = finiteDouble(player.state("elytra_pitch_degrees"));
        Double lookX = finiteDouble(player.state("elytra_look_x"));
        Double lookY = finiteDouble(player.state("elytra_look_y"));
        Double lookZ = finiteDouble(player.state("elytra_look_z"));
        if (lookX != null && lookY != null && lookZ != null) {
            return new LookState(new Vec3Snapshot(lookX, lookY, lookZ), pitch == null ? 0d : pitch);
        }

        double horizontal = horizontalLength(velocity);
        Vec3Snapshot fallback = horizontal > COLLISION_EPSILON
            ? new Vec3Snapshot(velocity.x() / horizontal, 0d, velocity.z() / horizontal)
            : new Vec3Snapshot(0d, 0d, 1d);
        return new LookState(fallback, pitch == null ? 0d : pitch);
    }

    private static AabbSnapshot move(AabbSnapshot box, Vec3Snapshot movement) {
        return new AabbSnapshot(
            box.minX() + movement.x(), box.minY() + movement.y(), box.minZ() + movement.z(),
            box.maxX() + movement.x(), box.maxY() + movement.y(), box.maxZ() + movement.z()
        );
    }

    private static Vec3Snapshot add(Vec3Snapshot value, double x, double y, double z) {
        return new Vec3Snapshot(value.x() + x, value.y() + y, value.z() + z);
    }

    private static double horizontalLength(Vec3Snapshot value) {
        return Math.sqrt(value.x() * value.x() + value.z() * value.z());
    }

    private static boolean differs(double expected, double actual) {
        return Math.abs(expected - actual) > COLLISION_EPSILON;
    }

    private static double square(double value) {
        return value * value;
    }

    private static String value(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static Double finiteDouble(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record CollisionPrediction(
        long tick,
        double preCollisionHorizontalSpeed,
        double postCollisionHorizontalSpeed,
        DamageRange rawDamage,
        AabbSnapshot resolvedBox,
        Vec3Snapshot requestedMovement,
        Vec3Snapshot resolvedMovement,
        Confidence confidence
    ) {
        public CollisionPrediction {
            if (tick < 1L) throw new IllegalArgumentException("tick must be positive");
            rawDamage = Objects.requireNonNull(rawDamage, "rawDamage");
            resolvedBox = Objects.requireNonNull(resolvedBox, "resolvedBox");
            requestedMovement = Objects.requireNonNull(requestedMovement, "requestedMovement");
            resolvedMovement = Objects.requireNonNull(resolvedMovement, "resolvedMovement");
            confidence = Objects.requireNonNull(confidence, "confidence");
        }
    }

    private record LookState(Vec3Snapshot vector, double pitchDegrees) {
    }

    private enum Axis {
        X, Y, Z
    }
}
