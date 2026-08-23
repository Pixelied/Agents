package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3PlannedOpportunityPublishingArchitectureTest {
    @Test
    void precomputedPlanCrossesWorkerBoundaryWithoutReactiveSearch() throws IOException {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));

        assertTrue(coordinator.contains("result.plannedOpportunity()"));
        assertTrue(scanner.contains("Optional<PlannedOpportunity> plannedOpportunity"));
        assertTrue(scanner.contains("plannedOpportunity.ifPresent"));
        assertFalse(coordinator.contains("new V3SequencePlanner"));
        assertFalse(scanner.contains("new V3SequencePlanner"));
        assertFalse(scanner.contains("new BeamPlanner"));
    }
}
