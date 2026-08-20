package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2ProductionCutoverArchitectureTest {
    @Test
    void clientBootstrapUsesV2CoordinatorAndPersistentConfigOnly() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java"
        ));

        assertTrue(source.contains("OptimizerConfigService.instance()"));
        assertTrue(source.contains("ClientCombatCoordinator.create("));
        assertTrue(source.contains("ClientCombatEventBus.instance().subscribe(coordinator::onEvent)"));
        assertTrue(source.contains("OptimizerHud.register(coordinator::diagnostics)"));
        assertTrue(source.contains("configService.apply("));
        assertTrue(source.contains("coordinator.tick()"));
        assertFalse(source.contains("ClientCombatRuntime"),
            "production bootstrap must stop constructing the V1 orchestration runtime");
    }

    @Test
    void v2CoordinatorOwnsConcreteClientCompositionWithoutHotPathPlannerWork() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        assertTrue(source.contains("public static ClientCombatCoordinator create("));
        assertTrue(source.contains("ClientStrategicScanner"));
        assertTrue(source.contains("ClientLiveCombatView"));
        assertTrue(source.contains("ReactiveBurstDispatcher"));

        int start = source.indexOf("public void onEvent(CombatEvent event)");
        int end = source.indexOf("public ClientCombatDiagnostics diagnostics()", start);
        assertTrue(start >= 0 && end > start);
        String hotPath = source.substring(start, end);
        assertFalse(hotPath.contains("ClientStrategicScanner"));
        assertFalse(hotPath.contains("ClientDamageMapBuilder"));
        assertFalse(hotPath.contains("BeamPlanner"));
    }
}
