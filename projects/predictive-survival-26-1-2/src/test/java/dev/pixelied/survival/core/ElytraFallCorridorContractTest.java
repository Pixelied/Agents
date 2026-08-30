package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraFallCorridorContractTest {
    @Test
    void fallFlyingUsesAProjectedSweepInsteadOfStoppingAtTheNearbyCube() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftFallCorridorSnapshotFactory.java"
        ));

        assertTrue(source.contains("captureFallFlyingSweep("),
            "fast Elytra paths need a trajectory-derived block sweep beyond the fixed nearby cube");
        assertFalse(source.contains("if (player.isFallFlying()\n            ||"),
            "fall-flying must not share the early-return branch used for ordinary non-descending movement");
    }
}
