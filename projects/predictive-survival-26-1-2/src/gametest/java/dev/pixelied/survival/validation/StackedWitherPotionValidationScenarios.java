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

import java.util.ArrayList;
import java.util.List;

final class StackedWitherPotionValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private StackedWitherPotionValidationScenarios() {
    }

    static void validateHiddenWitherTailIsPredictedBeforeImpact(
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
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            List<ThreatEvent> predictedWither = frame.timeline().events().stream()
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":"))
                .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                .toList();
            long latestPredicted = predictedWither.stream()
                .mapToLong(event -> event.impact().latest())
                .max()
                .orElse(-1L);
            String snapshot = frame.context().world().entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(setup.projectileId())))
                .map(entity -> entity.properties().toString())
                .findFirst()
                .orElse("<projectile missing>");

            int strongSeenTick = -1;
            int downgradeTick = -1;
            float previousHealth = 20f;
            List<DamageSample> damageSamples = new ArrayList<>();
            DamageSample firstTailDamage = null;
            for (int tick = 1; tick <= 120; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                if (observation.witherAmplifier() == 1 && strongSeenTick < 0) strongSeenTick = tick;
                if (strongSeenTick >= 0 && observation.witherAmplifier() == 0 && downgradeTick < 0) {
                    downgradeTick = tick;
                }
                if (observation.health() < previousHealth - EPSILON) {
                    DamageSample sample = new DamageSample(tick, observation);
                    damageSamples.add(sample);
                    if (downgradeTick >= 0 && tick > downgradeTick && firstTailDamage == null) firstTailDamage = sample;
                    previousHealth = observation.health();
                }
                if (firstTailDamage != null) break;
            }

            if (strongSeenTick < 0 || downgradeTick < 0 || firstTailDamage == null) {
                throw new AssertionError(
                    "stacked Wither fixture did not demonstrate vanilla hidden-effect resume; strongSeenTick=" + strongSeenTick
                        + " downgradeTick=" + downgradeTick + " firstTailDamage=" + firstTailDamage
                        + " damageSamples=" + damageSamples + " snapshot=" + snapshot
                );
            }
            if (latestPredicted < firstTailDamage.tick()) {
                throw new AssertionError(
                    "vanilla hidden Wither tail damaged after production's last pre-impact Wither threat; "
                        + "latestPredicted=" + latestPredicted + " firstTailDamage=" + firstTailDamage
                        + " downgradeTick=" + downgradeTick + " damageSamples=" + damageSamples
                        + " predicted=" + predictedWither + " snapshot=" + snapshot
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
            wither == null ? -1 : wither.getAmplifier(),
            String.valueOf(player.getLastDamageSource())
        );
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
