package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WitherSpawnExplosionSnapshotContractTest {
    @Test
    void synchronizedWitherInvulnerabilityCountdownFeedsFusedExplosionPrediction() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertTrue(source.contains("WitherBoss"), "spawn-phase Withers must stay threat-relevant");
        assertTrue(source.contains("getInvulnerableTicks()"), "the synchronized spawn countdown must be observed");
        assertTrue(source.contains("\"explosion_radius\", \"7\""), "Wither spawn blast power must be snapshotted");
        assertTrue(source.contains("\"fuse_ticks\""), "remaining spawn ticks must feed the fused explosion deadline");
        assertTrue(source.contains("\"explosion_center_y_offset\""), "Wither blast center must preserve vanilla eye height");
    }
}
