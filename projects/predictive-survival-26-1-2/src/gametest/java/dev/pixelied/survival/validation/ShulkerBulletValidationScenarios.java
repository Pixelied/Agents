package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ShulkerBulletPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

final class ShulkerBulletValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float EPSILON = 0.0001f;

    private ShulkerBulletValidationScenarios() {
    }

    static void validateVisibleBulletProducesPreImpactThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            ArmorStand owner = new ArmorStand(level, player.getX(), player.getY(), player.getZ() + 8d);
            owner.setNoGravity(true);
            level.addFreshEntity(owner);
            ShulkerBullet bullet = new ShulkerBullet(level, owner, player, null);
            level.addFreshEntity(bullet);
            return new Setup(owner.getId(), bullet.getId());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.bulletId()) instanceof ShulkerBullet);

            ThreatEvent predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for shulker bullet validation");
                }
                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                boolean bulletPresent = world.entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.bulletId()))
                        && entity.typeKey().equals("minecraft:shulker_bullet"));
                if (!bulletPresent) {
                    throw new AssertionError("client-tracked shulker bullet missing from production world snapshot");
                }
                PredictionContext predictionContext = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                return new ShulkerBulletPredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().equals("shulker_bullet:" + setup.bulletId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("visible shulker bullet produced no pre-impact threat"));
            });

            int actualDamageTick = waitForDamage(context, singleplayer);
            if (predicted.impact().earliest() > actualDamageTick || predicted.impact().latest() < actualDamageTick) {
                throw new AssertionError(
                    "shulker bullet impact window did not contain the real hit; predicted=" + predicted.impact()
                        + " actualTick=" + actualDamageTick
                );
            }
            if (predicted.damage().rawDamage().min() > 4f + EPSILON
                || predicted.damage().rawDamage().max() < 4f - EPSILON) {
                throw new AssertionError("shulker bullet raw range did not contain vanilla 4 damage: " + predicted.damage().rawDamage());
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity bullet = player.level().getEntity(setup.bulletId());
                if (bullet != null) bullet.discard();
                Entity owner = player.level().getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static int waitForDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (int tick = 1; tick <= LIMITS.maxProjectileHorizonTicks(); tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < 20f - EPSILON) return tick;
        }
        throw new AssertionError("shulker bullet did not hit within the configured projectile horizon");
    }

    private record Setup(int ownerId, int bulletId) {
    }
}
