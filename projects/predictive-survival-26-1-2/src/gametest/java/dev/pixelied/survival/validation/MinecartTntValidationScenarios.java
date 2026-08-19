package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.Vec3;

final class MinecartTntValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final float MAX_OPAQUE_EXPLOSION_RADIUS = 1088f;

    private MinecartTntValidationScenarios() {
    }

    static void validatePrimedMinecartProducesBoundedExplosionThreat(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            MinecartTNT minecart = new MinecartTNT(EntityType.TNT_MINECART, level);
            Vec3 spawn = player.position().add(0d, 0.5d, 5d);
            minecart.setPos(spawn.x, spawn.y, spawn.z);
            minecart.setNoGravity(true);
            minecart.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(minecart);
            return minecart.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof MinecartTNT);

            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
                if (!(entity instanceof MinecartTNT minecart)) {
                    throw new AssertionError("server TNT minecart disappeared before priming");
                }
                minecart.primeFuse(null);
            });

            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof MinecartTNT minecart
                && minecart.isPrimed());

            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for TNT minecart validation");
                }

                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot.EntitySnapshot snapshot = world.entities().stream()
                    .filter(entity -> entity.id().equals(Integer.toString(entityId)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("primed TNT minecart missing from world snapshot"));

                int fuseMin = parseInt(snapshot, "fuse_ticks_min");
                int fuseMax = parseInt(snapshot, "fuse_ticks_max");
                float radiusMin = parseFloat(snapshot, "explosion_radius_min");
                float radiusMax = parseFloat(snapshot, "explosion_radius_max");
                if (fuseMin != 0 || fuseMax <= 0 || fuseMax > 80) {
                    throw new AssertionError("unsafe TNT minecart fuse bounds: " + snapshot.properties());
                }
                if (Math.abs(radiusMin - 4f) > 0.0001f || radiusMax < MAX_OPAQUE_EXPLOSION_RADIUS) {
                    throw new AssertionError(
                        "TNT minecart client cannot observe hidden explosion NBT, so radius max must cover the full "
                            + "vanilla-legal bound " + MAX_OPAQUE_EXPLOSION_RADIUS + ": " + snapshot.properties()
                    );
                }
                if (!Boolean.parseBoolean(snapshot.properties().getOrDefault("scales_with_difficulty", "false"))) {
                    throw new AssertionError("TNT minecart snapshot did not expose difficulty scaling");
                }

                PredictionContext predictionContext = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                ThreatEvent event = new ExplosionPredictor().predict(predictionContext).stream()
                    .filter(candidate -> candidate.id().equals("explosion:" + entityId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("primed TNT minecart produced no explosion threat"));
                if (event.confidence() != Confidence.BOUNDED) {
                    throw new AssertionError("TNT minecart threat must remain bounded, got " + event.confidence());
                }
                if (event.impact().earliest() != 0 || event.impact().latest() != fuseMax) {
                    throw new AssertionError(
                        "TNT minecart impact window did not preserve conservative fuse bounds: " + event.impact()
                    );
                }
                if (event.damage().rawDamage().max() <= 0f || !event.damage().scalesWithDifficulty()) {
                    throw new AssertionError("TNT minecart explosion threat did not carry damaging vanilla semantics");
                }
            });
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = ((ServerLevel) player.level()).getEntity(entityId);
                if (entity != null) entity.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
            });
            context.waitTick();
        }
    }

    private static int parseInt(WorldSnapshot.EntitySnapshot snapshot, String key) {
        String value = snapshot.properties().get(key);
        if (value == null) throw new AssertionError("TNT minecart snapshot missing " + key + ": " + snapshot.properties());
        return Integer.parseInt(value);
    }

    private static float parseFloat(WorldSnapshot.EntitySnapshot snapshot, String key) {
        String value = snapshot.properties().get(key);
        if (value == null) throw new AssertionError("TNT minecart snapshot missing " + key + ": " + snapshot.properties());
        return Float.parseFloat(value);
    }
}
