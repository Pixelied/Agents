package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftBlockReplaceabilitySnapshotContractTest {
    @Test
    void nearbyBlockSnapshotPublishesContextFreeReplaceability() throws Exception {
        String factory = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftNearbyBlockSnapshotFactory.java"
        ));

        assertTrue(factory.contains(
            "properties.put(\"replaceable\", Boolean.toString(state.canBeReplaced()))"
        ));
    }
}
