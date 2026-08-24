package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.world.CombatRegion;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Collision-aware short-horizon remote-player predictor for strategic scoring. */
public final class TargetPredictor {
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final double GRAVITY_PER_TICK = 0.08;
    private static final double VERTICAL_FRICTION = 0.98;
    private static final double NO_INPUT_HORIZONTAL_FRICTION = 0.91;
    private static final double BRAKING_EXTRA_FRICTION = 0.72;
    private static final double COLLISION_EPSILON = 1.0E-7;

    private final PredictionCollisionResolver collisionResolver = new PredictionCollisionResolver();

    public PredictionSet predict(List<MovementSample> history, Duration horizon) {
        Objects.requireNonNull(history, "history");
        if (history.isEmpty()) {
            throw new IllegalArgumentException("history must not be empty");
        }
        MovementSample latest = history.stream()
            .max(Comparator.comparingLong(MovementSample::timestampNanos))
            .orElseThrow();
        AABB box = defaultPlayerBox(latest.position());
        return predict(
            history,
            CombatRegion.empty(),
            box,
            horizon,
            PredictionCalibration.defaults()
        );
    }

    public PredictionSet predict(
        List<MovementSample> history,
        CombatRegion geometry,
        AABB currentBox,
        Duration horizon,
        PredictionCalibration calibration
    ) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(currentBox, "currentBox");
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(calibration, "calibration");
        if (history.isEmpty()) {
            throw new IllegalArgumentException("history must not be empty");
        }
        if (horizon.isNegative()) {
            throw new IllegalArgumentException("horizon must be non-negative");
        }

        List<MovementSample> samples = history.stream()
            .sorted(Comparator.comparingLong(MovementSample::timestampNanos))
            .toList();
        MovementSample latest = samples.getLast();
        double ticks = horizon.toNanos() / NANOS_PER_TICK;
        double volatility = movementVolatility(samples);
        double confidence = clamp01(Math.exp(-0.22 * ticks) * (1.0 - 0.55 * volatility));
        Map<PositionHypothesis.Kind, Double> weights = calibration.normalizedWeights();

        Vec3 likelyVelocity = latest.velocity();
        Vec3 brakingVelocity = latest.velocity();
        Vec3 turnVelocity = turnOrReversalVelocity(samples);

        return new PredictionSet(
            List.of(
                propagate(
                    PositionHypothesis.Kind.LIKELY_INERTIAL,
                    latest.position(),
                    currentBox,
                    likelyVelocity,
                    ticks,
                    geometry,
                    weights.get(PositionHypothesis.Kind.LIKELY_INERTIAL)
                ),
                propagate(
                    PositionHypothesis.Kind.BRAKING,
                    latest.position(),
                    currentBox,
                    brakingVelocity,
                    ticks,
                    geometry,
                    weights.get(PositionHypothesis.Kind.BRAKING)
                ),
                propagate(
                    PositionHypothesis.Kind.TURN_OR_REVERSAL,
                    latest.position(),
                    currentBox,
                    turnVelocity,
                    ticks,
                    geometry,
                    weights.get(PositionHypothesis.Kind.TURN_OR_REVERSAL)
                )
            ),
            confidence
        );
    }

    public PredictionSet afterExplosion(
        PredictionSet current,
        Vec3 knockback,
        Duration additionalHorizon
    ) {
        return afterExplosion(
            current,
            knockback,
            CombatRegion.empty(),
            additionalHorizon,
            PredictionCalibration.defaults()
        );
    }

    public PredictionSet afterExplosion(
        PredictionSet current,
        Vec3 knockback,
        CombatRegion geometry,
        Duration additionalHorizon,
        PredictionCalibration calibration
    ) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(additionalHorizon, "additionalHorizon");
        Objects.requireNonNull(calibration, "calibration");
        if (additionalHorizon.isNegative()) {
            throw new IllegalArgumentException("additionalHorizon must be non-negative");
        }

        double ticks = additionalHorizon.toNanos() / NANOS_PER_TICK;
        Map<PositionHypothesis.Kind, Double> weights = calibration.normalizedWeights();
        List<PredictedSpatialState> shifted = new ArrayList<>(current.hypotheses().size());
        for (PredictedSpatialState hypothesis : current.hypotheses()) {
            Vec3 velocityWithKnockback = hypothesis.velocity().add(knockback);
            double weight = weights.getOrDefault(hypothesis.kind(), hypothesis.weight());
            shifted.add(propagate(
                hypothesis.kind(),
                hypothesis.position(),
                hypothesis.box(),
                velocityWithKnockback,
                ticks,
                geometry,
                weight
            ));
        }
        shifted = renormalize(shifted);

        double nextConfidence = clamp01(
            current.confidence()
                * Math.exp(-0.28 * ticks)
                * (knockback.lengthSqr() > 0.0 ? 0.96 : 1.0)
        );
        return new PredictionSet(shifted, nextConfidence);
    }

    private PredictedSpatialState propagate(
        PositionHypothesis.Kind kind,
        Vec3 startPosition,
        AABB startBox,
        Vec3 startVelocity,
        double ticks,
        CombatRegion geometry,
        double weight
    ) {
        Vec3 position = startPosition;
        AABB box = startBox;
        Vec3 velocity = startVelocity;
        double remaining = ticks;
        while (remaining > 1.0E-9) {
            double stepTicks = Math.min(1.0, remaining);
            Vec3 requested = velocity.scale(stepTicks);
            CollisionMoveResult move = collisionResolver.move(box, requested, geometry);
            Vec3 resolved = move.resolvedDelta();
            position = position.add(resolved);
            box = move.box();

            velocity = new Vec3(
                move.collidedX() ? 0.0 : velocity.x,
                move.collidedY() ? 0.0 : velocity.y,
                move.collidedZ() ? 0.0 : velocity.z
            );
            velocity = nextVelocity(kind, velocity, stepTicks);
            remaining -= stepTicks;
        }
        return new PredictedSpatialState(kind, position, box, velocity, weight);
    }

    private static Vec3 nextVelocity(
        PositionHypothesis.Kind kind,
        Vec3 velocity,
        double stepTicks
    ) {
        double horizontalFactor = switch (kind) {
            case LIKELY_INERTIAL -> 1.0;
            case BRAKING -> Math.pow(
                NO_INPUT_HORIZONTAL_FRICTION * BRAKING_EXTRA_FRICTION,
                stepTicks
            );
            case TURN_OR_REVERSAL -> Math.pow(NO_INPUT_HORIZONTAL_FRICTION, stepTicks);
            default -> Math.pow(NO_INPUT_HORIZONTAL_FRICTION, stepTicks);
        };
        double nextY = (velocity.y - GRAVITY_PER_TICK * stepTicks)
            * Math.pow(VERTICAL_FRICTION, stepTicks);
        if (Math.abs(nextY) < COLLISION_EPSILON) {
            nextY = 0.0;
        }
        return new Vec3(
            velocity.x * horizontalFactor,
            nextY,
            velocity.z * horizontalFactor
        );
    }

    private static Vec3 turnOrReversalVelocity(List<MovementSample> samples) {
        MovementSample latest = samples.getLast();
        double maxObservedSpeed = samples.stream()
            .mapToDouble(sample -> sample.velocity().length())
            .max()
            .orElse(latest.velocity().length());
        if (latest.velocity().lengthSqr() <= 1.0E-12 || maxObservedSpeed <= 1.0E-9) {
            return Vec3.ZERO;
        }

        Vec3 candidate;
        if (samples.size() >= 2) {
            Vec3 previous = samples.get(samples.size() - 2).velocity();
            Vec3 change = latest.velocity().subtract(previous);
            candidate = latest.velocity().add(change.scale(1.5));
            if (candidate.lengthSqr() <= 1.0E-12) {
                candidate = latest.velocity().scale(-0.35);
            }
        } else {
            candidate = latest.velocity().scale(-0.35);
        }
        return clampLength(candidate, maxObservedSpeed);
    }

    private static List<PredictedSpatialState> renormalize(List<PredictedSpatialState> states) {
        double sum = states.stream().mapToDouble(PredictedSpatialState::weight).sum();
        if (sum <= 0.0) {
            throw new IllegalArgumentException("prediction weights must be positive");
        }
        return states.stream()
            .map(state -> state.withWeight(state.weight() / sum))
            .toList();
    }

    private static double movementVolatility(List<MovementSample> samples) {
        if (samples.size() < 2) {
            return 0.0;
        }

        double directionChange = 0.0;
        double speedChange = 0.0;
        int comparisons = 0;
        for (int index = 1; index < samples.size(); index++) {
            Vec3 previous = samples.get(index - 1).velocity();
            Vec3 current = samples.get(index).velocity();
            double previousSpeed = previous.length();
            double currentSpeed = current.length();
            double maxSpeed = Math.max(previousSpeed, currentSpeed);

            if (previousSpeed > 1.0e-9 && currentSpeed > 1.0e-9) {
                double cosine = clamp(previous.dot(current) / (previousSpeed * currentSpeed), -1.0, 1.0);
                directionChange += (1.0 - cosine) * 0.5;
            } else if (maxSpeed > 1.0e-9) {
                directionChange += 0.5;
            }

            if (maxSpeed > 1.0e-9) {
                speedChange += Math.min(1.0, Math.abs(currentSpeed - previousSpeed) / maxSpeed);
            }
            comparisons++;
        }

        return clamp01(((directionChange / comparisons) * 0.75) + ((speedChange / comparisons) * 0.25));
    }

    private static Vec3 clampLength(Vec3 vector, double maximumLength) {
        double length = vector.length();
        if (length <= maximumLength || length <= 1.0E-9) {
            return vector;
        }
        return vector.scale(maximumLength / length);
    }

    private static AABB defaultPlayerBox(Vec3 position) {
        return new AABB(
            position.x - 0.3,
            position.y,
            position.z - 0.3,
            position.x + 0.3,
            position.y + 1.8,
            position.z + 0.3
        );
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
