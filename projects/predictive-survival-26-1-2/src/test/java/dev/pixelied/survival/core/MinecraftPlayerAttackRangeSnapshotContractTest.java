package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftPlayerAttackRangeSnapshotContractTest {
    @Test
    void remotePlayerSnapshotPublishesExactMainHandAttackRangeState() throws Exception {
        String adapter = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(adapter.contains("player.getAttackRangeWith(player.getMainHandItem())"));
        assertTrue(adapter.contains("properties.put(\"main_hand_attack_min_range\""));
        assertTrue(adapter.contains("properties.put(\"main_hand_attack_max_range\""));
        assertTrue(adapter.contains("properties.put(\"main_hand_attack_hitbox_margin\""));
        assertTrue(adapter.contains("properties.put(\"main_hand_count\""));
    }
}
