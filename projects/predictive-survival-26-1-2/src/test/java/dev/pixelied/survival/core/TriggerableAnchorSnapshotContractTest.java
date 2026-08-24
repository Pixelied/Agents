package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriggerableAnchorSnapshotContractTest {
    @Test
    void dangerousDimensionAnchorPreservesChargeZeroAsObservableBurstPrecursor() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftTriggerableExplosionSnapshotFactory.java"
        ));

        assertTrue(source.contains("\"anchor_explodes\""));
        assertTrue(source.contains("\"anchor_charge\""));
        assertFalse(
            source.contains("works || state.getValue(RespawnAnchorBlock.CHARGE) <= 0"),
            "charge-0 anchors must not be discarded before the predictor can see a glowstone precharge race"
        );
    }
}
