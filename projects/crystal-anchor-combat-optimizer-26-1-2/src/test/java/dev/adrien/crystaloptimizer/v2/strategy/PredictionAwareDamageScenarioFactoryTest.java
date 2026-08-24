package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.prediction.TargetPredictionModel;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEngine;
import dev.adrien.crystaloptimizer.v2.damage.DamageUncertainty;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PredictionAwareDamageScenarioFactoryTest {
    @Test
    void delayedExplosionUsesMovementHypothesesAndChangesExpectedDamage() {
        var history = Task10PredictionFixtures.movingAwayHistory();
        var snapshot = Task10PredictionFixtures.snapshot(
            1L,
            Task10PredictionFixtures.TICK_NANOS * 2L,
            history.getLast().position(),
            history
        );
        CombatState state = CombatState.fromSnapshot(snapshot.combat(), Task10PredictionFixtures.TARGET);
        TargetPredictionModel predictions = new TargetPredictionModel();
        StrategicDamageScenarioFactory factory = new StrategicDamageScenarioFactory(predictions);
        DamageEngine damage = new DamageEngine();
        ExplosionContext explosion = ExplosionContext.crystal(new Vec3(0.5, 65.0, 0.0));

        var immediate = factory.targetScenarios(snapshot, state, SequenceTiming.immediate());
        var delayed = factory.targetScenarios(
            snapshot,
            state,
            new SequenceTiming(150.0, 250.0, 1, 0.9)
        );

        assertTrue(delayed.stream().anyMatch(scenario ->
            scenario.uncertainties().contains(DamageUncertainty.PREDICTED_POSITION)));
        assertTrue(delayed.stream().anyMatch(scenario ->
            scenario.position().x > state.targetSpatial().position().x + 0.1));
        assertEquals(1.0, delayed.stream().mapToDouble(scenario -> scenario.probabilityWeight()).sum(), 1.0e-9);
        assertFalse(immediate.stream().anyMatch(scenario ->
            scenario.uncertainties().contains(DamageUncertainty.PREDICTED_POSITION)));

        float immediateExpected = damage.estimate(
            explosion,
            state,
            immediate,
            snapshot.worldRevision(),
            snapshot.worldRevision()
        ).expected();
        float delayedExpected = damage.estimate(
            explosion,
            state,
            delayed,
            snapshot.worldRevision(),
            snapshot.worldRevision()
        ).expected();
        assertTrue(delayedExpected < immediateExpected,
            "moving-away prediction must lower expected delayed explosion damage");
    }

    @Test
    void noHistoryFallsBackToCurrentSpatialStateEvenForDelayedAction() {
        Vec3 current = new Vec3(2.8, 64.0, 0.0);
        var snapshot = Task10PredictionFixtures.snapshot(2L, 200_000_000L, current, List.of());
        CombatState state = CombatState.fromSnapshot(snapshot.combat(), Task10PredictionFixtures.TARGET);
        StrategicDamageScenarioFactory factory = new StrategicDamageScenarioFactory(new TargetPredictionModel());

        var scenarios = factory.targetScenarios(
            snapshot,
            state,
            new SequenceTiming(150.0, 250.0, 1, 0.9)
        );

        assertTrue(scenarios.stream().allMatch(scenario -> scenario.position().equals(current)));
        assertFalse(scenarios.stream().anyMatch(scenario ->
            scenario.uncertainties().contains(DamageUncertainty.PREDICTED_POSITION)));
    }
}
