package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePlayerMeleeSourceContractTest {
    @Test
    void remotePlayerSnapshotDoesNotReadUnsyncableAttackDamage() throws IOException {
        Path sourcePath = Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftWorldSnapshotFactory.java"
        );
        String source = Files.readString(sourcePath);

        assertFalse(
            source.contains("remotePlayer.getAttribute(Attributes.ATTACK_DAMAGE)"),
            "ATTACK_DAMAGE is not client-syncable in Minecraft 26.1.2"
        );
        assertFalse(
            source.contains("remotePlayer.getAttributeValue(Attributes.ATTACK_DAMAGE)"),
            "ATTACK_DAMAGE is not client-syncable in Minecraft 26.1.2"
        );
        assertTrue(
            source.contains("MinecraftMeleeSnapshotAdapter.playerProperties(remotePlayer"),
            "remote player melee must be reconstructed through the fail-closed adapter"
        );
    }
}
