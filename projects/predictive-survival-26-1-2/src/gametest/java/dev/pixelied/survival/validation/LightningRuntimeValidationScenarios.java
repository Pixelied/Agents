package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;

final class LightningRuntimeValidationScenarios {
    private LightningRuntimeValidationScenarios() {
    }

    static void validateVisibleVisualOnlyBoltFailsClosed(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int boltId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, player.level());
            bolt.snapTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
            bolt.setVisualOnly(true);
            player.level().addFreshEntity(bolt);
            return bolt.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(boltId) instanceof LightningBolt);

            long predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for lightning validation");
                }
                return new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                    .filter(event -> "minecraft:lightning_bolt".equals(event.damage().sourceKey()))
                    .count();
            });
            if (predicted != 4L) {
                throw new AssertionError(
                    "production runtime expected four conservative cooldown-eligible lightning threats, found " + predicted
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity bolt = player.level().getEntity(boltId);
                if (bolt != null) bolt.discard();
            });
            context.waitTick();
        }
    }
}
