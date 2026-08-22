package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3TargetIntegrationArchitectureTest {
    @Test
    void liveStrategicPathUsesBoundedWorkerSelectionWithoutThreeTargetShortlist() throws Exception {
        String targetManager = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java"
        ));
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));
        String planner = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicCombatPlanner.java"
        ));
        String builder = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicDamageMapBuilder.java"
        ));

        assertFalse(targetManager.contains("SHORTLIST_LIMIT = 3"));
        assertTrue(targetManager.contains("StrategicTargetSelector.MAX_EXACT_TARGETS"));
        assertTrue(coordinator.contains("ClientStrategicSnapshotCapture"));
        assertTrue(coordinator.contains("ClientStrategicPlannerService"));
        assertTrue(coordinator.contains("plannerService.submit("));
        assertTrue(coordinator.contains("scanner.publish("));
        assertTrue(planner.contains("selector.selectBest"));
        assertTrue(planner.contains("exactMaps.computeIfAbsent"),
            "worker target scoring must reuse each exact damage map within one strategic computation");
        assertFalse(scanner.contains("damageMaps.update"));
        assertTrue(scanner.contains("DamageMap map"));
        assertTrue(builder.contains("CollateralSafetyPolicy"));
        assertTrue(builder.contains("protectedPlayerIds"));
    }
}
