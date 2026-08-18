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
    void clientInitializerRegistersToggleAndDrivesOneRuntimeFromEndClientTick() throws IOException {
        Path initializerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java"
        );
        Path runtimePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java"
        );

        assertTrue(Files.exists(runtimePath), "live client combat runtime must exist");
        String initializer = Files.readString(initializerPath);
        String runtime = Files.readString(runtimePath);

        assertTrue(initializer.contains("KeyMappingHelper.registerKeyMapping"));
        assertTrue(initializer.contains("ClientTickEvents.END_CLIENT_TICK.register"));
        assertTrue(initializer.contains("consumeClick()"));
        assertTrue(initializer.contains("new ClientCombatRuntime"));
        assertFalse(initializer.contains("KeyBindingHelper"), "26.1 runtime must not use the legacy helper name");

        for (String required : List.of(
            "ClientCombatSnapshotBuilder",
            "TargetSelector",
            "TargetPredictor",
            "BeamPlanner",
            "CombatRuntimeEngine",
            "VanillaInteractionDispatcher",
            "RuntimeFrame"
        )) {
            assertTrue(runtime.contains(required), "runtime is missing integration: " + required);
        }

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
            assertFalse(runtime.contains(forbidden), "runtime contains forbidden primitive: " + forbidden);
        }
    }
}
