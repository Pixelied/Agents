package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeMitigationAuthorityContractTest {
    @Test
    void runtimeTracksOptimisticMitigationAndUsesFailClosedAuthorityState() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(
            source.contains("authority.observeUntrackedLocalMitigation(rawPlayer.mitigation(), timing);"),
            "runtime capture must feed optimistic local mitigation into the equipment authority tracker"
        );
        assertTrue(
            source.contains("equipment.conservativeMitigationAt(serverTick)"),
            "decision PlayerSnapshot must use fail-closed server-authority mitigation"
        );
    }
}
