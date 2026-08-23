package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class OffhandStackCaptureArchitectureTest {
    @Test
    void plannerAndLiveDispatcherCaptureExactOffhandStackCount() throws Exception {
        String snapshotBuilder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
        ));
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));

        assertTrue(snapshotBuilder.contains("offhand.getCount()"),
            "strategic snapshots must preserve the real offhand stack quantity");
        assertTrue(dispatcher.contains("offhand.getCount()"),
            "live interaction routing must preserve the same offhand stack quantity model");
    }
}
