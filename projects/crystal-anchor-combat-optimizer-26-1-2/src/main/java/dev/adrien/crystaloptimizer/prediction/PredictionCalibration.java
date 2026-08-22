package dev.adrien.crystaloptimizer.prediction;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Small bounded EMA calibration for the three V3 behavioral hypotheses. */
public final class PredictionCalibration {
    private static final double EMA_ALPHA = 0.20;
    private static final double MIN_WEIGHT = 0.05;
    private static final double DISTRIBUTABLE_WEIGHT = 0.85;
    private static final PositionHypothesis.Kind[] ACTIVE = {
        PositionHypothesis.Kind.LIKELY_INERTIAL,
        PositionHypothesis.Kind.BRAKING,
        PositionHypothesis.Kind.TURN_OR_REVERSAL
    };

    private final EnumMap<PositionHypothesis.Kind, Double> errorEma =
        new EnumMap<>(PositionHypothesis.Kind.class);

    public PredictionCalibration() {
        errorEma.put(PositionHypothesis.Kind.LIKELY_INERTIAL, 0.55);
        errorEma.put(PositionHypothesis.Kind.BRAKING, 0.80);
        errorEma.put(PositionHypothesis.Kind.TURN_OR_REVERSAL, 1.10);
    }

    public static PredictionCalibration defaults() {
        return new PredictionCalibration();
    }

    public synchronized void observeError(PositionHypothesis.Kind kind, double error) {
        Objects.requireNonNull(kind, "kind");
        if (!isActive(kind)) {
            return;
        }
        if (!Double.isFinite(error) || error < 0.0) {
            throw new IllegalArgumentException("prediction error must be finite and non-negative");
        }
        double previous = errorEma.get(kind);
        errorEma.put(kind, previous + EMA_ALPHA * (error - previous));
    }

    public synchronized Map<PositionHypothesis.Kind, Double> normalizedWeights() {
        EnumMap<PositionHypothesis.Kind, Double> scores = new EnumMap<>(PositionHypothesis.Kind.class);
        double scoreSum = 0.0;
        for (PositionHypothesis.Kind kind : ACTIVE) {
            double score = 1.0 / (0.25 + errorEma.get(kind));
            scores.put(kind, score);
            scoreSum += score;
        }

        EnumMap<PositionHypothesis.Kind, Double> weights = new EnumMap<>(PositionHypothesis.Kind.class);
        for (PositionHypothesis.Kind kind : ACTIVE) {
            double normalized = scores.get(kind) / scoreSum;
            weights.put(kind, MIN_WEIGHT + DISTRIBUTABLE_WEIGHT * normalized);
        }
        return Map.copyOf(weights);
    }

    public synchronized double errorEma(PositionHypothesis.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        return errorEma.getOrDefault(kind, Double.POSITIVE_INFINITY);
    }

    private static boolean isActive(PositionHypothesis.Kind kind) {
        for (PositionHypothesis.Kind active : ACTIVE) {
            if (kind == active) {
                return true;
            }
        }
        return false;
    }
}
