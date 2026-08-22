package dev.adrien.crystaloptimizer.prediction;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3TargetPredictorPhysicsTest {
    private final TargetPredictor predictor = new TargetPredictor();

    @Test
    void predictionDoesNotPassThroughSolidWall() {
        PredictionSet predicted = predictor.predict(
            V3PredictionFixtures.samplesMovingTowardWall(),
            V3PredictionFixtures.geometryWithWallAtX(3),
            V3PredictionFixtures.currentBox(),
            Duration.ofMillis(200),
            PredictionCalibration.defaults()
        );

        assertTrue(predicted.hypotheses().stream()
            .allMatch(hypothesis -> hypothesis.box().maxX <= 3.0 + 1.0e-6));
        assertEquals(
            Set.of(
                PositionHypothesis.Kind.LIKELY_INERTIAL,
                PositionHypothesis.Kind.BRAKING,
                PositionHypothesis.Kind.TURN_OR_REVERSAL
            ),
            predicted.hypotheses().stream().map(PredictedSpatialState::kind).collect(java.util.stream.Collectors.toSet())
        );
        assertEquals(1.0, predicted.hypotheses().stream().mapToDouble(PredictedSpatialState::weight).sum(), 1.0e-9);
    }

    @Test
    void explosionKnockbackIsAppliedBeforeCollisionConstrainedFollowupPropagation() {
        PredictionCalibration calibration = PredictionCalibration.defaults();
        PredictionSet current = predictor.predict(
            V3PredictionFixtures.samplesMovingTowardWall(),
            V3PredictionFixtures.geometryWithWallAtX(3),
            V3PredictionFixtures.currentBox(),
            Duration.ofMillis(50),
            calibration
        );
        PredictionSet after = predictor.afterExplosion(
            current,
            new Vec3(0.75, 0.20, 0.0),
            V3PredictionFixtures.geometryWithWallAtX(3),
            Duration.ofMillis(100),
            calibration
        );

        assertTrue(after.likely().position().x >= current.likely().position().x);
        assertTrue(after.likely().box().maxX <= 3.0 + 1.0e-6);
        assertTrue(after.likely().velocity().x <= current.likely().velocity().x + 0.75 + 1.0e-9);
    }

    @Test
    void calibrationPenalizesHighErrorHypothesisAndKeepsNormalizedBoundedWeights() {
        PredictionCalibration calibration = PredictionCalibration.defaults();
        for (int i = 0; i < 20; i++) {
            calibration.observeError(PositionHypothesis.Kind.LIKELY_INERTIAL, 4.0);
            calibration.observeError(PositionHypothesis.Kind.BRAKING, 0.15);
            calibration.observeError(PositionHypothesis.Kind.TURN_OR_REVERSAL, 0.35);
        }

        Map<PositionHypothesis.Kind, Double> weights = calibration.normalizedWeights();
        assertTrue(weights.get(PositionHypothesis.Kind.LIKELY_INERTIAL)
            < weights.get(PositionHypothesis.Kind.BRAKING));
        assertEquals(1.0, weights.values().stream().mapToDouble(Double::doubleValue).sum(), 1.0e-9);
        assertTrue(weights.values().stream().allMatch(weight -> weight >= 0.05 && weight <= 0.90));
    }
}
