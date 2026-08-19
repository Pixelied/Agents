package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.cow.Cow;

final class CrammingRuntimeValidationScenarios {
    private CrammingRuntimeValidationScenarios() {
    }

    static void validateOverlappingPushableEntityProducesPotentialCrammingThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int cowId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Cow cow = new Cow(EntityType.COW, player.level());
            cow.snapTo(player.getX(), player.getY(), player.getZ(), 0f, 0f);
            cow.setNoAi(true);
            player.level().addFreshEntity(cow);
            return cow.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getEntity(cowId) instanceof Cow
                && !minecraft.level.getPushableEntities(
                    minecraft.player,
                    minecraft.player.getBoundingBox()
                ).isEmpty());

            boolean predicted = context.computeOnClient(minecraft ->
                new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                    .anyMatch(event -> "minecraft:cramming".equals(event.damage().sourceKey()))
            );
            if (!predicted) {
                throw new AssertionError(
                    "production runtime omitted conservative cramming threat for an overlapping pushable entity"
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity cow = player.level().getEntity(cowId);
                if (cow != null) cow.discard();
            });
            context.waitTick();
        }
    }
}
