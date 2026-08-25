package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RemotePlayerBurstInventoryContractTest {
    @Test
    void remotePlayerSnapshotCarriesBothHandsAndBlockInteractionRangeForBurstPrecursors() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(
            source.contains("player.getOffhandItem()"),
            "a crystal held in a remote player's offhand must remain observable to burst prediction"
        );
        assertTrue(
            source.contains("\"offhand_item_key\""),
            "remote offhand registry identity must be carried into the world snapshot"
        );
        assertTrue(
            source.contains("Attributes.BLOCK_INTERACTION_RANGE"),
            "crystal/anchor/bed placement reach must use the synced block-interaction range when available"
        );
    }
}
