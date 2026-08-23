package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class HiddenStatusSnapshotValidationScenarios {
    private static final float EPSILON = 0.0001f;

    private HiddenStatusSnapshotValidationScenarios() {
    }

    static void validateDirectHiddenWitherTailSurvivesVisibleExpiry(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Vec3 anchor = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            player.getFoodData().setFoodLevel(17);
            player.getFoodData().setSaturation(0f);

            // Vanilla stores the weaker, longer Wither I as a hidden effect when the shorter,
            // stronger Wither II takes over. ClientboundUpdateMobEffectPacket does not serialize
            // that hidden chain, so production must fail closed until the promotion update arrives.
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 240, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1));
            return player.position();
        });

        try {
            context.waitFor(minecraft -> {
                if (minecraft.player == null) return false;
                MobEffectInstance wither = minecraft.player.getEffect(MobEffects.WITHER);
                return wither != null && wither.getAmplifier() == 1 && wither.getDuration() > 0;
            });

            MinecraftSurvivalRuntime runtime = context.computeOnClient(MinecraftSurvivalRuntime::new);
            SnapshotPrediction prediction = context.computeOnClient(minecraft -> {
                var frame = runtime.capture();
                EffectInstanceSnapshot visible = frame.context().player().statusEffects()
                    .effect("minecraft:wither")
                    .orElseThrow(() -> new AssertionError("client snapshot lost active Wither II"));
                if (visible.amplifier() != 1) {
                    throw new AssertionError("expected visible Wither II, got " + visible);
                }

                List<ThreatEvent> predictedWither = frame.timeline().events().stream()
                    .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
                    .toList();
                long latest = predictedWither.stream()
                    .mapToLong(event -> event.impact().latest())
                    .max()
                    .orElse(-1L);
                return new SnapshotPrediction(
                    visible.durationTicks(),
                    visible.hiddenTailUnknown(),
                    latest,
                    predictedWither
                );
            });

            float previousHealth = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            int downgradeTick = -1;
            int hiddenDamageCount = 0;
            DamageSample secondHiddenDamage = null;
            for (int tick = 1; tick <= 100; tick++) {
                anchor(singleplayer, anchor);
                context.waitTick();
                Observation observation = singleplayer.getServer().computeOnServer(HiddenStatusSnapshotValidationScenarios::observe);
                if (downgradeTick < 0 && observation.amplifier() == 0) {
                    downgradeTick = tick;
                }
                if (observation.health() < previousHealth - EPSILON) {
                    if (downgradeTick >= 0) {
                        hiddenDamageCount++;
                        if (hiddenDamageCount == 2) {
                            secondHiddenDamage = new DamageSample(tick, observation);
                            break;
                        }
                    }
                    previousHealth = observation.health();
                }
            }

            if (downgradeTick < 0 || secondHiddenDamage == null) {
                throw new AssertionError(
                    "direct stacked Wither fixture did not produce the second hidden-tail pulse; "
                        + "downgradeTick=" + downgradeTick
                        + " hiddenDamageCount=" + hiddenDamageCount
                        + " secondHiddenDamage=" + secondHiddenDamage
                        + " prediction=" + prediction
                );
            }
            if (secondHiddenDamage.tick() > 80) {
                throw new AssertionError(
                    "fixture second hidden Wither pulse escaped the configured 80-tick horizon: "
                        + secondHiddenDamage
                );
            }
            if (prediction.latestPredictedTick() < secondHiddenDamage.tick()) {
                throw new AssertionError(
                    "unobservable hidden Wither tail was not conservatively covered; "
                        + "prediction=" + prediction
                        + " downgradeTick=" + downgradeTick
                        + " secondHiddenDamage=" + secondHiddenDamage
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
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

    private static Observation observe(net.minecraft.server.MinecraftServer server) {
        ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
        MobEffectInstance wither = player.getEffect(MobEffects.WITHER);
        return new Observation(
            player.getHealth(),
            wither == null ? -1 : wither.getDuration(),
            wither == null ? -1 : wither.getAmplifier()
        );
    }

    private record SnapshotPrediction(
        int visibleDuration,
        boolean hiddenTailUnknown,
        long latestPredictedTick,
        List<ThreatEvent> events
    ) {
    }

    private record Observation(float health, int duration, int amplifier) {
    }

    private record DamageSample(int tick, Observation observation) {
    }
}
