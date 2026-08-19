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
            cow.snapTo(player.getX() + 2d, player.getY(), player.getZ(), 0f, 0f);
            cow.setNoAi(true);
            player.level().addFreshEntity(cow);
            return cow.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.level != null
                && minecraft.level.getEntity(cowId) instanceof Cow);

            boolean predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for cramming validation");
                }
                Entity cow = minecraft.level.getEntity(cowId);
                if (!(cow instanceof Cow)) {
                    throw new AssertionError("client did not retain tracked cramming fixture cow");
                }

                // Establish an observable overlap immediately before capture so normal push physics
                // cannot race the test. ClientLevel#getPushableEntities(localPlayer, ...) intentionally
                // returns no remote entities in 26.1.2, so production must derive the conservative
                // server-cramming possibility from tracked overlap/pushability instead.
                cow.setPos(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
                boolean observableOverlap = cow.isPushable()
                    && !cow.isPassenger()
                    && cow.getBoundingBox().intersects(minecraft.player.getBoundingBox());
                if (!observableOverlap) {
                    throw new AssertionError("client fixture failed to establish a pushable overlap");
                }

                return new MinecraftSurvivalRuntime(minecraft).capture().timeline().events().stream()
                    .anyMatch(event -> "minecraft:cramming".equals(event.damage().sourceKey()));
            });
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
