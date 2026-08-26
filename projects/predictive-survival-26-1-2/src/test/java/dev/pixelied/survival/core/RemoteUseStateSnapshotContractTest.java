package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteUseStateSnapshotContractTest {
    @Test
    void remotePlayerSnapshotPublishesSynchronizedItemUsePrecursorState() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(source.contains("properties.put(\"using_item\", Boolean.toString(player.isUsingItem()));"));
        assertTrue(source.contains("player.getUsedItemHand() == InteractionHand.OFF_HAND ? \"off_hand\" : \"main_hand\""));
        assertTrue(source.contains("properties.put(\"used_hand\", player.isUsingItem()"));
        assertTrue(source.contains("properties.put(\"client_observed_use_ticks\", Integer.toString(Math.max(0, player.getTicksUsingItem())));"));
    }
}
