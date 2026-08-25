package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftMeleeOpportunitySnapshotContractTest {
    @Test
    void remotePlayerSnapshotPublishesCrystalOpportunityEvidence() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(adapter.contains("player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE)"));
        assertTrue(adapter.contains("properties.put(\"block_interaction_range\""));
        assertTrue(adapter.contains("properties.put(\"main_hand_item_key\", itemKey(player.getMainHandItem()))"));
        assertTrue(adapter.contains("properties.put(\"offhand_item_key\", itemKey(player.getOffhandItem()))"));
        assertTrue(adapter.contains("player.getEyePosition()"));
        assertTrue(adapter.contains("properties.put(\"eye_position_x\""));
        assertTrue(adapter.contains("properties.put(\"eye_position_y\""));
        assertTrue(adapter.contains("properties.put(\"eye_position_z\""));
        assertTrue(adapter.contains("properties.put(\"attack_range\""));
    }
}
