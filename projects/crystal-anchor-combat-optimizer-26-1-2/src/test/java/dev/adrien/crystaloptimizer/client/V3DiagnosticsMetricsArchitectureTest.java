package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3DiagnosticsMetricsArchitectureTest {
    @Test
    void liveMetricsAreFedByWorkerScannerAndStaleResultPaths() throws Exception {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));
        String service = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicPlannerService.java"
        ));

        assertTrue(service.contains("lastComputationNanos"));
        assertTrue(coordinator.contains("diagnostics.recordStrategicDuration(plannerService.lastComputationNanos())"));
        assertTrue(coordinator.contains("diagnostics.recordStaleResult()"));
        assertTrue(scanner.contains("diagnostics.recordHurtWindowConfidence(threshold.confidence())"));
        assertTrue(scanner.contains("diagnostics.recordCandidateCounts"));
    }
}
