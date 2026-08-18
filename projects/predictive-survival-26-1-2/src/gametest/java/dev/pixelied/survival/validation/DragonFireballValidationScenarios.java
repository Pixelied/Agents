package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.threat.EnvironmentPredictorRegistry;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;

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

            ImpactObservation impact = null;
            CloudObservation firstCloud = null;
            Boolean cloudPredictedThreat = null;
            for (int tick = 1; tick <= 40; tick++) {
                context.waitTick();
                ImpactObservation observation = singleplayer.getServer().computeOnServer(server ->
                    observe(server, setup.projectileId())
                );
                if (firstCloud == null && observation.cloud() != null) {
                    firstCloud = observation.cloud();
                    CloudObservation capturedCloud = firstCloud;
                    context.waitFor(minecraft ->
                        minecraft.level != null && minecraft.level.getEntity(capturedCloud.entityId()) != null
                    );
                    cloudPredictedThreat = context.computeOnClient(minecraft -> {
                        if (minecraft.player == null || minecraft.level == null) {
                            throw new AssertionError("client player/level unavailable for area-cloud validation");
                        }
                        PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                        PredictionContext predictionContext = new PredictionContext(
                            player,
                            new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS),
                            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                            LIMITS
                        );
                        return EnvironmentPredictorRegistry.defaults().predict(predictionContext).stream()
                            .anyMatch(event -> event.id().startsWith(
                                "env:area_effect_cloud:" + capturedCloud.entityId() + ":"
                            ));
                    });
                }
                if (observation.health() < 20f) {
                    impact = new ImpactObservation(
                        observation.health(),
                        observation.projectilePresent(),
                        observation.cloud(),
                        tick
                    );
                    break;
                }
            }

            if (impact == null) {
                throw new AssertionError(
                    "dragon-fireball fixture produced no server damage within 40 ticks; firstCloud=" + firstCloud
                );
            }
            if (!predictedThreat) {
                throw new AssertionError(
                    "live dragon fireball caused server damage but the pre-impact production predictor emitted no threat; "
                        + "actualHealth=" + impact.health()
                        + " damageTick=" + impact.tick()
                        + " projectilePresent=" + impact.projectilePresent()
                        + " firstCloud=" + firstCloud
                        + " impactCloud=" + impact.cloud()
                );
            }
            if (!Boolean.TRUE.equals(cloudPredictedThreat)) {
                throw new AssertionError(
                    "live dragon-breath area effect cloud caused server damage but the production environment registry "
                        + "emitted no cloud threat; actualHealth=" + impact.health()
                        + " damageTick=" + impact.tick()
                        + " firstCloud=" + firstCloud
                        + " impactCloud=" + impact.cloud()
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

    private static ImpactObservation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        ServerLevel level = (ServerLevel) player.level();
        Entity cloud = level.getEntities(
            player,
            player.getBoundingBox().inflate(16d),
            entity -> entity.getType() == EntityType.AREA_EFFECT_CLOUD
        ).stream().findFirst().orElse(null);
        CloudObservation cloudObservation = cloud == null ? null : cloud(cloud, player);
        return new ImpactObservation(
            player.getHealth(),
            level.getEntity(projectileId) != null,
            cloudObservation,
            -1
        );
    }

    private static CloudObservation cloud(Entity cloud, ServerPlayer player) {
        AABB box = cloud.getBoundingBox();
        return new CloudObservation(
            cloud.getId(),
            new Vec3Snapshot(cloud.getX(), cloud.getY(), cloud.getZ()),
            box.maxX - box.minX,
            box.maxY - box.minY,
            box.maxZ - box.minZ,
            Math.sqrt(player.distanceToSqr(cloud)),
            cloud.tickCount,
            cloudApi(cloud)
        );
    }

    private static String cloudApi(Entity cloud) {
        return Arrays.stream(cloud.getClass().getMethods())
            .filter(method -> {
                String name = method.getName().toLowerCase();
                return name.contains("potion")
                    || name.contains("effect")
                    || name.contains("radius")
                    || name.contains("wait")
                    || name.contains("duration")
                    || name.contains("reapplication")
                    || name.contains("owner");
            })
            .map(DragonFireballValidationScenarios::methodSignature)
            .sorted()
            .distinct()
            .collect(Collectors.joining(","));
    }

    private static String methodSignature(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes())
            .map(Class::getSimpleName)
            .collect(Collectors.joining(";")) + "):" + method.getReturnType().getSimpleName();
    }

    private record Setup(int projectileId, int ownerId) {
    }

    private record ImpactObservation(
        float health,
        boolean projectilePresent,
        CloudObservation cloud,
        int tick
    ) {
    }

    private record CloudObservation(
        int entityId,
        Vec3Snapshot position,
        double width,
        double height,
        double depth,
        double playerDistance,
        int ageTicks,
        String api
    ) {
    }
}
