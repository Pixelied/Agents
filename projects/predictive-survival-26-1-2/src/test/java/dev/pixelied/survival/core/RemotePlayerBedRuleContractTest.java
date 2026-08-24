package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePlayerBedRuleContractTest {
    @Test
    void remotePlayerSnapshotCarriesWhetherBedsExplodeInCurrentEnvironment() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(source.contains("EnvironmentAttributes.BED_RULE"));
        assertTrue(source.contains("\"bed_explodes\""));
    }
}
