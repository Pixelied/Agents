package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftWorldSnapshotBroadPhaseContractTest {
    @Test
    void liveCaptureUsesBoundedTopKInsteadOfMaterializingAndSortingEveryRenderedEntity() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertTrue(
            source.contains("BoundedTopKAccumulator<Entity> tracked"),
            "live broad phase must retain only the configured top-K entities"
        );
        assertFalse(
            source.contains("tracked.sort("),
            "live broad phase must not full-sort every rendered entity before applying the cap"
        );
    }
}
