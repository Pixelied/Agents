package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ElytraSnapshotContractTest {
    @Test
    void localSnapshotPreservesVanillaLookAndPitchInputsForElytraSolver() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSnapshotFactory.java"
        ));

        assertTrue(source.contains("Vec3 lookAngle = player.getLookAngle()"));
        assertTrue(source.contains("state.put(\"elytra_pitch_degrees\", Float.toString(player.getXRot()))"));
        assertTrue(source.contains("state.put(\"elytra_look_x\", Double.toString(lookAngle.x()))"));
        assertTrue(source.contains("state.put(\"elytra_look_y\", Double.toString(lookAngle.y()))"));
        assertTrue(source.contains("state.put(\"elytra_look_z\", Double.toString(lookAngle.z()))"));
    }
}
