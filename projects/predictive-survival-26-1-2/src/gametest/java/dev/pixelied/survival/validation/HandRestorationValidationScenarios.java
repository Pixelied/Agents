package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

final class HandRestorationValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private HandRestorationValidationScenarios() {
    }

    static void validateConfirmedHotbarProtectionRestoresAfterDanger(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Arena arena = singleplayer.getServer().computeOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 220d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);
            player.teleportTo(center.getX() + 0.5d, 220d, center.getZ() + 0.5d);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0d;
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STICK));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.containerMenu.broadcastChanges();
            return new Arena(originalPosition, center, originals);
        });

        int crystalId = singleplayer.getServer().computeOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            EndCrystal crystal = new EndCrystal(level, player.getX(), player.getY() + 0.9d, player.getZ() + 2.5d);
            level.addFreshEntity(crystal);
            return crystal.getId();
        });

        RuntimeHarness harness = context.computeOnClient(minecraft -> {
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(), runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            return new RuntimeHarness(runtime, engine);
        });

        try {
            waitForClientPosition(context, arena.center().getX() + 0.5d, 220d, arena.center().getZ() + 0.5d);
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.level != null
                && minecraft.level.getEntity(crystalId) instanceof EndCrystal);

            tickUntilServerSelection(context, singleplayer, harness, 1, "preemptive Totem arm");
            for (int i = 0; i < 12; i++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();
                int selected = singleplayer.getServer().computeOnServer(server ->
                    SurvivalValidationClientGameTest.onlyPlayer(server).getInventory().getSelectedSlot());
                if (selected != 1) {
                    throw new AssertionError("restoreHandState restored while lethal crystal remained visible");
                }
            }

            singleplayer.getServer().runOnServer(server -> {
                var player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(crystalId);
                if (entity != null) entity.discard();
            });
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(crystalId) == null);

            RestorationDispatchFrame dispatchFrame = captureRestorationDispatch(context, harness);
            if (dispatchFrame.processingEarliestTick() <= dispatchFrame.clientTick()
                && dispatchFrame.protectionCredited()) {
                throw new AssertionError(
                    "runtime kept guaranteeing the parked Totem after restoration was already feasible server-side: "
                        + dispatchFrame
                );
            }

            tickUntilServerSelection(context, singleplayer, harness, 0, "safe hand restoration");

            boolean intact = singleplayer.getServer().computeOnServer(server -> {
                var player = SurvivalValidationClientGameTest.onlyPlayer(server);
                return player.getMainHandItem().is(Items.STICK)
                    && player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                    && player.getInventory().getItem(1).getCount() == 1;
            });
            if (!intact) {
                throw new AssertionError("restoreHandState did not preserve the unconsumed Totem and original hand item");
            }
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                var player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity entity = level.getEntity(crystalId);
                if (entity != null) entity.discard();
                restore(level, arena.originalBlocks());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.teleportTo(arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
                player.containerMenu.broadcastChanges();
            });
            context.waitTick();
        }
    }

    private static RestorationDispatchFrame captureRestorationDispatch(
        ClientGameTestContext context,
        RuntimeHarness harness
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            RestorationDispatchFrame frame = context.computeOnClient(minecraft -> {
                if (minecraft.player == null) {
                    throw new AssertionError("client player disappeared while waiting for hotbar restoration");
                }
                harness.engine().tick();
                if (minecraft.player.getInventory().getSelectedSlot() != 0) return null;

                var captured = harness.runtime().capture();
                long clientTick = captured.context().timing().clientTick();
                long earliest = captured.context().timing().nextPacketProcessingWindow().earliest();
                return new RestorationDispatchFrame(
                    clientTick,
                    earliest,
                    captured.context().player().deathProtection().anyHandAvailable(),
                    minecraft.player.getMainHandItem().toString()
                );
            });
            if (frame != null) return frame;
            context.waitTick();
        }
        throw new AssertionError("client never dispatched the safe hotbar restoration");
    }

    private static void tickUntilServerSelection(
        ClientGameTestContext context, TestSingleplayerContext singleplayer,
        RuntimeHarness harness, int expectedSlot, String phase
    ) {
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            context.runOnClient(minecraft -> harness.engine().tick());
            context.waitTick();
            int selected = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getInventory().getSelectedSlot());
            if (selected == expectedSlot) return;
        }
        throw new AssertionError("server never confirmed hotbar " + expectedSlot + " during " + phase);
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = 219; y <= 223; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, y == 219 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
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

    private static void waitForClientPosition(ClientGameTestContext context, double x, double y, double z) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - x) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - y) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - z) <= POSITION_EPSILON);
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record RestorationDispatchFrame(
        long clientTick,
        long processingEarliestTick,
        boolean protectionCredited,
        String renderedMainHand
    ) {
    }

    private record Arena(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
    }
}
