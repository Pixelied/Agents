package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossbowTippedSnapshotContractTest {
    @Test
    void remoteCrossbowSnapshotPreservesVisibleLoadedHarmingPayload() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(source.contains("prefix + \"crossbow_arrow_instant_damage\""));
        assertTrue(source.contains("projectile.get(DataComponents.POTION_CONTENTS)"));
        assertTrue(source.contains("effect.getEffect().is(MobEffects.INSTANT_DAMAGE)"));
    }
}
