package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

final class TriggerableExplosionRangeValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private TriggerableExplosionRangeValidationScenarios() {
    }

    static void validateNineBlockRespawnAnchorArmsBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Arena arena = singleplayer.getServer().computeOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 220d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center, 10, 219, 223);

            player.teleportTo(center.getX() + 0.5d, 220d, center.getZ() + 0.5d);
            prepareTotemInventory(player, 4f);

            BlockPos anchorPos = center.offset(0, 0, 9);
            level.setBlockAndUpdate(
                anchorPos,
                Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 4)
            );
            return new Arena(originalPosition, center, anchorPos, originals);
        });

        try {
            waitForClientPosition(
                context,
                arena.center().getX() + 0.5d,
                220d,
                arena.center().getZ() + 0.5d
            );
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.player != null
                && minecraft.level.getBlockState(arena.anchorPos()).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(arena.anchorPos()).getValue(RespawnAnchorBlock.CHARGE) == 4
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

            boolean protectedOnServer = false;
            for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();
                protectedOnServer = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                );
                if (protectedOnServer) break;
            }
            if (!protectedOnServer) {
                throw new AssertionError(
                    "production engine did not arm for lethal respawn anchor nine blocks away"
                );
            }

            ExplosionOutcome outcome = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server lost Totem authority before nine-block anchor detonation");
                }
                BlockState state = level.getBlockState(arena.anchorPos());
                if (!state.is(Blocks.RESPAWN_ANCHOR)) {
                    throw new AssertionError("nine-block Respawn Anchor disappeared before detonation");
                }

                player.invulnerableTime = 0;
                player.setHealth(4f);
                state.useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(arena.anchorPos().getCenter(), Direction.UP, arena.anchorPos(), false)
                );
                return new ExplosionOutcome(
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    !level.getBlockState(arena.anchorPos()).is(Blocks.RESPAWN_ANCHOR)
                );
            });

            SurvivalValidationClientGameTest.assertClose(
                "nine_block_respawn_anchor_preemptive_totem",
                1f,
                outcome.health(),
                EPSILON
            );
            if (!outcome.totemConsumed()) {
                throw new AssertionError("nine-block Respawn Anchor did not consume server-authoritative Totem");
            }
            if (!outcome.sourceRemoved()) {
                throw new AssertionError("nine-block Respawn Anchor did not detonate through vanilla path");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                restore(level, arena.originalBlocks());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.teleportTo(
                    arena.originalPosition().x,
                    arena.originalPosition().y,
                    arena.originalPosition().z
                );
                player.containerMenu.broadcastChanges();
            });
            ensureHotbarSelection(context, singleplayer, 0);
            waitForClientPosition(
                context,
                arena.originalPosition().x,
                arena.originalPosition().y,
                arena.originalPosition().z
            );
        }
    }

    private static void prepareTotemInventory(ServerPlayer player, float health) {
        SurvivalValidationClientGameTest.reset(player, health);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0d;
        player.getInventory().setSelectedSlot(0);
        player.getInventory().setItem(0, new ItemStack(Items.STICK));
        player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
        player.containerMenu.broadcastChanges();
    }

    private static Map<BlockPos, BlockState> prepareArena(
        ServerLevel level,
        BlockPos center,
        int radius,
        int minY,
        int maxY
    ) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(
                        pos,
                        y == minY ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                        2
                    );
                }
            }
        }
        return Map.copyOf(originals);
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlock(entry.getKey(), entry.getValue(), 2);
        }
    }

    private static void ensureHotbarSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int slot
    ) {
        context.runOnClient(minecraft -> {
            if (minecraft.player == null) {
                throw new AssertionError("client player unavailable during nine-block anchor cleanup");
            }
            boolean dispatched = new MinecraftCommandDispatcher().dispatch(
                minecraft,
                new ExecutionCommand.SelectHotbar(slot)
            );
            if (!dispatched) throw new AssertionError("could not restore hotbar after nine-block anchor regression");
        });
        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getInventory().getSelectedSlot() == slot);
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            boolean confirmed = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getInventory().getSelectedSlot() == slot
            );
            if (confirmed) return;
            context.waitTick();
        }
        throw new AssertionError("server did not restore hotbar after nine-block anchor regression");
    }

    private static void waitForClientPosition(ClientGameTestContext context, double x, double y, double z) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - x) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - y) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - z) <= POSITION_EPSILON);
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record Arena(
        Vec3 originalPosition,
        BlockPos center,
        BlockPos anchorPos,
        Map<BlockPos, BlockState> originalBlocks
    ) {
    }

    private record ExplosionOutcome(float health, boolean totemConsumed, boolean sourceRemoved) {
    }
}
