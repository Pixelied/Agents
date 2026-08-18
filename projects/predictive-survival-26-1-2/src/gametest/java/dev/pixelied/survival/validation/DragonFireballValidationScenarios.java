package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.timeline.ThreatEvent;
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
            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame projectileFrame = context.computeOnClient(minecraft -> runtime.capture());
            boolean precursorPredicted = projectileFrame.timeline().events().stream()
                .anyMatch(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":dragon_breath:"));
            if (!precursorPredicted) {
                throw new AssertionError("production runtime emitted no pre-impact dragon-breath precursor");
            }

            CloudObservation firstCloud = null;
            ThreatEvent persistentCloudThreat = null;
            float predictedHealth = Float.NaN;
            float actualHealth = 20f;
            for (int tick = 1; tick <= 40; tick++) {
                context.waitTick();
                CloudObservation observation = singleplayer.getServer().computeOnServer(server -> observe(server));

                if (firstCloud == null && observation.entityId() >= 0) {
                    firstCloud = observation;
                    int cloudId = observation.entityId();
                    context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(cloudId) != null);
                    SurvivalEngine.EngineFrame cloudFrame = context.computeOnClient(minecraft -> runtime.capture());
                    persistentCloudThreat = cloudFrame.timeline().events().stream()
                        .filter(event -> event.id().startsWith("env:area_effect_cloud:" + cloudId + ":"))
                        .findFirst()
                        .orElse(null);
                    if (persistentCloudThreat != null) {
                        predictedHealth = new DamageSimulator()
                            .simulate(cloudFrame.context().player(), persistentCloudThreat.damage())
                            .after().health();
                    }
                }

                actualHealth = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
                );
                if (actualHealth < 20f) break;
            }

            if (actualHealth >= 20f) {
                throw new AssertionError("dragon-fireball fixture produced no server damage within 40 ticks");
            }
            if (persistentCloudThreat == null) {
                throw new AssertionError(
                    "dragon projectile disappeared into a damaging area-effect cloud, but the production runtime lost "
                        + "the hazard attribution; firstCloud=" + firstCloud + " actualHealth=" + actualHealth
                );
            }
            if (persistentCloudThreat.damage().rawDamage().max() != 6f) {
                throw new AssertionError("dragon-breath cloud raw damage was not 6: " + persistentCloudThreat.damage());
            }
            if (!persistentCloudThreat.damage().has(DamageFlag.BYPASSES_ARMOR)
                || !persistentCloudThreat.damage().has(DamageFlag.BYPASSES_SHIELD)
                || persistentCloudThreat.blockable()) {
                throw new AssertionError("dragon-breath cloud mitigation semantics are wrong: " + persistentCloudThreat);
            }
            if (Math.abs(predictedHealth - actualHealth) > 0.0001f) {
                throw new AssertionError(
                    "persistent dragon-breath prediction did not match vanilla damage; predicted=" + predictedHealth
                        + " actual=" + actualHealth + " event=" + persistentCloudThreat
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

    private static CloudObservation observe(net.minecraft.server.MinecraftServer server) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        ServerLevel level = (ServerLevel) player.level();
        Entity cloud = level.getEntities(
            player,
            player.getBoundingBox().inflate(16d),
            entity -> entity.getType() == EntityType.AREA_EFFECT_CLOUD
        ).stream().findFirst().orElse(null);
        return cloud == null ? new CloudObservation(-1, -1) : new CloudObservation(cloud.getId(), cloud.tickCount);
    }

    private record Setup(int projectileId, int ownerId) {
    }

    private record CloudObservation(int entityId, int ageTicks) {
    }
}
