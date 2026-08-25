package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrimedTntOpaquePowerContractTest {
    @Test
    void unsynchronizedPrimedTntExplosionPowerIsNeverTreatedAsAuthoritative() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertFalse(
            source.contains("predictiveSurvival$getExplosionPower"),
            "PrimedTnt.explosionPower is server-side NBT and must not be read from the remote client entity as authoritative"
        );
        assertTrue(
            source.contains("properties.put(\"explosion_radius_min\", \"0.0\")"),
            "Primed TNT must preserve the legal lower explosion-power bound"
        );
        assertTrue(
            source.contains("properties.put(\"explosion_radius_max\", \"128.0\")"),
            "Primed TNT must preserve the legal upper explosion-power bound from vanilla NBT clamping"
        );
    }
}
