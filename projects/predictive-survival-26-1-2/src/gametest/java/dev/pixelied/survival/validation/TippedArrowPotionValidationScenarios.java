package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

final class TippedArrowPotionValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private TippedArrowPotionValidationScenarios() {
    }

    static void validateTippedArrowWitherHasPreImpactThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int projectileId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            ItemStack tippedArrow = new ItemStack(Items.TIPPED_ARROW);
            tippedArrow.set(
                DataComponents.POTION_CONTENTS,
                PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.WITHER, 800, 0))
            );
            Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 6d);
            Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, tippedArrow, null);
            arrow.setDeltaMovement(0d, 0d, -1.5d);
            level.addFreshEntity(arrow);
            return arrow.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(projectileId) instanceof Arrow);
            context.waitTick();

            ClientObservation client = context.computeOnClient(minecraft -> {
                Arrow arrow = (Arrow) minecraft.level.getEntity(projectileId);
                if (arrow == null) throw new AssertionError("tipped arrow disappeared before client observation");

                ItemStack pickup = arrow.getPickupItemStackOrigin();
                PotionContents clientContents = pickup.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
                float durationScale = pickup.getOrDefault(DataComponents.POTION_DURATION_SCALE, 1.0f);
                SurvivalEngine.EngineFrame frame = new MinecraftSurvivalRuntime(minecraft).capture();
                WorldSnapshot.EntitySnapshot snapshot = frame.context().world().entities().stream()
                    .filter(entity -> entity.id().equals(Integer.toString(projectileId)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("tipped arrow missing from production snapshot"));
                ThreatEvent witherThreat = frame.timeline().events().stream()
                    .filter(event -> event.id().startsWith("projectile:" + projectileId + ":"))
                    .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                    .findFirst()
                    .orElse(null);
                return new ClientObservation(
                    witherThreat != null,
                    arrow.getColor(),
                    BuiltInRegistries.ITEM.getKey(pickup.getItem()).toString(),
                    clientContents.hasEffects(),
                    durationScale,
                    snapshot.properties().toString()
                );
            });

            ServerObservation firstWither = null;
            DamageObservation witherDamage = null;
            float healthAtEffect = Float.NaN;
            for (int tick = 1; tick <= 80; tick++) {
                context.waitTick();
                ServerObservation observation = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
                    return new ServerObservation(
                        player.getHealth(),
                        wither == null ? -1 : wither.getDuration(),
                        wither == null ? -1 : wither.getAmplifier(),
                        String.valueOf(player.getLastDamageSource())
                    );
                });
                if (observation.witherDuration() >= 0 && firstWither == null) {
                    firstWither = observation;
                    healthAtEffect = observation.health();
                }
                if (firstWither != null && observation.health() < healthAtEffect - EPSILON) {
                    witherDamage = new DamageObservation(tick, observation);
                    break;
                }
            }

            if (firstWither == null || witherDamage == null) {
                throw new AssertionError(
                    "tipped-arrow Wither fixture did not produce a harmful vanilla status; firstWither=" + firstWither
                        + " witherDamage=" + witherDamage + " client=" + client
                );
            }
            if (!client.predictedWither()) {
                throw new AssertionError(
                    "tipped arrow applied damaging vanilla Wither but production emitted no pre-impact Wither threat; "
                        + "firstWither=" + firstWither + " witherDamage=" + witherDamage + " client=" + client
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity projectile = player.level().getEntity(projectileId);
                if (projectile != null) projectile.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitTick();
        }
    }

    private record ClientObservation(
        boolean predictedWither,
        int potionColor,
        String pickupItem,
        boolean clientPickupHasEffects,
        float durationScale,
        String snapshotProperties
    ) {
    }

    private record ServerObservation(
        float health,
        int witherDuration,
        int witherAmplifier,
        String lastDamageSource
    ) {
    }

    private record DamageObservation(int tick, ServerObservation observation) {
    }
}
