package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

final class InfiniteWitherValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private InfiniteWitherValidationScenarios() {
    }

    static void validateInfiniteWitherUsesBoundedPhase(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.getFoodData().setFoodLevel(17);
            player.getFoodData().setSaturation(0f);
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, -1, 0));
        });

        try {
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.hasEffect(MobEffects.WITHER)
                && minecraft.player.getEffect(MobEffects.WITHER).getDuration() == -1);

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SurvivalEngine.EngineFrame frame = context.computeOnClient(minecraft -> runtime.capture());
            var effect = frame.context().player().statusEffects().effect("minecraft:wither")
                .orElseThrow(() -> new AssertionError(
                    "infinite Wither was synchronized to client but missing from production status snapshot"
                ));
            if (effect.durationTicks() != -1) {
                throw new AssertionError("expected production to preserve infinite duration=-1, got " + effect);
            }

            ThreatEvent predicted = frame.timeline().events().stream()
                .filter(event -> event.id().equals("env:wither:infinite:0"))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "infinite Wither had no bounded future threat; status=" + frame.context().player().statusEffects()
                ));
            if (predicted.impact().earliest() != 1L || predicted.impact().latest() != 40L) {
                throw new AssertionError("infinite Wither I first cadence window must be [1,40], got " + predicted);
            }

            float baseline = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            int damageTick = -1;
            for (int tick = 1; tick <= 40; tick++) {
                context.waitTick();
                float health = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
                );
                if (health < baseline - EPSILON) {
                    damageTick = tick;
                    break;
                }
            }
            if (damageTick < 0 || !predicted.impact().contains(damageTick)) {
                throw new AssertionError(
                    "authoritative infinite Wither damage did not land inside predicted [1,40] phase window; "
                        + "damageTick=" + damageTick + " predicted=" + predicted
                );
            }
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
}
