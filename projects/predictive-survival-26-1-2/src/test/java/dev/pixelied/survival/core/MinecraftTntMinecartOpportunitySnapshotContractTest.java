package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftTntMinecartOpportunitySnapshotContractTest {
    @Test
    void liveSnapshotPublishesUnprimedMinecartTriggerEvidence() throws Exception {
        String factory = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertTrue(factory.contains("properties.put(\"fall_distance\""));
        assertTrue(factory.contains("minecart.fallDistance"));
        assertTrue(factory.contains("GameRules.TNT_EXPLODES"));
        assertTrue(factory.contains("properties.put(\"tnt_explodes\""));
    }

    @Test
    void liveSnapshotPublishesBurningAbstractArrowEvidence() throws Exception {
        String factory = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        ));

        assertTrue(factory.contains("properties.put(\"abstract_arrow\", \"true\")"));
        assertTrue(factory.contains("properties.put(\"on_fire\", Boolean.toString(arrow.isOnFire()))"));
    }
}
