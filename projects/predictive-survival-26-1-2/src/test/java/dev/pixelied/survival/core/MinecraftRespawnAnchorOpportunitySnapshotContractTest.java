package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftRespawnAnchorOpportunitySnapshotContractTest {
    @Test
    void nearbyAnchorSnapshotPublishesChargeAndDimensionExplosionRuleAtEveryCharge() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftNearbyBlockSnapshotFactory.java"
        ));

        assertTrue(source.contains("int charge = state.getValue(RespawnAnchorBlock.CHARGE);"));
        assertTrue(source.contains("properties.put(\"anchor_explodes\", Boolean.toString(!works));"));
        assertTrue(source.contains("properties.put(\"anchor_charge\", Integer.toString(charge));"));
        assertTrue(source.contains("if (!works && charge > 0)"));
    }

    @Test
    void extendedAnchorSnapshotKeepsUnchargedExplosiveDimensionAnchorsForPrecursors() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftTriggerableExplosionSnapshotFactory.java"
        ));

        assertTrue(source.contains("int charge = state.getValue(RespawnAnchorBlock.CHARGE);"));
        assertTrue(source.contains("if (works) return null;"));
        assertTrue(source.contains("properties.put(\"anchor_explodes\", \"true\");"));
        assertTrue(source.contains("properties.put(\"anchor_charge\", Integer.toString(charge));"));
        assertTrue(source.contains("if (charge > 0)"));
    }
}
