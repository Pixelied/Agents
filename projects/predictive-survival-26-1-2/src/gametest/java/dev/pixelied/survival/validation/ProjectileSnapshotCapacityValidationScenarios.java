package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.WorldSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class ProjectileSnapshotCapacityValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final EngineLimits SMALL_LIMITS = new EngineLimits(2, 32, 80, 128);

    private ProjectileSnapshotCapacityValidationScenarios() {
    }

    static void validateHarmlessTrackedEntitiesCannotCrowdOutDamagingProjectile(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            List<Integer> fillerIds = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ArmorStand stand = new ArmorStand(
                    level,
                    player.getX() + 4d,
                    player.getY(),
                    player.getZ() + 0.5d * i
                );
                stand.setNoGravity(true);
                level.addFreshEntity(stand);
                fillerIds.add(stand.getId());
            }

            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 60d);
            Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
            arrow.setNoGravity(true);
            arrow.setDeltaMovement(0d, 0d, -1.2d);
            level.addFreshEntity(arrow);
            return new Setup(arrow.getId(), List.copyOf(fillerIds));
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) instanceof Arrow
                && setup.fillerIds().stream().allMatch(id -> minecraft.level.getEntity(id) instanceof ArmorStand));

            ClientObservation client = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable while testing snapshot capacity");
                }
                WorldSnapshot snapshot = new MinecraftWorldSnapshotFactory().capture(
                    minecraft.level,
                    minecraft.player,
                    SMALL_LIMITS
                );
                boolean projectilePresent = snapshot.entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.projectileId())));
                long fillerCount = snapshot.entities().stream()
                    .filter(entity -> setup.fillerIds().contains(Integer.parseInt(entity.id())))
                    .count();
                return new ClientObservation(snapshot.entities().size(), fillerCount, projectilePresent);
            });

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
                    "snapshot-capacity arrow did not hit within 90 ticks; client=" + client
                );
            }
            if (!client.projectilePresent()) {
                throw new AssertionError(
                    "harmless client-tracked entities crowded a damaging projectile out of the production world snapshot; "
                        + "actualDamageTick=" + actualDamageTick + " client=" + client
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity projectile = player.level().getEntity(setup.projectileId());
                if (projectile != null) projectile.discard();
                for (int fillerId : setup.fillerIds()) {
                    Entity filler = player.level().getEntity(fillerId);
                    if (filler != null) filler.discard();
                }
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private record Setup(int projectileId, List<Integer> fillerIds) {
    }

    private record ClientObservation(int snapshotEntityCount, long fillerCount, boolean projectilePresent) {
    }
}
