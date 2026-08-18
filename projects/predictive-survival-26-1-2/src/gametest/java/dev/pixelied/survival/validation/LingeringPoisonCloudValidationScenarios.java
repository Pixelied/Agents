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

final class LingeringPoisonCloudValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private LingeringPoisonCloudValidationScenarios() {
    }

    static void validateLingeringPoisonRetainsThreatAcrossCloudHandoff(
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

            ItemStack stack = new ItemStack(Items.LINGERING_POTION);
            stack.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.POISON, 100, 0))
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
                .filter(event -> event.id().startsWith("projectile:" + setup.projectileId() + ":lingering_status:poison:"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("lingering Poison had no pre-impact status threat"));
            assertPoison("pre-impact lingering Poison", preImpact);

            int projectileGoneTick = -1;
            int firstPoisonTick = -1;
            int firstDamageTick = -1;
            ThreatEvent persistent = null;
            int persistentThreatTick = -1;

            for (int tick = 1; tick <= 50; tick++) {
                anchor(singleplayer, setup.playerAnchor());
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));

                if (!observation.projectilePresent() && projectileGoneTick < 0) projectileGoneTick = tick;
                if (observation.poisonDuration() >= 0 && firstPoisonTick < 0) firstPoisonTick = tick;

                if (projectileGoneTick >= 0 && firstPoisonTick < 0 && persistent == null) {
                    SurvivalEngine.EngineFrame cloudFrame = context.computeOnClient(minecraft -> runtime.capture());
                    persistent = cloudFrame.timeline().events().stream()
                        .filter(event -> event.id().startsWith("env:area_effect_cloud:"))
                        .filter(event -> "minecraft:magic".equals(event.damage().sourceKey()))
                        .findFirst()
                        .orElse(null);
                    if (persistent != null) persistentThreatTick = tick;
                }

                if (observation.health() < 20f - EPSILON) {
                    firstDamageTick = tick;
                    break;
                }
            }

            if (firstPoisonTick < 0 || firstDamageTick < 0) {
                throw new AssertionError(
                    "lingering Poison did not apply/damage as expected; projectileGoneTick=" + projectileGoneTick
                        + " firstPoisonTick=" + firstPoisonTick + " firstDamageTick=" + firstDamageTick
                );
            }
            if (persistent == null) {
                throw new AssertionError(
                    "lingering Poison lost its threat after projectile disappearance and before effect application; "
                        + "projectileGoneTick=" + projectileGoneTick
                        + " firstPoisonTick=" + firstPoisonTick
                        + " firstDamageTick=" + firstDamageTick
                );
            }
            assertPoison("persistent lingering Poison cloud", persistent);
            if (persistentThreatTick >= firstPoisonTick) {
                throw new AssertionError(
                    "lingering Poison cloud threat was not visible before effect application; persistentThreatTick="
                        + persistentThreatTick + " firstPoisonTick=" + firstPoisonTick
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
                for (AreaEffectCloud cloud : level.getEntitiesOfClass(AreaEffectCloud.class, player.getBoundingBox().inflate(16d))) {
                    cloud.discard();
                }
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static void assertPoison(String label, ThreatEvent event) {
        if (Math.abs(event.damage().rawDamage().max() - 1f) > EPSILON) {
            throw new AssertionError(label + " expected 1 raw damage, got " + event.damage().rawDamage());
        }
        if (Math.abs(event.damage().applicationHealthThresholdExclusive() - 1f) > EPSILON) {
            throw new AssertionError(label + " expected 1 HP floor, got " + event.damage().applicationHealthThresholdExclusive());
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
        MobEffectInstance poison = player.getEffect(MobEffects.POISON);
        return new Observation(
            player.getHealth(),
            player.level().getEntity(projectileId) != null,
            poison == null ? -1 : poison.getDuration()
        );
    }

    private record Setup(int projectileId, int ownerId, Vec3 playerAnchor) {
    }

    private record Observation(float health, boolean projectilePresent, int poisonDuration) {
    }
}
