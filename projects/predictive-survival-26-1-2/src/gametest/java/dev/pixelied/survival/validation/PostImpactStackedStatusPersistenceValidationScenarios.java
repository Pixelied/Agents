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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class PostImpactStackedStatusPersistenceValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private PostImpactStackedStatusPersistenceValidationScenarios() {
    }

    static void validateHiddenWitherTailSurvivesProjectileRemoval(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            player.getFoodData().setFoodLevel(17);
            player.getFoodData().setSaturation(0f);
            ServerLevel level = (ServerLevel) player.level();

            Creeper owner = new Creeper(EntityType.CREEPER, level);
            owner.setNoAi(true);
            owner.setPos(player.getX() + 12d, player.getY(), player.getZ() + 12d);
            level.addFreshEntity(owner);

            PotionContents contents = PotionContents.EMPTY
                .withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 240, 0))
                .withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 40, 1));
            ItemStack stack = new ItemStack(Items.SPLASH_POTION);
            stack.set(DataComponents.POTION_CONTENTS, contents);
            ThrownSplashPotion potion = new ThrownSplashPotion(level, owner, stack);
            potion.setPos(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            potion.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(potion);
            return new Setup(potion.getId(), owner.getId(), player.position());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) != null);

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame preImpact = context.computeOnClient(minecraft -> runtime.capture());
            List<ThreatEvent> preImpactTail = preImpact.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":stacked_status:wither:"))
                .toList();
            if (preImpactTail.isEmpty()) {
                throw new AssertionError("fixture had no pre-impact stacked Wither tail to preserve");
            }

            SurvivalEngine.EngineFrame postImpact = null;
            int postImpactTick = -1;
            Observation postImpactObservation = null;
            for (int tick = 1; tick <= 20; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                boolean clientProjectilePresent = frame.context().world().entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.projectileId())));

                if (!observation.projectilePresent()
                    && !clientProjectilePresent
                    && observation.witherAmplifier() == 1) {
                    postImpact = frame;
                    postImpactTick = tick;
                    postImpactObservation = observation;
                    break;
                }
            }

            if (postImpact == null) {
                throw new AssertionError("fixture never reached a client-observed post-impact Wither II frame");
            }

            List<ThreatEvent> postImpactWither = postImpact.timeline().events().stream()
                .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                .toList();
            long latestPredicted = postImpactWither.stream()
                .mapToLong(event -> event.impact().latest())
                .max()
                .orElse(-1L);

            float previousHealth = postImpactObservation.health();
            DamageSample lateHiddenDamage = null;
            int finalTick = Math.min(220, postImpactTick + 160);
            for (int tick = postImpactTick + 1; tick <= finalTick; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                if (observation.health() < previousHealth - EPSILON) {
                    long delay = tick - postImpactTick;
                    if (delay > latestPredicted) {
                        lateHiddenDamage = new DamageSample(tick, observation);
                        break;
                    }
                    previousHealth = observation.health();
                }
            }

            if (lateHiddenDamage == null) {
                throw new AssertionError(
                    "fixture produced no damaging hidden Wither pulse after the post-impact prediction deadline; "
                        + "postImpactTick=" + postImpactTick
                        + " latestPostImpactPrediction=" + latestPredicted
                        + " postImpactObservation=" + postImpactObservation
                        + " postImpactWither=" + postImpactWither
                );
            }

            long actualLateDelay = lateHiddenDamage.tick() - postImpactTick;
            if (latestPredicted < actualLateDelay) {
                throw new AssertionError(
                    "known hidden Wither tail was forgotten after splash projectile removal from the client snapshot; "
                        + "postImpactTick=" + postImpactTick
                        + " actualLateDelay=" + actualLateDelay
                        + " latestPostImpactPrediction=" + latestPredicted
                        + " postImpactObservation=" + postImpactObservation
                        + " lateHiddenDamage=" + lateHiddenDamage
                        + " postImpactWither=" + postImpactWither
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity projectile = player.level().getEntity(setup.projectileId());
                if (projectile != null) projectile.discard();
                Entity owner = player.level().getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
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
            wither == null ? -1 : wither.getAmplifier()
        );
    }

    private record Setup(int projectileId, int ownerId, Vec3 playerAnchor) {
    }

    private record Observation(
        float health,
        boolean projectilePresent,
        int witherDuration,
        int witherAmplifier
    ) {
    }

    private record DamageSample(int tick, Observation observation) {
    }
}
