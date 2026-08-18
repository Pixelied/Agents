package dev.pixelied.survival.validation;

import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

final class InventoryValidationScenarios {
    private InventoryValidationScenarios() {
    }

    static void validateOffhandSwapAndLethalPop(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        final int sourceInventoryIndex = 10;

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setItem(sourceInventoryIndex, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.GOLDEN_APPLE));
            player.containerMenu.broadcastChanges();
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getItem(sourceInventoryIndex).is(Items.TOTEM_OF_UNDYING)
            && minecraft.player.getOffhandItem().is(Items.GOLDEN_APPLE));

        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable for inventory swap validation");
            MinecraftInventorySnapshotFactory snapshots = new MinecraftInventorySnapshotFactory();
            var inventory = snapshots.captureInventory(minecraft.player);
            var menu = snapshots.captureMenu(minecraft.player);
            DeathProtectionRoute route = new DeathProtectionRoutePlanner().choose(inventory, menu)
                .orElseThrow(() -> new AssertionError("no death-protection route for synced inventory Totem"));
            if (!(route instanceof DeathProtectionRoute.ContainerSwap swap)) {
                throw new AssertionError("expected main-inventory Totem to use ContainerSwap, got " + route);
            }
            if (swap.destination() != DeathProtectionRoute.Destination.OFF_HAND || swap.button() != 40) {
                throw new AssertionError(
                    "expected offhand SWAP button 40, got destination=" + swap.destination() + " button=" + swap.button()
                );
            }

            boolean dispatched = new MinecraftCommandDispatcher().dispatch(
                minecraft,
                new ExecutionCommand.SwapMenuSlot(
                    menu.containerId(),
                    menu.stateId(),
                    swap.sourceMenuSlot(),
                    swap.button()
                )
            );
            if (!dispatched) throw new AssertionError("MinecraftCommandDispatcher rejected valid offhand SWAP");
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
            && minecraft.player.getInventory().getItem(sourceInventoryIndex).is(Items.GOLDEN_APPLE));

        PopAfterSwap state = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                throw new AssertionError("server did not observe Totem in offhand after client SWAP");
            }
            if (!player.getInventory().getItem(sourceInventoryIndex).is(Items.GOLDEN_APPLE)) {
                throw new AssertionError("server did not preserve prior offhand item in source inventory slot");
            }

            player.invulnerableTime = 0;
            player.setHealth(4f);
            player.hurtServer((ServerLevel) player.level(), player.damageSources().generic(), 20f);
            return new PopAfterSwap(player.getHealth(), player.getOffhandItem().isEmpty());
        });

        SurvivalValidationClientGameTest.assertClose("inventory_swap_lethal_pop", 1f, state.health(), 0.0001f);
        if (!state.offhandConsumed()) {
            throw new AssertionError("Totem moved by live client SWAP was not consumed by lethal damage");
        }

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.getInventory().setItem(sourceInventoryIndex, ItemStack.EMPTY);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();
        });
        context.waitTick();
    }

    private record PopAfterSwap(float health, boolean offhandConsumed) {
    }
}
