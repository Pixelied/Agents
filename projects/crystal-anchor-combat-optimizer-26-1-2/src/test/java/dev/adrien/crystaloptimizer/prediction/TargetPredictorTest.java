package dev.adrien.crystaloptimizer.prediction;

import java.time.Duration;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetPredictorTest {
    private final TargetPredictor predictor = new TargetPredictor();

    @Test
    void confidenceDropsAsPredictionHorizonGrows() {
        PredictionSet oneTick = predictor.predict(stableHistory(), Duration.ofMillis(50));
        PredictionSet fiveTicks = predictor.predict(stableHistory(), Duration.ofMillis(250));

        assertTrue(oneTick.confidence() > fiveTicks.confidence());
    }

    @Test
    void recentDirectionReversalsLowerConfidenceAndKeepMultipleHypotheses() {
        PredictionSet stable = predictor.predict(stableHistory(), Duration.ofMillis(100));
        PredictionSet reversing = predictor.predict(reversingHistory(), Duration.ofMillis(100));

        assertTrue(stable.confidence() > reversing.confidence());
        assertTrue(reversing.hypotheses().size() >= 3);
        assertEquals(1.0, reversing.hypotheses().stream().mapToDouble(PositionHypothesis::weight).sum(), 1.0e-9);
    }

    @Test
    void simulatedKnockbackMovesFollowupHypotheses() {
        PredictionSet current = predictor.predict(stableHistory(), Duration.ofMillis(50));
        PredictionSet after = predictor.afterExplosion(
            current,
            new Vec3(0.7, 0.2, 0.0),
            Duration.ofMillis(50)
        );

        assertTrue(after.likely().position().x > current.likely().position().x);
        assertTrue(after.likely().position().y > current.likely().position().y);
        assertTrue(after.confidence() < current.confidence());
    }

    private static List<MovementSample> stableHistory() {
        return List.of(
            new MovementSample(0L, new Vec3(0.0, 64.0, 0.0), new Vec3(0.20, 0.0, 0.0)),
            new MovementSample(50_000_000L, new Vec3(0.20, 64.0, 0.0), new Vec3(0.20, 0.0, 0.0)),
            new MovementSample(100_000_000L, new Vec3(0.40, 64.0, 0.0), new Vec3(0.20, 0.0, 0.0))
        );
    }

    private static List<MovementSample> reversingHistory() {
        return List.of(
            new MovementSample(0L, new Vec3(0.0, 64.0, 0.0), new Vec3(0.22, 0.0, 0.0)),
            new MovementSample(50_000_000L, new Vec3(0.22, 64.0, 0.0), new Vec3(-0.18, 0.0, 0.0)),
            new MovementSample(100_000_000L, new Vec3(0.04, 64.0, 0.0), new Vec3(0.16, 0.0, 0.0))
        );
    }
}
