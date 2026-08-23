package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSurvivalRuntimeRestorationContractTest {
    @Test
    void runtimeCollectsRestorationCheckpointsFromEveryInventoryMutatingExecutor() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(runtime.contains(
            "protectionExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);"
        ));
        assertTrue(runtime.contains(
            "shieldExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);"
        ), "routed shields must participate in restoreHandState");
        assertTrue(runtime.contains(
            "nonTotemExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);"
        ), "routed consumables/equipment must participate in restoreHandState");
    }
}
