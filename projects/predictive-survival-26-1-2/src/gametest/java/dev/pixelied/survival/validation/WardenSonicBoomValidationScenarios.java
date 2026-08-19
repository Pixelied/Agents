package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftSpecialThreatSnapshotAnnotator;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.WardenSonicBoomPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;

final class WardenSonicBoomValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float EPSILON = 0.0001f;

    private WardenSonicBoomValidationScenarios() {
    }

    static void validateObservedChargeProducesSonicThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int wardenId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Warden warden = new Warden(EntityType.WARDEN, level);
            warden.setPos(player.getX(), player.getY(), player.getZ() + 10d);
            warden.setNoGravity(true);
            warden.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(warden);
            return warden.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(wardenId) instanceof Warden);

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(wardenId);
                if (!(entity instanceof Warden warden)) {
                    throw new AssertionError("server Warden disappeared before sonic event");
                }
                ((ServerLevel) player.level()).broadcastEntityEvent(warden, (byte) 62);
            });

            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(wardenId) instanceof Warden warden
                && warden.sonicBoomAnimationState.isStarted());

            ThreatEvent predicted = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for Warden sonic validation");
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
                return new WardenSonicBoomPredictor().predict(predictionContext).stream()
                    .filter(event -> event.id().equals("warden_sonic:" + wardenId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("client-observed Warden sonic charge produced no threat"));
            });

            if (predicted.impact().latest() <= 0 || predicted.impact().latest() > 34) {
                throw new AssertionError("unsafe Warden sonic impact window: " + predicted.impact());
            }

            float actualHealth = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(wardenId);
                if (!(entity instanceof Warden warden)) {
                    throw new AssertionError("server Warden disappeared before sonic damage validation");
                }
                player.hurtServer((ServerLevel) player.level(), player.damageSources().sonicBoom(warden), 10f);
                return player.getHealth();
            });
            if (Math.abs(actualHealth - 10f) > EPSILON) {
                throw new AssertionError("vanilla sonic_boom 10 raw damage did not produce expected clean-player health: " + actualHealth);
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity warden = player.level().getEntity(wardenId);
                if (warden != null) warden.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }
}
