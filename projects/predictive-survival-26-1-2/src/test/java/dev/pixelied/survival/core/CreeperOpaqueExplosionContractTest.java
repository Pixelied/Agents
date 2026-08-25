package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreeperOpaqueExplosionContractTest {
    @Test
    void remoteCreeperUsesOnlySynchronizedPrimingStateAndOpaqueNbtBounds() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        String branchAnchor = "} else if (entity instanceof Creeper creeper";
        int start = source.indexOf(branchAnchor);
        int end = source.indexOf("} else if (", start + branchAnchor.length());
        assertTrue(start >= 0 && end > start, "creeper snapshot branch must exist");
        String creeper = source.substring(start, end);

        assertFalse(creeper.contains("getSwelling(1f)"),
            "remote client swell progress depends on opaque server Fuse/tracking history and must not become an exact deadline");
        assertFalse(creeper.contains("\"fuse_ticks\""),
            "opaque creeper fuse must not be snapshotted as exact");
        assertTrue(creeper.contains("\"triggerable\", \"true\""),
            "a priming/ignited creeper must be modeled as capable of exploding inside the immediate reaction window");
        assertTrue(creeper.contains("\"explosion_radius_min\", \"0.0\""),
            "creeper explosion radius must preserve the legal non-damaging lower bound");
        assertTrue(creeper.contains("creeper.isPowered() ? \"254.0\" : \"127.0\""),
            "creeper explosion radius must preserve the maximum harmful signed-byte NBT radius, doubled when powered");
        assertTrue(source.contains("creeper.getSwellDir() > 0 || creeper.isIgnited()"),
            "explicit synchronized ignition must become threat-relevant before waiting for a later swell-direction update");
    }
}
