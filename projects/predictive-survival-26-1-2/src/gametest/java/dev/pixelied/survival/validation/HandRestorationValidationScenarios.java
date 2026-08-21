package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class HandRestorationValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final int SERVER_AUTHORITY_WAIT_TICKS = 200;

    private HandRestorationValidationScenarios() {
    }

    static void validateSafeHandRestorationAfterThreatDisappears(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int skeletonId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STONE));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            player.containerMenu.broadcastChanges();

            ServerLevel level = (ServerLevel) player.level();
            WitherSkeleton skeleton = EntityType.WITHER_SKELETON.spawn(
                level,
                player.blockPosition().offset(0, 0, 1),
                EntitySpawnReason.TRIGGERED
            );
            if (skeleton == null) throw new AssertionError("failed to spawn Wither Skeleton for restoration validation");
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            skeleton.setPersistenceRequired();
            skeleton.setDeltaMovement(Vec3.ZERO);
            skeleton.setPos(player.getX(), player.getY(), player.getZ() + 1.0d);
            return skeleton.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getEntity(skeletonId) instanceof WitherSkeleton
                && Math.abs(minecraft.player.getHealth() - 4f) <= EPSILON
                && minecraft.player.getInventory().getSelectedSlot() == 0
                && minecraft.player.getInventory().getItem(0).is(Items.STONE)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            waitForServerAuthoritativeTotemSelection(context, singleplayer);

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(skeletonId);
                if (entity == null) throw new AssertionError("Wither Skeleton disappeared before restoration danger removal");
                if (player.getInventory().getSelectedSlot() != 1 || !player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server never observed emergency Totem selection before restoration validation");
                }
                entity.discard();
            });

            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(skeletonId) == null);
            waitForServerRestoration(context, singleplayer);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(skeletonId);
                if (entity != null) entity.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                player.containerMenu.broadcastChanges();
            });
            context.waitTick();
        }
    }

    private static void waitForServerAuthoritativeTotemSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < SERVER_AUTHORITY_WAIT_TICKS; tick++) {
            boolean selected = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 1
                    && player.getMainHandItem().is(Items.TOTEM_OF_UNDYING);
            });
            if (selected) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not make Totem server-authoritative for restoration validation");
    }

    private static void waitForServerRestoration(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < SERVER_AUTHORITY_WAIT_TICKS; tick++) {
            boolean restored = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getInventory().getSelectedSlot() == 0
                    && player.getMainHandItem().is(Items.STONE)
                    && player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING);
            });
            if (restored) return;
            context.waitTick();
        }
        throw new AssertionError("Predictive Survival did not restore the original server-authoritative hand state after danger disappeared");
    }
}
