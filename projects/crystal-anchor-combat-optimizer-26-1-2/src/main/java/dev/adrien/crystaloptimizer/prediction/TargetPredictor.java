package dev.adrien.crystaloptimizer.prediction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.Vec3;

public final class TargetPredictor {
    private static final double NANOS_PER_TICK = 50_000_000.0;

    public PredictionSet predict(List<MovementSample> history, Duration horizon) {
        Objects.requireNonNull(history, "history");
        Objects.requireNonNull(horizon, "horizon");
        if (history.isEmpty()) {
            throw new IllegalArgumentException("history must not be empty");
        }
        if (horizon.isNegative()) {
            throw new IllegalArgumentException("horizon must be non-negative");
        }

        List<MovementSample> samples = history.stream()
            .sorted(Comparator.comparingLong(MovementSample::timestampNanos))
            .toList();
        MovementSample latest = samples.get(samples.size() - 1);
        double ticks = horizon.toNanos() / NANOS_PER_TICK;
        double volatility = movementVolatility(samples);
        double confidence = clamp01(Math.exp(-0.22 * ticks) * (1.0 - 0.55 * volatility));

        Vec3 likelyVelocity = latest.velocity();
        Vec3 likelyPosition = latest.position().add(likelyVelocity.scale(ticks));

        double reversalScale = volatility >= 0.45
            ? -Math.min(0.35, 0.15 + volatility * 0.20)
            : 0.35;
        Vec3 slowedVelocity = latest.velocity().scale(reversalScale);
        Vec3 slowedPosition = latest.position().add(slowedVelocity.scale(ticks));

        double maxObservedSpeed = samples.stream()
            .mapToDouble(sample -> sample.velocity().length())
            .max()
            .orElse(latest.velocity().length());
        Vec3 boundaryVelocity = boundedDirection(latest.velocity(), maxObservedSpeed);
        Vec3 boundaryPosition = latest.position().add(boundaryVelocity.scale(ticks));

        double likelyWeight = 0.68 - 0.25 * volatility;
        double alternateWeight = 0.20 + 0.12 * volatility;
        double boundWeight = 1.0 - likelyWeight - alternateWeight;
        double total = likelyWeight + alternateWeight + boundWeight;

        return new PredictionSet(
            List.of(
                new PositionHypothesis(
                    PositionHypothesis.Kind.LIKELY,
                    likelyPosition,
                    likelyVelocity,
                    likelyWeight / total
                ),
                new PositionHypothesis(
                    PositionHypothesis.Kind.SLOWED_OR_REVERSAL,
                    slowedPosition,
                    slowedVelocity,
                    alternateWeight / total
                ),
                new PositionHypothesis(
                    PositionHypothesis.Kind.CONSERVATIVE_BOUND,
                    boundaryPosition,
                    boundaryVelocity,
                    boundWeight / total
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
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(knockback, "knockback");
        Objects.requireNonNull(additionalHorizon, "additionalHorizon");
        if (additionalHorizon.isNegative()) {
            throw new IllegalArgumentException("additionalHorizon must be non-negative");
        }

        double ticks = additionalHorizon.toNanos() / NANOS_PER_TICK;
        List<PositionHypothesis> shifted = new ArrayList<>(current.hypotheses().size());
        for (PositionHypothesis hypothesis : current.hypotheses()) {
            Vec3 nextVelocity = hypothesis.velocity().add(knockback);
            Vec3 nextPosition = hypothesis.position().add(nextVelocity.scale(ticks));
            shifted.add(new PositionHypothesis(
                hypothesis.kind(),
                nextPosition,
                nextVelocity,
                hypothesis.weight()
            ));
        }

        double nextConfidence = clamp01(
            current.confidence() * Math.exp(-0.28 * ticks) * (knockback.lengthSqr() > 0.0 ? 0.96 : 1.0)
        );
        return new PredictionSet(shifted, nextConfidence);
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

    private static Vec3 boundedDirection(Vec3 velocity, double maxObservedSpeed) {
        double speed = velocity.length();
        if (speed <= 1.0e-9 || maxObservedSpeed <= 1.0e-9) {
            return Vec3.ZERO;
        }
        return velocity.scale(maxObservedSpeed / speed);
    }

    private static double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
