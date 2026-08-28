package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.timing.TimingSnapshot;
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

/** Exact-runtime proof for lethal -> short safe flicker -> lethal restoration re-entry. */
final class RestorationReentryValidationScenarios {
    private static final double POSITION_EPSILON = 0.05d;

    private RestorationReentryValidationScenarios() {
    }

    static void validateSafeFlickerCannotRestoreBeforeDangerReturns(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Arena arena = singleplayer.getServer().computeOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 224d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center);
            player.teleportTo(center.getX() + 0.5d, 224d, center.getZ() + 0.5d);
            SurvivalValidationClientGameTest.reset(player, 4f);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0d;
            player.getInventory().setSelectedSlot(0);
            player.getInventory().setItem(0, new ItemStack(Items.STICK));
            player.getInventory().setItem(1, new ItemStack(Items.TOTEM_OF_UNDYING));
            player.containerMenu.broadcastChanges();
            return new Arena(originalPosition, center, originals);
        });

        int firstCrystalId = addCrystal(singleplayer);
        RuntimeHarness harness = context.computeOnClient(minecraft -> {
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(), runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            return new RuntimeHarness(runtime, engine);
        });

        int secondCrystalId = -1;
        try {
            waitForClientPosition(context, arena.center().getX() + 0.5d, 224d, arena.center().getZ() + 0.5d);
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(0).is(Items.STICK)
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING)
                && minecraft.level != null
                && minecraft.level.getEntity(firstCrystalId) instanceof EndCrystal);

            tickUntilServerSelection(context, singleplayer, harness, 1, "preemptive Totem arm");

            singleplayer.getServer().runOnServer(server -> {
                var player = SurvivalValidationClientGameTest.onlyPlayer(server);
                Entity entity = player.level().getEntity(firstCrystalId);
                if (entity != null) entity.discard();
            });
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(firstCrystalId) == null);

            SafeWindowFrame safe = context.computeOnClient(minecraft -> {
                var captured = harness.runtime().capture();
                TimingSnapshot timing = captured.context().timing();
                long oldRestoreEligible = Math.max(
                    timing.clientTick(),
                    timing.nextPacketProcessingWindow().latest()
                );
                long outbound = Math.max(0L, timing.nextPacketProcessingWindow().latest() - timing.clientTick());
                long leaseRelease = timing.clientTick()
                    + timing.observationAgeWindow().latest()
                    + outbound
                    + timing.serverCorrectionReturnTicks();
                harness.engine().tick();
                return new SafeWindowFrame(timing.clientTick(), oldRestoreEligible, leaseRelease);
            });
            if (safe.leaseReleaseTick() <= safe.oldRestoreEligibleTick() + 1L) {
                throw new AssertionError("fixture lacks a discriminating hold-lease window: " + safe);
            }
            context.waitTick();
            assertServerSelection(singleplayer, 1, "first apparently safe frame");

            // Advance past the old controller's next-packet-only restore point, but remain before
            // the timing-derived observation/correction return lease is allowed to release.
            while (context.computeOnClient(minecraft -> harness.runtime().capture().context().timing().clientTick())
                <= safe.oldRestoreEligibleTick()) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();
                assertServerSelection(singleplayer, 1, "safe flicker before re-entry");
            }
            long beforeReentry = context.computeOnClient(minecraft ->
                harness.runtime().capture().context().timing().clientTick());
            if (beforeReentry >= safe.leaseReleaseTick()) {
                throw new AssertionError("fixture crossed the hold-lease release before danger re-entry: " + safe
                    + " current=" + beforeReentry);
            }

            secondCrystalId = addCrystal(singleplayer);
            int reentryCrystalId = secondCrystalId;
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getEntity(reentryCrystalId) instanceof EndCrystal);

            // No safe restoration may have reached the server while the source disappeared and
            // returned. Once the renewed lethal evidence is visible, repeated production ticks
            // must keep the already-authoritative Totem parked.
            for (int i = 0; i < 4; i++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();
                assertServerSelection(singleplayer, 1, "renewed lethal crystal");
            }
        } finally {
            int cleanupSecondCrystalId = secondCrystalId;
            singleplayer.getServer().runOnServer(server -> {
                var player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                Entity first = level.getEntity(firstCrystalId);
                if (first != null) first.discard();
                if (cleanupSecondCrystalId >= 0) {
                    Entity second = level.getEntity(cleanupSecondCrystalId);
                    if (second != null) second.discard();
                }
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

    private static int addCrystal(TestSingleplayerContext singleplayer) {
        return singleplayer.getServer().computeOnServer(server -> {
            var player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            EndCrystal crystal = new EndCrystal(level, player.getX(), player.getY() + 0.9d, player.getZ() + 2.5d);
            level.addFreshEntity(crystal);
            return crystal.getId();
        });
    }

    private static void tickUntilServerSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        RuntimeHarness harness,
        int expectedSlot,
        String phase
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

    private static void assertServerSelection(TestSingleplayerContext singleplayer, int expected, String phase) {
        int selected = singleplayer.getServer().computeOnServer(server ->
            SurvivalValidationClientGameTest.onlyPlayer(server).getInventory().getSelectedSlot());
        if (selected != expected) {
            throw new AssertionError(phase + " expected server-selected hotbar=" + expected + " actual=" + selected);
        }
    }

    private static Map<BlockPos, BlockState> prepareArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int y = 223; y <= 227; y++) {
                    BlockPos pos = new BlockPos(center.getX() + dx, y, center.getZ() + dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, y == 223 ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
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

    private record SafeWindowFrame(long clientTick, long oldRestoreEligibleTick, long leaseReleaseTick) {
    }

    private record Arena(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
    }
}
