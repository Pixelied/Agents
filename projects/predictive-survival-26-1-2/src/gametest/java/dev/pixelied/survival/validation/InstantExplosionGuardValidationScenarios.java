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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class InstantExplosionGuardValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;

    private InstantExplosionGuardValidationScenarios() {
    }

    static void validateVisibleInstantSourcesArmBeforeDetonation(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateCrystalAndAnchor(context, singleplayer);
        validateExplosiveBed(context, singleplayer);
    }

    private static void validateCrystalAndAnchor(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        ArenaSetup arena = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 220d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(level, center, 5, 219, 223);
            player.teleportTo(center.getX() + 0.5d, 220d, center.getZ() + 0.5d);
            prepareTotemInventory(player, 4f);
            return new ArenaSetup(originalPosition, center, originals);
        });

        try {
            waitForClientPosition(context, arena.center().getX() + 0.5d, 220d, arena.center().getZ() + 0.5d);
            context.waitFor(minecraft -> minecraft.player != null
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));

            int crystalId = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                EndCrystal crystal = new EndCrystal(level, player.getX(), player.getY() + 0.9d, player.getZ() + 2.5d);
                level.addFreshEntity(crystal);
                return crystal.getId();
            });
            try {
                context.waitFor(minecraft -> minecraft.level != null
                    && minecraft.level.getEntity(crystalId) instanceof EndCrystal);
                armTotemOnServer(context, singleplayer, "end_crystal");
                ExplosionOutcome crystal = singleplayer.getServer().computeOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                        throw new AssertionError("server lost Totem authority before controlled End Crystal detonation");
                    }
                    Entity entity = player.level().getEntity(crystalId);
                    if (!(entity instanceof EndCrystal endCrystal)) {
                        throw new AssertionError("End Crystal disappeared before controlled detonation");
                    }
                    player.invulnerableTime = 0;
                    player.setHealth(4f);
                    boolean accepted = endCrystal.hurtServer(
                        (ServerLevel) player.level(),
                        player.damageSources().playerAttack(player),
                        1f
                    );
                    return new ExplosionOutcome(
                        accepted,
                        player.getHealth(),
                        player.getMainHandItem().isEmpty(),
                        endCrystal.isRemoved()
                    );
                });
                assertProtectedExplosion("end_crystal", crystal);
            } finally {
                singleplayer.getServer().runOnServer(server -> {
                    ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                    Entity entity = player.level().getEntity(crystalId);
                    if (entity != null) entity.discard();
                    player.teleportTo(arena.center().getX() + 0.5d, 220d, arena.center().getZ() + 0.5d);
                    prepareTotemInventory(player, 4f);
                });
                context.waitTick();
            }

            BlockPos anchorPos = arena.center().offset(0, 0, 2);
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                level.setBlockAndUpdate(
                    anchorPos,
                    Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 4)
                );
                player.teleportTo(arena.center().getX() + 0.5d, 220d, arena.center().getZ() + 0.5d);
                prepareTotemInventory(player, 4f);
            });
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(anchorPos).getValue(RespawnAnchorBlock.CHARGE) == 4);
            armTotemOnServer(context, singleplayer, "respawn_anchor");

            ExplosionOutcome anchor = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server lost Totem authority before controlled Respawn Anchor detonation");
                }
                BlockState state = level.getBlockState(anchorPos);
                if (!state.is(Blocks.RESPAWN_ANCHOR)) {
                    throw new AssertionError("Respawn Anchor disappeared before controlled detonation");
                }
                player.invulnerableTime = 0;
                player.setHealth(4f);
                state.useWithoutItem(
                    level,
                    player,
                    new BlockHitResult(anchorPos.getCenter(), Direction.UP, anchorPos, false)
                );
                return new ExplosionOutcome(
                    true,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    !level.getBlockState(anchorPos).is(Blocks.RESPAWN_ANCHOR)
                );
            });
            assertProtectedExplosion("respawn_anchor", anchor);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel) player.level();
                restore(level, arena.originalBlocks());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                player.teleportTo(arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
                player.containerMenu.broadcastChanges();
            });
            ensureHotbarSelection(context, singleplayer, 0, "instant_explosion_overworld_cleanup");
            waitForClientPosition(context, arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
        }
    }

    private static void validateExplosiveBed(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        BedArena arena = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel originalLevel = (ServerLevel) player.level();
            Vec3 originalPosition = player.position();
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether == null) throw new AssertionError("Nether level unavailable for explosive-bed regression");

            BlockPos center = BlockPos.containing(player.getX(), 128d, player.getZ());
            Map<BlockPos, BlockState> originals = prepareArena(nether, center, 5, 127, 132);
            boolean teleported = player.teleportTo(
                nether,
                center.getX() + 0.5d,
                128d,
                center.getZ() + 0.5d,
                Set.<Relative>of(),
                0f,
                0f,
                true
            );
            if (!teleported) throw new AssertionError("could not move GameTest player to Nether bed arena");
            prepareTotemInventory(player, 4f);

            BlockPos foot = center.offset(0, 0, 2);
            BlockPos head = foot.relative(Direction.SOUTH);
            BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
            BlockState headState = footState.setValue(BedBlock.PART, BedPart.HEAD);
            nether.setBlock(foot, footState, 2);
            nether.setBlock(head, headState, 2);
            return new BedArena(originalLevel.dimension(), originalPosition, center, foot, head, originals);
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.dimension() == Level.NETHER
                && minecraft.player != null
                && minecraft.level.getBlockState(arena.head()).is(Blocks.RED_BED)
                && minecraft.level.getBlockState(arena.head()).getValue(BedBlock.PART) == BedPart.HEAD
                && minecraft.player.getInventory().getItem(1).is(Items.TOTEM_OF_UNDYING));
            armTotemOnServer(context, singleplayer, "explosive_bed");

            ExplosionOutcome bed = singleplayer.getServer().computeOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel nether = (ServerLevel) player.level();
                if (nether.dimension() != Level.NETHER) throw new AssertionError("player left Nether before bed detonation");
                if (!player.getMainHandItem().is(Items.TOTEM_OF_UNDYING)) {
                    throw new AssertionError("server lost Totem authority before controlled bed detonation");
                }
                BlockState state = nether.getBlockState(arena.head());
                if (!state.is(Blocks.RED_BED) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
                    throw new AssertionError("bed head disappeared before controlled detonation");
                }
                player.invulnerableTime = 0;
                player.setHealth(4f);
                state.useWithoutItem(
                    nether,
                    player,
                    new BlockHitResult(arena.head().getCenter(), Direction.UP, arena.head(), false)
                );
                return new ExplosionOutcome(
                    true,
                    player.getHealth(),
                    player.getMainHandItem().isEmpty(),
                    !nether.getBlockState(arena.head()).is(Blocks.RED_BED)
                        && !nether.getBlockState(arena.foot()).is(Blocks.RED_BED)
                );
            });
            assertProtectedExplosion("explosive_bed", bed);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel nether = server.getLevel(Level.NETHER);
                if (nether != null) restore(nether, arena.originalBlocks());
                ServerLevel original = server.getLevel(arena.originalDimension());
                if (original == null) throw new AssertionError("original level unavailable after bed regression");
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.getInventory().setSelectedSlot(0);
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, ItemStack.EMPTY);
                boolean teleported = player.teleportTo(
                    original,
                    arena.originalPosition().x,
                    arena.originalPosition().y,
                    arena.originalPosition().z,
                    Set.<Relative>of(),
                    0f,
                    0f,
                    true
                );
                if (!teleported) throw new AssertionError("could not return GameTest player after bed regression");
                player.containerMenu.broadcastChanges();
            });
            context.waitFor(minecraft -> minecraft.level != null && minecraft.level.dimension() == arena.originalDimension());
            ensureHotbarSelection(context, singleplayer, 0, "instant_explosion_bed_cleanup");
            waitForClientPosition(context, arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
        }
    }

    private static void armTotemOnServer(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        String sourceId
    ) {
        ensureHotbarSelection(context, singleplayer, 0, sourceId + "_pre_arm");
        RuntimeHarness harness = context.computeOnClient(minecraft -> {
            MinecraftSurvivalRuntime runtime = new MinecraftSurvivalRuntime(minecraft);
            SurvivalEngine engine = new SurvivalEngine(
                SurvivalConfig.defaults(),
                runtime,
                new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
            );
            return new RuntimeHarness(runtime, engine);
        });
        for (int tick = 0; tick < ClientGameTestContext.DEFAULT_TIMEOUT; tick++) {
            context.runOnClient(minecraft -> harness.engine().tick());
            context.waitTick();
            boolean protectedOnServer = singleplayer.getServer().computeOnServer(server ->
                SurvivalValidationClientGameTest.onlyPlayer(server).getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            );
            if (protectedOnServer) return;
        }
        throw new AssertionError(
            "production engine did not make a Totem server-authoritative before visible lethal " + sourceId
        );
    }

    private static void ensureHotbarSelection(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        int slot,
        String id
    ) {
        context.runOnClient(minecraft -> {
            if (minecraft.player == null) throw new AssertionError("client player unavailable while selecting hotbar for " + id);
            boolean dispatched = new MinecraftCommandDispatcher().dispatch(
                minecraft,
                new ExecutionCommand.SelectHotbar(slot)
            );
            if (!dispatched) throw new AssertionError("could not select hotbar " + slot + " for " + id);
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
        throw new AssertionError("server did not confirm hotbar " + slot + " for " + id);
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
                    BlockState desired = y == minY ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    level.setBlock(pos, desired, 2);
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

    private static void assertProtectedExplosion(String id, ExplosionOutcome outcome) {
        if (!outcome.detonated()) throw new AssertionError(id + " real vanilla detonation was rejected");
        SurvivalValidationClientGameTest.assertClose(id + "_preemptive_totem", 1f, outcome.health(), EPSILON);
        if (!outcome.totemConsumed()) throw new AssertionError(id + " did not consume server-authoritative Totem");
        if (!outcome.sourceRemoved()) throw new AssertionError(id + " source was not removed by its real vanilla detonation path");
    }

    private record RuntimeHarness(MinecraftSurvivalRuntime runtime, SurvivalEngine engine) {
    }

    private record ExplosionOutcome(boolean detonated, float health, boolean totemConsumed, boolean sourceRemoved) {
    }

    private record ArenaSetup(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originalBlocks) {
    }

    private record BedArena(
        net.minecraft.resources.ResourceKey<Level> originalDimension,
        Vec3 originalPosition,
        BlockPos center,
        BlockPos foot,
        BlockPos head,
        Map<BlockPos, BlockState> originalBlocks
    ) {
    }
}
