package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftProjectileIgnitionSnapshotContractTest {
    @Test
    void genericProjectileSnapshotPublishesObservedFireStateForVehicleIgnition() throws Exception {
        String factory = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertTrue(factory.contains("if (entity instanceof Projectile projectile)"));
        assertTrue(factory.contains("properties.put(\"projectile\", \"true\")"));
        assertTrue(factory.contains("properties.put(\"on_fire\", Boolean.toString(projectile.isOnFire()))"));
    }
}
