package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class LingeringWitherCloudValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private LingeringWitherCloudValidationScenarios() {
    }

    static void validateLingeringWitherRetainsThreatAcrossCloudHandoff(
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

            ItemStack stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 100, 0))
            );
            ThrownLingeringPotion potion = new ThrownLingeringPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 5d);
            potion.setDeltaMovement(0d, 0d, -1.0d);
            level.addFreshEntity(potion);

            return new Setup(potion.getId(), owner.getId(), player.position());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) != null);

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame initial = context.computeOnClient(minecraft -> runtime.capture());
            ThreatEvent preImpact = initial.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                .findFirst()
                .orElse(null);
            String snapshot = initial.context().world().entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                .map(entity -> "type=" + entity.typeKey()
                    + " position=" + entity.position()
                    + " velocity=" + entity.velocity()
                    + " properties=" + entity.properties())
                .findFirst()
                .orElse("<projectile missing from snapshot>");

            int projectileGoneTick = -1;
            int firstWitherTick = -1;
            int persistentThreatTick = -1;
            Observation firstWither = null;
            ThreatEvent persistent = null;
            float previousHealth = 20f;
            List<DamageSample> damageSamples = new ArrayList<>();

            for (int tick = 1; tick <= 100; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));

                if (!observation.projectilePresent() && projectileGoneTick < 0) projectileGoneTick = tick;
                if (observation.witherDuration() >= 0 && firstWither == null) {
                    firstWither = observation;
                    firstWitherTick = tick;
                }
                if (observation.health() < previousHealth - EPSILON) {
                    damageSamples.add(new DamageSample(tick, observation));
                    previousHealth = observation.health();
                }

                if (projectileGoneTick >= 0 && firstWither == null && persistent == null) {
                    SurvivalEngine.EngineFrame cloudFrame = context.computeOnClient(minecraft -> runtime.capture());
                    persistent = cloudFrame.timeline().events().stream()
                        .filter(event -> event.id().startsWith("env:area_effect_cloud:"))
                        .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                        .findFirst()
                        .orElse(null);
                    if (persistent != null) persistentThreatTick = tick;
                }
            }

            if (firstWither == null) {
                throw new AssertionError(
                    "lingering Wither fixture never applied Wither; snapshot=" + snapshot
                        + " projectileGoneTick=" + projectileGoneTick
                        + " " + cloudDiagnostics(singleplayer)
                );
            }
            if (damageSamples.isEmpty()) {
                throw new AssertionError(
                    "lingering Wither applied an effect but caused no vanilla damage; snapshot=" + snapshot
                        + " projectileGoneTick=" + projectileGoneTick
                        + " firstWitherTick=" + firstWitherTick
                        + " firstWither=" + firstWither
                );
            }
            if (preImpact == null) {
                throw new AssertionError(
                    "live lingering Wither caused vanilla damage but production emitted no pre-impact Wither threat; "
                        + "snapshot=" + snapshot
                        + " projectileGoneTick=" + projectileGoneTick
                        + " firstWitherTick=" + firstWitherTick
                        + " firstWither=" + firstWither
                        + " damageSamples=" + damageSamples
                        + " persistentThreatTick=" + persistentThreatTick
                        + " persistent=" + persistent
                );
            }
            if (persistent == null) {
                throw new AssertionError(
                    "lingering Wither lost its threat after projectile disappearance and before effect application; "
                        + "preImpact=" + preImpact
                        + " projectileGoneTick=" + projectileGoneTick
                        + " firstWitherTick=" + firstWitherTick
                        + " firstWither=" + firstWither
                        + " damageSamples=" + damageSamples
                        + " " + cloudDiagnostics(singleplayer)
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
                for (AreaEffectCloud cloud : level.getEntitiesOfClass(
                    AreaEffectCloud.class,
                    player.getBoundingBox().inflate(16d)
                )) {
                    cloud.discard();
                }
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static void anchor(TestSingleplayerContext singleplayer, Vec3 anchor) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
        return new Observation(
            player.getHealth(),
            player.level().getEntity(projectileId) != null,
            wither == null ? -1 : wither.getDuration(),
            wither == null ? -1 : wither.getAmplifier(),
            String.valueOf(player.getLastDamageSource())
        );
    }

    private static String cloudDiagnostics(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            return "player=" + player.position() + " clouds=" + level.getEntitiesOfClass(
                AreaEffectCloud.class,
                player.getBoundingBox().inflate(16d)
            ).stream().map(cloud -> "id=" + cloud.getId()
                + " pos=" + cloud.position()
                + " radius=" + cloud.getRadius()
                + " waiting=" + cloud.isWaiting())
                .toList();
        });
    }

    private record Setup(int projectileId, int ownerId, Vec3 playerAnchor) {
    }

    private record Observation(
        float health,
        boolean projectilePresent,
        int witherDuration,
        int witherAmplifier,
        String lastDamageSource
    ) {
    }

    private record DamageSample(int tick, Observation observation) {
    }
}
