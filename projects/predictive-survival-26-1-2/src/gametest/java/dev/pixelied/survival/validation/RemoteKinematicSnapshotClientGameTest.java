package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.WorldSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** Integration gate for the shared Task 9 remote-observation history layer. */
public final class RemoteKinematicSnapshotClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);
            waitForServerClientLoaded(context, singleplayer);

            int projectileId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setDeltaMovement(Vec3.ZERO);
                ServerLevel level = (ServerLevel) player.level();

                Vec3 spawn = new Vec3(player.getX() + 6d, player.getEyeY(), player.getZ() + 6d);
                Arrow arrow = new Arrow(level, spawn.x, spawn.y, spawn.z, new ItemStack(Items.ARROW), null);
                arrow.setNoGravity(true);
                arrow.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(arrow);
                return arrow.getId();
            });

            try {
                context.waitFor(minecraft -> minecraft.level != null
                    && minecraft.level.getEntity(projectileId) instanceof Arrow);

                context.computeOnClient(minecraft -> {
                    MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                    WorldSnapshot.EntitySnapshot first = projectileSnapshot(runtime, projectileId);
                    Map<String, String> firstProperties = first.properties();

                    if (firstProperties.containsKey("observation_age_ticks")) {
                        throw new AssertionError(
                            "Task 10 must not expose the fixed legacy projectile observation age"
                        );
                    }
                    long ageMin = requiredLong(firstProperties, "observation_age_min_ticks");
                    long ageMax = requiredLong(firstProperties, "observation_age_max_ticks");
                    int historySamples = requiredInt(firstProperties, "kinematic_history_samples");
                    boolean resetBoundary = requiredBoolean(firstProperties, "kinematic_reset_boundary");

                    if (ageMin < 0L || ageMax < ageMin) {
                        throw new AssertionError("invalid RTT/jitter observation-age bounds: " + ageMin + ".." + ageMax);
                    }
                    if (historySamples != 1) {
                        throw new AssertionError("first remote observation must have exactly one history sample, found " + historySamples);
                    }
                    if (!resetBoundary) {
                        throw new AssertionError("first remote observation must be marked as a reset boundary");
                    }

                    Map<String, String> repeatedProperties = projectileSnapshot(runtime, projectileId).properties();
                    if (requiredInt(repeatedProperties, "kinematic_history_samples") != 1) {
                        throw new AssertionError("same-logical-tick recapture must replace, not append, the kinematic sample");
                    }
                    if (requiredBoolean(repeatedProperties, "kinematic_reset_boundary")) {
                        throw new AssertionError("same-logical-tick recapture must not fabricate a reset boundary");
                    }

                    runtime.markRemoteEntityDiscontinuity(projectileId);
                    Map<String, String> resetProperties = projectileSnapshot(runtime, projectileId).properties();
                    if (requiredInt(resetProperties, "kinematic_history_samples") != 1) {
                        throw new AssertionError("authoritative discontinuity must reset history to one sample");
                    }
                    if (!requiredBoolean(resetProperties, "kinematic_reset_boundary")) {
                        throw new AssertionError("authoritative discontinuity must be exposed as a reset boundary");
                    }
                    return true;
                });
            } finally {
                singleplayer.getServer().runOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    Entity projectile = player.level().getEntity(projectileId);
                    if (projectile != null) projectile.discard();
                    SurvivalValidationClientGameTest.reset(player, 20f);
                    player.setDeltaMovement(Vec3.ZERO);
                });
                context.waitTick();
            }
        }
    }

    private static WorldSnapshot.EntitySnapshot projectileSnapshot(MinecraftSurvivalRuntime runtime, int projectileId) {
        return runtime.capture().context().world().entities().stream()
            .filter(entity -> entity.id().equals(Integer.toString(projectileId)))
            .findFirst()
            .orElseThrow(() -> new AssertionError("tracked arrow missing from production world snapshot"));
    }

    private static long requiredLong(Map<String, String> properties, String key) {
        String value = required(properties, key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new AssertionError("invalid long snapshot property " + key + "=" + value, exception);
        }
    }

    private static int requiredInt(Map<String, String> properties, String key) {
        String value = required(properties, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new AssertionError("invalid int snapshot property " + key + "=" + value, exception);
        }
    }

    private static boolean requiredBoolean(Map<String, String> properties, String key) {
        String value = required(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new AssertionError("invalid boolean snapshot property " + key + "=" + value);
        }
        return Boolean.parseBoolean(value);
    }

    private static String required(Map<String, String> properties, String key) {
        String value = properties.get(key);
        if (value == null || value.isBlank()) {
            throw new AssertionError("missing remote-kinematic snapshot property " + key);
        }
        return value;
    }

    private static void waitForServerClientLoaded(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean loaded = singleplayer.getServer().computeOnServer(server -> {
                var players = server.getPlayerList().getPlayers();
                return players.size() == 1 && players.getFirst().connection.hasClientLoaded();
            });
            if (loaded) return;
            context.waitTick();
        }
        throw new AssertionError("remote-kinematic server player did not report client-loaded readiness");
    }
}
