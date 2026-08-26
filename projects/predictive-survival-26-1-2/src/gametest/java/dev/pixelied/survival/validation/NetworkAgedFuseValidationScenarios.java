package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;

/** Exact-runtime proof that synchronized fuse observations are aged before deadline planning. */
final class NetworkAgedFuseValidationScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final int SERVER_FUSE_SEED = 16;
    private static final int DELIBERATE_STALE_TICKS = 3;
    private static final int MAX_REASONABLE_AGE_TICKS = 12;

    private NetworkAgedFuseValidationScenarios() {
    }

    static void validateDelayedTntObservationContainsAuthoritativeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0d;
            ServerLevel level = (ServerLevel)player.level();
            Vec3 spawn = player.position().add(0d, 0.9d, 6.5d);
            PrimedTnt tnt = new PrimedTnt(level, spawn.x, spawn.y, spawn.z, null);
            tnt.setNoGravity(true);
            tnt.setDeltaMovement(Vec3.ZERO);
            tnt.setFuse(SERVER_FUSE_SEED);
            level.addFreshEntity(tnt);
            return tnt.getId();
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof PrimedTnt);

            int seedFuse = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = ((ServerLevel)player.level()).getEntity(entityId);
                if (!(entity instanceof PrimedTnt tnt)) {
                    throw new AssertionError("server TNT disappeared before delayed-observation setup");
                }
                tnt.setFuse(SERVER_FUSE_SEED);
                return tnt.getFuse();
            });

            ObservedCapture observed = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for delayed TNT observation");
                }
                Entity entity = minecraft.level.getEntity(entityId);
                if (!(entity instanceof PrimedTnt tnt)) {
                    throw new AssertionError("client TNT disappeared before delayed-observation capture");
                }

                // Simulate a real synchronized metadata sample that is several server ticks old.
                // This mutates only the client-side tracked entity; the authoritative server fuse
                // keeps counting independently.
                tnt.setFuse(seedFuse + DELIBERATE_STALE_TICKS);
                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(
                    minecraft.level,
                    minecraft.player,
                    LIMITS
                );
                WorldSnapshot.EntitySnapshot snapshot = world.entities().stream()
                    .filter(candidate -> candidate.id().equals(Integer.toString(entityId)))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("delayed TNT missing from production world snapshot"));
                int observedFuse = Integer.parseInt(snapshot.properties().getOrDefault("fuse_ticks", "-1"));
                if (!Boolean.parseBoolean(snapshot.properties().getOrDefault("countdown_server_synchronized", "false"))) {
                    throw new AssertionError("delayed TNT snapshot lost synchronized-countdown metadata");
                }
                return new ObservedCapture(player, world, observedFuse);
            });

            ServerCountdown authoritative = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = ((ServerLevel)player.level()).getEntity(entityId);
                if (!(entity instanceof PrimedTnt tnt)) {
                    throw new AssertionError("server TNT disappeared before authoritative countdown measurement");
                }
                int startingFuse = tnt.getFuse();
                int ticks = 0;
                while (!tnt.isRemoved() && ticks <= SERVER_FUSE_SEED + MAX_REASONABLE_AGE_TICKS) {
                    tnt.tick();
                    ticks++;
                }
                if (!tnt.isRemoved()) {
                    throw new AssertionError("authoritative TNT did not detonate within bounded vanilla fuse ticks");
                }
                return new ServerCountdown(startingFuse, ticks);
            });

            int observationAge = observed.observedFuse() - authoritative.startingFuse();
            if (observationAge < 1 || observationAge > MAX_REASONABLE_AGE_TICKS) {
                throw new AssertionError(
                    "delayed TNT harness produced unreasonable observation age: observed=" + observed.observedFuse()
                        + " server=" + authoritative.startingFuse() + " age=" + observationAge
                );
            }
            if (authoritative.ticksToDetonation() != authoritative.startingFuse()) {
                throw new AssertionError(
                    "vanilla TNT countdown mismatch: startingFuse=" + authoritative.startingFuse()
                        + " ticksToDetonation=" + authoritative.ticksToDetonation()
                );
            }

            // With zero RTT center and jitter=(age-1)*50ms, TimingSnapshot's conservative
            // observation-age window is [0, age]. That is intentionally broad enough to include
            // the exact delayed sample without pretending the client knows the server phase.
            double jitterMs = Math.max(0d, observationAge - 1L) * 50d;
            TimingSnapshot timing = new TimingSnapshot(0L, 0d, jitterMs, new TickWindow(0L, 0L));
            PredictionContext predictionContext = new PredictionContext(
                observed.player(),
                observed.world(),
                timing,
                LIMITS
            );
            ThreatEvent event = new ExplosionPredictor().predict(predictionContext).stream()
                .filter(candidate -> candidate.id().equals("explosion:" + entityId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("delayed TNT produced no explosion prediction"));

            if (event.impact().earliest() > authoritative.ticksToDetonation()) {
                throw new AssertionError(
                    "network-aged TNT earliest deadline was too late: impact=" + event.impact()
                        + " authoritativeTicks=" + authoritative.ticksToDetonation()
                        + " observedFuse=" + observed.observedFuse()
                        + " serverFuse=" + authoritative.startingFuse()
                        + " ageWindow=" + timing.observationAgeWindow()
                );
            }
            if (event.impact().latest() < authoritative.ticksToDetonation()) {
                throw new AssertionError(
                    "network-aged TNT window excluded authoritative detonation: impact=" + event.impact()
                        + " authoritativeTicks=" + authoritative.ticksToDetonation()
                        + " observedFuse=" + observed.observedFuse()
                        + " serverFuse=" + authoritative.startingFuse()
                        + " ageWindow=" + timing.observationAgeWindow()
                );
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = ((ServerLevel)player.level()).getEntity(entityId);
                if (entity != null) entity.discard();
                SurvivalValidationClientGameTest.reset(player, 20f);
            });
            context.waitTick();
        }
    }

    private record ObservedCapture(
        PlayerSnapshot player,
        WorldSnapshot world,
        int observedFuse
    ) {
    }

    private record ServerCountdown(
        int startingFuse,
        int ticksToDetonation
    ) {
    }
}
