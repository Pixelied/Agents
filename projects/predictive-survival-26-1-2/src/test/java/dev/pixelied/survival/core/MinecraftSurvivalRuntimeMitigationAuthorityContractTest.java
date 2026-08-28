package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSurvivalRuntimeMitigationAuthorityContractTest {
    @Test
    void runtimeQueuesOptimisticMitigationAndUsesConservativeProjection() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(runtime.contains(
            "authority.observeUntrackedLocalMitigation(rawPlayer.mitigation(), timing);"
        ), "live optimistic armor changes must enter the bounded equipment authority queue");
        assertTrue(runtime.contains(
            "equipment.conservativeMitigationAt(serverTick)"
        ), "decision-critical PlayerSnapshot mitigation must come from the conservative authority projection");
    }
}
