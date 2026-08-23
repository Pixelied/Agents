package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3PredictionDamageIntegrationArchitectureTest {
    @Test
    void workerPredictionChangesDamageScenariosAndCalibratesAcrossSnapshots() throws Exception {
        String builder = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicDamageMapBuilder.java"
        ));
        String scenarios = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicDamageScenarioFactory.java"
        ));
        String planner = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicCombatPlanner.java"
        ));
        String model = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/prediction/TargetPredictionModel.java"
        ));

        assertTrue(builder.contains("TargetPredictionModel"));
        assertTrue(builder.contains("targetScenarios(snapshot, state, timing)"));
        assertTrue(scenarios.contains("snapshot.movementHistory()"));
        assertTrue(scenarios.contains("DamageUncertainty.PREDICTED_POSITION"));
        assertTrue(planner.contains("predictionModel.observeSnapshot(snapshot)"));
        assertTrue(model.contains("PredictionCalibration"));
        assertTrue(model.contains("pendingProbe"));
    }
}
