package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V2StrategicScannerArchitectureTest {
    @Test
    void strategicScannerMayScanButReactivePackagesNeverImportItOrBeamPlanner() throws IOException {
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));
        assertTrue(scanner.contains("ClientDamageMapBuilder"));
        assertTrue(scanner.contains("blackboard.publish("));
        assertTrue(scanner.contains("FastOpportunitySelector"));

        Path reactiveRoot = Path.of("src/main/java/dev/adrien/crystaloptimizer/v2/reactive");
        try (var files = Files.walk(reactiveRoot)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                assertFalse(source.contains("ClientStrategicScanner"), file + " imports strategic scanner");
                assertFalse(source.contains("BeamPlanner"), file + " imports beam planner");
                assertFalse(source.contains("ClientCombatSnapshotBuilder"), file + " builds snapshots");
            }
        }
    }
}
