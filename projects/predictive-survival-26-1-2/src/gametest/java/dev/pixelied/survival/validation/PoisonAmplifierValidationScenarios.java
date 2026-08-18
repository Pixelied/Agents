package dev.pixelied.survival.validation;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;

final class PoisonAmplifierValidationScenarios {
    private PoisonAmplifierValidationScenarios() {
    }

    static void diagnosePoisonTwoInterval(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getFoodData().setFoodLevel(17);
            player.getFoodData().setSaturation(0f);
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1));
        });

        try {
            float previousHealth = 20f;
            List<Sample> samples = new ArrayList<>();
            for (int tick = 1; tick <= 65; tick++) {
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    MobEffectInstance poison = player.getEffect(MobEffects.POISON);
                    return new Observation(
                        player.getHealth(),
                        poison == null ? -1 : poison.getDuration(),
                        poison == null ? -1 : poison.getAmplifier(),
                        String.valueOf(player.getLastDamageSource())
                    );
                });
                if (observation.health() < previousHealth) {
                    samples.add(new Sample(tick, observation));
                    previousHealth = observation.health();
                }
            }
            throw new AssertionError("Poison II diagnostic samples=" + samples);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getFoodData().setFoodLevel(20);
                player.getFoodData().setSaturation(5f);
            });
            context.waitTick();
        }
    }

    private record Observation(float health, int duration, int amplifier, String source) {
    }

    private record Sample(int tick, Observation observation) {
    }
}
