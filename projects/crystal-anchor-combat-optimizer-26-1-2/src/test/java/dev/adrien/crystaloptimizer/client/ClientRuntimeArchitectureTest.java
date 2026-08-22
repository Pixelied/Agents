package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientRuntimeArchitectureTest {
    @Test
    void clientInitializerRegistersToggleAndDrivesV2CoordinatorFromEndClientTick() throws IOException {
        String initializer = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java"
        ));
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));
        String snapshotBuilder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
        ));

        assertTrue(initializer.contains("KeyMappingHelper.registerKeyMapping"));
        assertTrue(initializer.contains("ClientTickEvents.END_CLIENT_TICK.register"));
        assertTrue(initializer.contains("consumeClick()"));
        assertTrue(initializer.contains("configService.apply("));
        assertTrue(initializer.contains("coordinator.tick()"));
        assertFalse(initializer.contains("ClientCombatRuntime"));
        assertFalse(initializer.contains("KeyBindingHelper"),
            "26.1 runtime must not use the legacy helper name");
        assertTrue(snapshotBuilder.contains("self.getAbsorptionAmount()"),
            "local absorption is directly observable and must be preserved in self state");

        for (String required : List.of(
            "ClientStrategicSnapshotCapture",
            "ClientStrategicPlannerService",
            "StrategicCombatPlanner",
            "TargetManager",
            "ClientStrategicScanner",
            "ReactiveCombatEngine",
            "ReactiveBurstDispatcher",
            "VanillaInteractionDispatcher"
        )) {
            assertTrue(coordinator.contains(required), "V3 coordinator is missing integration: " + required);
        }

        String productionExecution = initializer + coordinator + dispatcher;
        for (String forbidden : List.of(
            "setPos(",
            "setDeltaMovement(",
            "options.keyUp",
            "options.keyDown",
            "options.keyLeft",
            "options.keyRight",
            "new ServerboundUseItemOnPacket",
            "new ServerboundInteractPacket"
        )) {
            assertFalse(productionExecution.contains(forbidden),
                "V3 runtime contains forbidden primitive: " + forbidden);
        }
    }
}
