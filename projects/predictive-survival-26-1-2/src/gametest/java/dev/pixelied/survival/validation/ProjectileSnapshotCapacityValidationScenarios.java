package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ObservationOverflowPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

final class ProjectileSnapshotCapacityValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final EngineLimits SMALL_LIMITS = new EngineLimits(2, 32, 80, 128);
    private static final int EXPECTED_ENTITY_BUDGET = SMALL_LIMITS.maxThreats() * 4;

    private ProjectileSnapshotCapacityValidationScenarios() {
    }

    static void validateHarmlessTrackedEntitiesCannotCrowdOutDamagingProjectile(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            List<Integer> fillerIds = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ArmorStand stand = new ArmorStand(
                    level,
                    player.getX() + 4d,
                    player.getY(),
                    player.getZ() + 0.5d * i
                );
                stand.setNoGravity(true);
                level.addFreshEntity(stand);
                fillerIds.add(stand.getId());
            }

            Arrow arrow = damagingArrow(level, player);
            level.addFreshEntity(arrow);
            return new Setup(arrow.getId(), List.copyOf(fillerIds));
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) instanceof Arrow
                && setup.fillerIds().stream().allMatch(id -> minecraft.level.getEntity(id) instanceof ArmorStand));

            ClientObservation client = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable while testing snapshot capacity");
                }
                WorldSnapshot snapshot = new MinecraftWorldSnapshotFactory().capture(
                    minecraft.level,
                    minecraft.player,
                    SMALL_LIMITS
                );
                boolean projectilePresent = snapshot.entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.projectileId())));
                long fillerCount = snapshot.entities().stream()
                    .filter(entity -> isNumeric(entity.id()) && setup.fillerIds().contains(Integer.parseInt(entity.id())))
                    .count();
                return new ClientObservation(snapshot.entities().size(), fillerCount, projectilePresent);
            });

            int actualDamageTick = waitForDamage(context, singleplayer);
            if (!client.projectilePresent()) {
                throw new AssertionError(
                    "harmless client-tracked entities crowded a damaging projectile out of the production world snapshot; "
                        + "actualDamageTick=" + actualDamageTick + " client=" + client
                );
            }
            assertBounded(client.snapshotEntityCount(), actualDamageTick, client.toString());
        } finally {
            cleanup(singleplayer, setup.projectileId(), setup.fillerIds());
            context.waitTick();
        }
    }

    static void validateRelevantProjectileOverflowFailsClosed(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Setup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            List<Integer> fillerIds = new ArrayList<>();
            for (int i = 0; i < EXPECTED_ENTITY_BUDGET; i++) {
                Arrow filler = new Arrow(
                    level,
                    player.getX() + 4d,
                    player.getY() + 1d,
                    player.getZ() + 0.5d * i,
                    new ItemStack(Items.ARROW),
                    null
                );
                filler.setNoGravity(true);
                filler.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(filler);
                fillerIds.add(filler.getId());
            }

            Arrow arrow = damagingArrow(level, player);
            level.addFreshEntity(arrow);
            return new Setup(arrow.getId(), List.copyOf(fillerIds));
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(setup.projectileId()) instanceof Arrow
                && setup.fillerIds().stream().allMatch(id -> minecraft.level.getEntity(id) instanceof Arrow));

            OverflowObservation observation = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable while testing relevant overflow");
                }
                MinecraftSnapshotFactory playerFactory = new MinecraftSnapshotFactory();
                MinecraftWorldSnapshotFactory worldFactory = new MinecraftWorldSnapshotFactory();
                PlayerSnapshot player = playerFactory.capture(minecraft.player);
                WorldSnapshot world = worldFactory.capture(minecraft.level, minecraft.player, SMALL_LIMITS);
                boolean projectilePresent = world.entities().stream()
                    .anyMatch(entity -> entity.id().equals(Integer.toString(setup.projectileId())));
                WorldSnapshot.EntitySnapshot marker = world.entities().stream()
                    .filter(entity -> ObservationOverflowPredictor.MARKER_TYPE.equals(entity.typeKey()))
                    .findFirst()
                    .orElse(null);
                PredictionContext predictionContext = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    SMALL_LIMITS
                );
                List<ThreatEvent> overflowEvents = new ObservationOverflowPredictor().predict(predictionContext);
                return new OverflowObservation(
                    world.entities().size(),
                    projectilePresent,
                    marker != null,
                    marker == null ? 0 : parsePositive(marker.properties().get("omitted_relevant_entities")),
                    overflowEvents.size()
                );
            });

            int actualDamageTick = waitForDamage(context, singleplayer);
            assertBounded(observation.snapshotEntityCount(), actualDamageTick, observation.toString());
            if (!observation.overflowMarkerPresent() || observation.omittedRelevantEntities() <= 0) {
                throw new AssertionError(
                    "more threat-relevant tracked entities than the snapshot budget must produce an explicit overflow marker; "
                        + "actualDamageTick=" + actualDamageTick + " observation=" + observation
                );
            }
            if (observation.overflowThreatCount() != 1) {
                throw new AssertionError(
                    "observation overflow marker did not become exactly one fail-closed threat; observation=" + observation
                );
            }
        } finally {
            cleanup(singleplayer, setup.projectileId(), setup.fillerIds());
            context.waitTick();
        }
    }

    private static Arrow damagingArrow(ServerLevel level, ServerPlayer player) {
        Vec3 spawn = new Vec3(player.getX(), player.getEyeY() - 0.15d, player.getZ() + 60d);
        Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(0d, 0d, -1.2d);
        return arrow;
    }

    private static int waitForDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        for (int tick = 1; tick <= 90; tick++) {
            context.waitTick();
            float health = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getHealth()
            );
            if (health < 20f - EPSILON) return tick;
        }
        throw new AssertionError("snapshot-capacity arrow did not hit within 90 ticks");
    }

    private static void assertBounded(int snapshotEntityCount, int actualDamageTick, String observation) {
        if (snapshotEntityCount > EXPECTED_ENTITY_BUDGET) {
            throw new AssertionError(
                "production world snapshot exceeded its entity budget; budget=" + EXPECTED_ENTITY_BUDGET
                    + " actualDamageTick=" + actualDamageTick + " observation=" + observation
            );
        }
    }

    private static void cleanup(TestSingleplayerContext singleplayer, int projectileId, List<Integer> fillerIds) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity projectile = player.level().getEntity(projectileId);
            if (projectile != null) projectile.discard();
            for (int fillerId : fillerIds) {
                Entity filler = player.level().getEntity(fillerId);
                if (filler != null) filler.discard();
            }
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
        });
    }

    private static int parsePositive(String value) {
        if (value == null) return 0;
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean isNumeric(String value) {
        if (value == null || value.isBlank()) return false;
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i)) && !(i == 0 && value.charAt(i) == '-')) return false;
        }
        return true;
    }

    private record Setup(int projectileId, List<Integer> fillerIds) {
    }

    private record ClientObservation(int snapshotEntityCount, long fillerCount, boolean projectilePresent) {
    }

    private record OverflowObservation(
        int snapshotEntityCount,
        boolean projectilePresent,
        boolean overflowMarkerPresent,
        int omittedRelevantEntities,
        int overflowThreatCount
    ) {
    }
}
