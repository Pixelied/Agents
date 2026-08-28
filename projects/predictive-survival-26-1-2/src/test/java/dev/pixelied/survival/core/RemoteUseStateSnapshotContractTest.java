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

    @Test
    void remotePlayerSnapshotPublishesObservableInstantReleaseComponentsForBothHands() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(source.contains("putProjectileReleaseItemProperties(properties, \"main_hand_\", player.getMainHandItem());"));
        assertTrue(source.contains("putProjectileReleaseItemProperties(properties, \"off_hand_\", player.getOffhandItem());"));
        assertTrue(source.contains("prefix + \"crossbow_projectile_kind\""));
        assertTrue(source.contains("prefix + \"crossbow_firework_explosions\""));
        assertTrue(source.contains("prefix + \"potion_instant_damage\""));
        assertTrue(source.contains("DataComponents.CHARGED_PROJECTILES"));
        assertTrue(source.contains("DataComponents.POTION_CONTENTS"));
    }

    @Test
    void remotePlayerSnapshotPublishesVisibleBowPowerForBothHands() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftMeleeSnapshotAdapter.java"
        ));

        assertTrue(source.contains("stack.is(Items.BOW)"));
        assertTrue(source.contains("prefix + \"bow_power_enchantment_level\""));
        assertTrue(source.contains("enchantmentLevel(stack, Enchantments.POWER)"));
    }
}
