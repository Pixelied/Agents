package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

final class ProjectileSnapshotRangeValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private ProjectileSnapshotRangeValidationScenarios() {
    }

    static void validateClientTrackedDistantArrowIsSnapshotted(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int projectileId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 60d);
            Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(0d, 0d, -1.2d);
            level.addFreshEntity(arrow);
            return arrow.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(projectileId) instanceof Arrow);

            ClientObservation client = context.computeOnClient(minecraft -> {
                Entity projectile = minecraft.level.getEntity(projectileId);
                if (!(projectile instanceof Arrow)) {
                    throw new AssertionError("distant arrow disappeared before client observation");
                }
                if (minecraft.player == null) {
                    throw new AssertionError("client player unavailable while observing distant arrow");
                }

                double horizontalDistance = Math.hypot(
                    projectile.getX() - minecraft.player.getX(),
                    projectile.getZ() - minecraft.player.getZ()
                );
                SurvivalEngine.EngineFrame frame = new MinecraftSurvivalRuntime(minecraft).capture();
                boolean snapshotted = frame.context().world().entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(projectileId)));
                ThreatEvent predicted = frame.timeline().events().stream()
                    .filter(event -> event.id().startsWith("projectile:" + projectileId + ":"))
                    .filter(event -> "minecraft:arrow".equals(event.damage().sourceKey()))
                    .findFirst()
                    .orElse(null);
                return new ClientObservation(horizontalDistance, snapshotted, predicted != null);
            });

            if (client.horizontalDistance() <= 48d) {
                throw new AssertionError(
                    "distant-arrow fixture reached the old 48-block snapshot range before observation; client=" + client
                );
            }

            int actualDamageTick = -1;
            for (int tick = 1; tick <= 90; tick++) {
                context.waitTick();
                float health = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
                );
                if (health < 20f - EPSILON) {
                    actualDamageTick = tick;
                    break;
                }
            }

            if (actualDamageTick < 0) {
                throw new AssertionError(
                    "distant-arrow fixture was client-tracked outside 48 blocks but did not hit within 90 ticks; client=" + client
                );
            }
            if (!client.snapshotted() || !client.predictedArrow()) {
                throw new AssertionError(
                    "client-tracked damaging arrow outside 48 blocks was omitted from the production threat snapshot; "
                        + "actualDamageTick=" + actualDamageTick + " client=" + client
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity projectile = player.level().getEntity(projectileId);
                if (projectile != null) projectile.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private record ClientObservation(
        double horizontalDistance,
        boolean snapshotted,
        boolean predictedArrow
    ) {
    }
}
