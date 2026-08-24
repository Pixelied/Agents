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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

final class FusedTntDeadlineValidationScenarios {
    private static final int FIXED_FUSE_TICKS = 16;

    private FusedTntDeadlineValidationScenarios() {
    }

    static void validateFixedFuseArmsBeforeVanillaDetonation(
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

        int tntId = -1;
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

            tntId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                PrimedTnt tnt = new PrimedTnt(level, player.getX(), player.getY() + 0.25d, player.getZ() + 2.0d, null);
                tnt.setDeltaMovement(Vec3.ZERO);
                tnt.setFuse(FIXED_FUSE_TICKS);
                level.addFreshEntity(tnt);
                return tnt.getId();
            });

            boolean protectedBeforeDetonation = false;
            int fuseWhenProtected = -1;
            for (int tick = 0; tick < FIXED_FUSE_TICKS + 12; tick++) {
                context.runOnClient(minecraft -> harness.engine().tick());
                context.waitTick();

                TntState state = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    Entity entity = player.level().getEntity(tntId);
                    int fuse = entity instanceof PrimedTnt tnt ? tnt.getFuse() : 0;
                    boolean protectedNow = player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
                        || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
                    return new TntState(entity instanceof PrimedTnt, fuse, protectedNow, player.getHealth());
                });

                if (state.protectedNow() && state.exists() && state.fuse() > 0) {
                    protectedBeforeDetonation = true;
                    fuseWhenProtected = state.fuse();
                }
                if (!state.exists()) break;
            }

            if (!protectedBeforeDetonation) {
                throw new AssertionError(
                    "production engine did not make a Totem server-authoritative before fixed TNT fuse reached zero"
                );
            }
            if (fuseWhenProtected <= 0 || fuseWhenProtected >= FIXED_FUSE_TICKS) {
                throw new AssertionError("invalid TNT protection fuse observation: " + fuseWhenProtected);
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
                throw new AssertionError("player did not survive fixed-fuse TNT after pre-arming protection");
            }
            if (after.stillHasTotem()) {
                throw new AssertionError("fixed-fuse TNT did not consume the armed Totem; scenario was not lethal enough");
            }
        } finally {
            int cleanupTntId = tntId;
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (cleanupTntId >= 0) {
                    Entity entity = level.getEntity(cleanupTntId);
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
        for (int dx = -5; dx <= 5; dx++) {
            for (int dz = -5; dz <= 5; dz++) {
                for (int y = 219; y <= 223; y++) {
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

    private record TntState(boolean exists, int fuse, boolean protectedNow, float health) {
    }

    private record FinalState(float health, boolean stillHasTotem) {
    }

    private record Arena(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
    }
}
