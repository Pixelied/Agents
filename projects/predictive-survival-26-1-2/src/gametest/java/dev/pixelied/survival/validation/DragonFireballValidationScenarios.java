package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.threat.ProjectilePredictor;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.hurtingprojectile.DragonFireball;
import net.minecraft.world.phys.Vec3;

final class DragonFireballValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();

    private DragonFireballValidationScenarios() {
    }

    static void validateObservableDamageHasPreImpactThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            DragonFireball fireball = new DragonFireball(EntityType.DRAGON_FIREBALL, level);
            fireball.setOwner(owner);
            fireball.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            fireball.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(fireball);
            return new Setup(fireball.getId(), owner.getId());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(setup.projectileId()) != null);
            boolean predictedThreat = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for dragon-fireball validation");
                }
                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                PredictionContext predictionContext = new PredictionContext(
                    player,
                    new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS),
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                return new ProjectilePredictor().predict(predictionContext).stream()
                    .anyMatch(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"));
            });

            boolean damaged = false;
            float actualHealth = 20f;
            for (int tick = 0; tick < 40; tick++) {
                context.waitTick();
                actualHealth = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
                );
                if (actualHealth < 20f) {
                    damaged = true;
                    break;
                }
            }

            if (!damaged) {
                throw new AssertionError("dragon-fireball fixture produced no server damage within 40 ticks");
            }
            if (!predictedThreat) {
                throw new AssertionError(
                    "live dragon fireball caused server damage but the pre-impact production predictor emitted no threat; actualHealth="
                        + actualHealth
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity projectile = level.getEntity(setup.projectileId());
                if (projectile != null) projectile.discard();
                Entity owner = level.getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                level.getEntities(
                    player,
                    player.getBoundingBox().inflate(16d),
                    entity -> entity.getType() == EntityType.AREA_EFFECT_CLOUD
                ).forEach(Entity::discard);
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitTick();
        }
    }

    private record Setup(int projectileId, int ownerId) {
    }
}
