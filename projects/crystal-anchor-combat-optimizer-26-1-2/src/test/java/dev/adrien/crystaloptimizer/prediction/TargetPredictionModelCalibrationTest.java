package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TargetPredictionModelCalibrationTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000010101");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000010102");

    @Test
    void laterObservedPositionCalibratesPendingProbeOncePerTarget() {
        TargetPredictionModel model = new TargetPredictionModel();
        List<MovementSample> firstHistory = List.of(
            sample(0L, 0.0, 0.40),
            sample(50_000_000L, 0.4, 0.40),
            sample(100_000_000L, 0.8, 0.40)
        );
        StrategicSnapshot first = snapshot(1L, 100_000_000L, 0.8, firstHistory);
        Map<PositionHypothesis.Kind, Double> before = model.weights(TARGET);

        PredictionSet prediction = model.predict(first, TARGET, Duration.ofMillis(100)).orElseThrow();
        double brakingX = prediction.hypotheses().stream()
            .filter(hypothesis -> hypothesis.kind() == PositionHypothesis.Kind.BRAKING)
            .findFirst().orElseThrow().position().x;

        List<MovementSample> observedHistory = new ArrayList<>(firstHistory);
        observedHistory.add(sample(200_000_000L, brakingX, 0.05));
        StrategicSnapshot observed = snapshot(2L, 200_000_000L, brakingX, observedHistory);
        model.observeSnapshot(observed);
        Map<PositionHypothesis.Kind, Double> after = model.weights(TARGET);

        assertTrue(after.get(PositionHypothesis.Kind.BRAKING)
            > before.get(PositionHypothesis.Kind.BRAKING));
        assertTrue(after.get(PositionHypothesis.Kind.LIKELY_INERTIAL)
            < before.get(PositionHypothesis.Kind.LIKELY_INERTIAL));
        assertEquals(0, model.pendingProbeCount(TARGET));

        model.observeSnapshot(observed);
        assertEquals(after, model.weights(TARGET),
            "the same observation must not calibrate an already-consumed probe twice");
    }

    private static MovementSample sample(long nanos, double x, double vx) {
        return new MovementSample(nanos, new Vec3(x, 64.0, 0.0), new Vec3(vx, 0.0, 0.0));
    }

    private static StrategicSnapshot snapshot(
        long id,
        long capturedAtNanos,
        double x,
        List<MovementSample> history
    ) {
        Vec3 selfPos = new Vec3(-4.0, 64.0, 0.0);
        Vec3 targetPos = new Vec3(x, 64.0, 0.0);
        CombatSnapshot combat = new CombatSnapshot(
            id,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, SimCombatant.testPlayer(20.0f)),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(Vec3.ZERO, 5.0, 5.0, List.of(), false),
            Map.of(
                SELF, spatial(selfPos, Vec3.ZERO),
                TARGET, spatial(targetPos, history.getLast().velocity())
            ),
            Difficulty.NORMAL
        );
        return new StrategicSnapshot(
            id,
            id,
            id,
            id,
            capturedAtNanos,
            SELF,
            Map.of(TARGET, id),
            combat,
            Map.of(TARGET, history),
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            TimingSnapshot.empty(capturedAtNanos)
        );
    }

    private static CombatantSpatialState spatial(Vec3 position, Vec3 velocity) {
        return new CombatantSpatialState(
            position,
            new AABB(
                position.x - 0.3, position.y, position.z - 0.3,
                position.x + 0.3, position.y + 1.8, position.z + 0.3
            ),
            velocity
        );
    }
}
