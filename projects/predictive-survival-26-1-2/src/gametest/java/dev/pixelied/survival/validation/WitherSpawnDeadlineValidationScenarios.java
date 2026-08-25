package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WitherSpawnDeadlineValidationScenarios implements FabricClientGameTest {
    private static final int FIXED_INVULNERABLE_TICKS = 24;

    @Override
    public void runTest(ClientGameTestContext context) {
        try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
            context.waitFor(minecraft -> minecraft.player != null && minecraft.level != null);
            waitForServerClientLoaded(context, singleplayer);
            validateSpawnCountdownArmsBeforeVanillaBlast(context, singleplayer);
        }
    }

    static void validateSpawnCountdownArmsBeforeVanillaBlast(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Arena arena = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 220d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);
            player.teleportTo(center.getX() + 0.5d, 220d, center.getZ() + 0.5d);
            prepareTotemInventory(player);
            return new Arena(originalPosition, center, originals);
        });

        int witherId = -1;
        try {
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getY() - 220d) < 0.05d
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            RuntimeHarness harness = context.computeOnClient(minecraft -> {
                MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
                SurvivalEngine engine = new SurvivalEngine(
                    SurvivalConfig.defaults(),
                    runtime,
                    new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
                );
                return new RuntimeHarness(runtime, engine);
            });

            witherId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                WitherBoss wither = EntityType.WITHER.create(level, EntitySpawnReason.TRIGGERED);
                if (wither == null) throw new AssertionError("could not create Wither for spawn-deadline regression");
                wither.setPos(player.getX() + 3.0d, player.getY(), player.getZ());
                wither.setDeltaMovement(Vec3.ZERO);
                wither.makeInvulnerable();
                wither.setInvulnerableTicks(FIXED_INVULNERABLE_TICKS);
                level.addFreshEntity(wither);
                return wither.getId();
            });

            int observedWitherId = witherId;
            context.waitFor(minecraft -> {
                Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(observedWitherId);
                return entity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0;
            });

            boolean protectedBeforeExplosion = false;
            int countdownWhenProtected = -1;
            for (int tick = 0; tick < FIXED_INVULNERABLE_TICKS + 12; tick++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();

                WitherState state = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    Entity entity = player.level().getEntity(observedWitherId);
                    int remaining = entity instanceof WitherBoss wither ? wither.getInvulnerableTicks() : 0;
                    boolean protectedNow = player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                        || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
                    return new WitherState(entity instanceof WitherBoss, remaining, protectedNow, player.getHealth());
                });

                if (state.protectedNow() && state.exists() && state.remainingInvulnerableTicks() > 0) {
                    protectedBeforeExplosion = true;
                    countdownWhenProtected = state.remainingInvulnerableTicks();
                }
                if (state.exists() && state.remainingInvulnerableTicks() <= 0) break;
            }

            if (!protectedBeforeExplosion) {
                throw new AssertionError(
                    "production engine did not make a Totem server-authoritative before the Wither spawn countdown reached zero"
                );
            }
            if (countdownWhenProtected <= 0 || countdownWhenProtected >= FIXED_INVULNERABLE_TICKS) {
                throw new AssertionError("invalid Wither protection countdown observation: " + countdownWhenProtected);
            }

            FinalState after = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return new FinalState(
                    player.getHealth(),
                    player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                        || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
                        || player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                );
            });
            if (after.health() <= 0f) {
                throw new AssertionError("player did not survive the Wither spawn explosion after pre-arming protection");
            }
            if (after.stillHasTotem()) {
                throw new AssertionError("Wither spawn blast did not consume the armed Totem; scenario was not lethal enough");
            }
        } finally {
            int cleanupWitherId = witherId;
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (cleanupWitherId >= 0) {
                    Entity entity = level.getEntity(cleanupWitherId);
                    if (entity != null) entity.discard();
                }
                restore(level, arena.originalBlocks());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.teleportTo(arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
                player.containerMenu.broadcastChanges();
            });
            context.waitFor(minecraft -> minecraft.player != null
                && Math.abs(minecraft.player.getX() - arena.originalPosition().x) < 0.05d
                && Math.abs(minecraft.player.getY() - arena.originalPosition().y) < 0.05d
                && Math.abs(minecraft.player.getZ() - arena.originalPosition().z) < 0.05d);
        }
    }

    private static void waitForServerClientLoaded(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean loaded = singleplayer.getServer().computeOnServer(server -> {
                List<ServerPlayer> players = server.getPlayerList().getPlayers();
                return players.size() == 1 && players.getFirst().connection.hasClientLoaded();
            });
            if (loaded) return;
            context.waitTick();
        }
        throw new AssertionError("server player did not report client-loaded readiness before Wither spawn regression");
    }

    private static void prepareTotemInventory(ServerPlayer player) {
        SurvivalValidationClientGameTest.reset(player, 4f);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0d;
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.STICK));
        player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.containerMenu.broadcastChanges();
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -7; dx <= 7; dx++) {
            for (int dz = -7; dz <= 7; dz++) {
                for (int y = 219; y <= 225; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(
                        pos,
                        y == 219 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                        2
                    );
                }
            }
        }
        return originals;
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record WitherState(boolean exists, int remainingInvulnerableTicks, boolean protectedNow, float health) {
    }

    private record FinalState(float health, boolean stillHasTotem) {
    }

    private record Arena(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
    }
}
