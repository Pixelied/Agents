package dev.pixelied.survival.validation;

import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;
import java.util.stream.Collectors;

final class NonTotemCandidateValidationScenarios {
    private NonTotemCandidateValidationScenarios() {
    }

    static void validateLiveItemCapabilities(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.NETHERITE_CHESTPLATE));
            player.containerMenu.broadcastChanges();
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getSelectedSlot() == 0
            && minecraft.player.getInventory().getItem(0).is(Items.ENCHANTED_GOLDEN_APPLE)
            && minecraft.player.getOffhandItem().is(Items.NETHERITE_CHESTPLATE));

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) {
                throw new AssertionError("client player unavailable for non-totem capability validation");
            }
            var inventory = new MinecraftInventorySnapshotFactory().captureInventory(minecraft.player);

            var consumable = inventory.slot(0).orElseThrow().consumable()
                .orElseThrow(() -> new AssertionError("enchanted golden apple did not expose consumable capability"));
            if (!consumable.usable()) throw new AssertionError("enchanted golden apple should be usable from selected hand");
            if (consumable.consumeTicks() != 32) {
                throw new AssertionError("enchanted golden apple consumeTicks expected=32 actual=" + consumable.consumeTicks());
            }
            Set<String> effectKeys = consumable.guaranteedEffects().stream()
                .map(effect -> effect.effectKey())
                .collect(Collectors.toSet());
            Set<String> requiredEffects = Set.of(
                "minecraft:regeneration",
                "minecraft:resistance",
                "minecraft:fire_resistance",
                "minecraft:absorption"
            );
            if (!effectKeys.containsAll(requiredEffects)) {
                throw new AssertionError("missing guaranteed enchanted golden apple effects: " + effectKeys);
            }

            var equippable = inventory.slot(40).orElseThrow().equippable()
                .orElseThrow(() -> new AssertionError("netherite chestplate did not expose equippable capability"));
            if (!equippable.usable()) throw new AssertionError("offhand netherite chestplate should be swappable onto empty chest slot");
            ArmorPieceSnapshot armor = equippable.armorPiece();
            if (armor.slot() != ArmorPieceSnapshot.Slot.CHEST) {
                throw new AssertionError("netherite chestplate mapped to wrong armor slot: " + armor.slot());
            }
            SurvivalValidationClientGameTest.assertClose("live_netherite_chest_armor", 8f, armor.armor(), 0.0001f);
            SurvivalValidationClientGameTest.assertClose("live_netherite_chest_toughness", 3f, armor.toughness(), 0.0001f);
        });

        singleplayer.getServer().runOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(0, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        context.waitTick();
    }
}
