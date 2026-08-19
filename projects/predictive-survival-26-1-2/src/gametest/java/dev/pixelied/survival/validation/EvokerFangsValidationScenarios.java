package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftSpecialThreatSnapshotAnnotator;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.EvokerFangsPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.phys.Vec3;

final class EvokerFangsValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float EPSILON = 0.0001f;

    private EvokerFangsValidationScenarios() {
    }

    static void validateVisibleFangsProducePreImpactThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            ArmorStand owner = new ArmorStand(level, player.getX() + 4d, player.getY(), player.getZ());
            owner.setNoGravity(true);
            level.addFreshEntity(owner);
            EvokerFangs fangs = new EvokerFangs(
                level, player.getX(), player.getY(), player.getZ(), 0f, 0, owner
            );
            level.addFreshEntity(fangs);
            return new Setup(owner.getId(), fangs.getId());
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.fangsId()) instanceof EvokerFangs fangs
                && fangs.getAnimationProgress(1f) > 0f);

            ThreatEvent predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Evoker Fang validation");
                }
                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot raw = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot world = new MinecraftSpecialThreatSnapshotAnnotator().annotate(
                    minecraft.level, minecraft.player, raw
                );
                PredictionContext predictionContext = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                return new EvokerFangsPredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().equals("evoker_fangs:" + setup.fangsId()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("visible started Evoker Fangs produced no threat"));
            });

            int actualDamageTick = waitForDamage(context, singleplayer);
            if (predicted.impact().earliest() > actualDamageTick || predicted.impact().latest() < actualDamageTick) {
                throw new AssertionError(
                    "Evoker Fang window missed real damage tick; predicted=" + predicted.impact()
                        + " actual=" + actualDamageTick
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity fangs = player.level().getEntity(setup.fangsId());
                if (fangs != null) fangs.discard();
                Entity owner = player.level().getEntity(setup.ownerId());
                if (owner != null) owner.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static int waitForDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (int tick = 1; tick <= 20; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < 20f - EPSILON) return tick;
        }
        throw new AssertionError("Evoker Fangs did not damage the player within 20 ticks");
    }

    private record Setup(int ownerId, int fangsId) {
    }
}
