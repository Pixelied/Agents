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

final class LingeringPotionCloudHandoffValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private LingeringPotionCloudHandoffValidationScenarios() {
    }

    static void validateLiveCloudRetainsHarmingThreat(
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
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.INSTANT_DAMAGE, 1, 1))
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
            ThreatEvent forecast = initial.timeline().events().stream()
                .filter(event -> event.id().equals("projectile:" + setup.projectileId() + ":lingering_cloud:0"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("lingering Harming had no pre-impact cloud forecast"));
            assertHarmingSix("pre-impact lingering forecast", forecast);

            ThreatEvent persistent = null;
            float actualHealth = 20f;
            boolean projectileGone = false;
            int projectileGoneTick = -1;
            int persistentThreatTick = -1;
            int damageTick = -1;

            for (int tick = 1; tick <= 40; tick++) {
                anchorPlayer(singleplayer, setup.playerAnchor());
                context.waitTick();

                Observation observation = singleplayer.getServer().computeOnServer(server -> observe(server, setup.projectileId()));
                actualHealth = observation.health();
                if (!observation.projectilePresent() && !projectileGone) {
                    projectileGone = true;
                    projectileGoneTick = tick;
                }

                if (projectileGone && actualHealth >= 20f && persistent == null) {
                    SurvivalEngine.EngineFrame cloudFrame = context.computeOnClient(minecraft -> runtime.capture());
                    persistent = cloudFrame.timeline().events().stream()
                        .filter(event -> event.id().startsWith("env:area_effect_cloud:"))
                        .filter(event -> "minecraft:indirect_magic".equals(event.damage().sourceKey()))
                        .filter(event -> event.damage().rawDamage().max() >= 6f - EPSILON)
                        .findFirst()
                        .orElse(null);
                    if (persistent != null) persistentThreatTick = tick;
                }

                if (actualHealth < 20f) {
                    damageTick = tick;
                    break;
                }
            }

            if (!projectileGone) {
                throw new AssertionError("lingering Harming projectile never disappeared");
            }
            if (persistent == null) {
                throw new AssertionError(
                    "lingering Harming cloud never retained a production threat after projectile disappearance; "
                        + "projectileGoneTick=" + projectileGoneTick
                        + " damageTick=" + damageTick
                        + " actualHealth=" + actualHealth
                        + " " + cloudDiagnostics(singleplayer)
                );
            }
            assertHarmingSix("persistent lingering cloud", persistent);
            if (persistentThreatTick >= damageTick && damageTick >= 0) {
                throw new AssertionError(
                    "persistent cloud threat was not visible before vanilla damage; persistentThreatTick="
                        + persistentThreatTick + " damageTick=" + damageTick
                );
            }
            if (Math.abs(actualHealth - 14f) > EPSILON) {
                throw new AssertionError(
                    "lingering Harming vanilla damage changed; expected health=14.0 actual=" + actualHealth
                        + " projectileGoneTick=" + projectileGoneTick
                        + " persistentThreatTick=" + persistentThreatTick
                        + " damageTick=" + damageTick
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
                    AreaEffectCloud.class, player.getBoundingBox().inflate(16d)
                )) {
                    cloud.discard();
                }
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static void anchorPlayer(TestSingleplayerContext singleplayer, Vec3 anchor) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            player.setPos(anchor.x, anchor.y, anchor.z);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static Observation observe(net.minecraft.server.MinecraftServer server, int projectileId) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        return new Observation(player.getHealth(), player.level().getEntity(projectileId) != null);
    }

    private static void assertHarmingSix(String label, ThreatEvent event) {
        if (Math.abs(event.damage().rawDamage().min() - 6f) > EPSILON
            || Math.abs(event.damage().rawDamage().max() - 6f) > EPSILON) {
            throw new AssertionError(label + " expected 6 raw damage, got " + event.damage().rawDamage());
        }
        if (!"minecraft:indirect_magic".equals(event.damage().sourceKey())) {
            throw new AssertionError(label + " expected indirect_magic, got " + event.damage().sourceKey());
        }
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

    private record Observation(float health, boolean projectilePresent) {
    }
}
