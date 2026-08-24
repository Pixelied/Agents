package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2ProductionReadinessArchitectureTest {
    @Test
    void strategicTickHonorsAutoRestockWithoutRevivingV1Scheduler() throws Exception {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        assertTrue(coordinator.contains("HotbarRestocker"));
        assertTrue(coordinator.contains("config.autoRestock()"));
        assertTrue(coordinator.contains("restocker.restockOne(self)"));
        assertTrue(coordinator.contains("InventoryCoordinator inventory = new InventoryCoordinator()"));
        assertFalse(coordinator.contains("CommitScheduler"),
            "V2 composition must not keep a dead V1 scheduler just to dispatch vanilla actions");
    }

    @Test
    void v2HudHonorsPersistedHudToggle() throws Exception {
        String diagnostics = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java"
        ));
        String hud = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java"
        ));
        assertTrue(diagnostics.contains("hudEnabled"));
        assertTrue(diagnostics.contains("config.hud()"));
        assertTrue(hud.contains("diagnostics.hudEnabled()"));
    }

    @Test
    void vanillaDispatcherHasV2ConstructorWithoutCommitScheduler() throws Exception {
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));
        assertTrue(dispatcher.contains(
            "VanillaInteractionDispatcher(\n        Minecraft minecraft,\n        RotationController rotations,\n        RotationMode rotationMode"
        ));
    }
}
