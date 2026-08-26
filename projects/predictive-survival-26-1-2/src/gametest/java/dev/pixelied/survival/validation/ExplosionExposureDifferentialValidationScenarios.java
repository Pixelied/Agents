package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ExplosionExposure;
import dev.pixelied.survival.threat.SnapshotOcclusionView;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Differentially checks live vanilla exposure against exact captured collision components. */
final class ExplosionExposureDifferentialValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final float RADIUS = 4f;
    private static final double EXPLOSION_DISTANCE = 5d;
    private static final double POSITION_EPSILON = 0.05d;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final ExplosionExposure EXPOSURE = new ExplosionExposure();

    private ExplosionExposureDifferentialValidationScenarios() {
    }

    static void validateExactCollisionShapeExposure(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Arena arena = singleplayer.getServer().computeOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)player.level();
            Vec3 original = player.position();
            BlockPos center = BlockPos.containing(player.getX(), 230d, player.getZ());
            Map<BlockPos, BlockState> originals = clearArena(level, center);
            positionPlayer(player, center);
            return new Arena(original, center, originals);
        });

        try {
            waitForClientPosition(context, arena.center());
            for (ShapeCase shape : cases()) validateCase(context, singleplayer, arena.center(), shape);
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ServerLevel level = (ServerLevel)player.level();
                restore(level, arena.originals());
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.setNoGravity(false);
                player.teleportTo(arena.originalPosition().x, arena.originalPosition().y, arena.originalPosition().z);
            });
            context.waitTick();
        }
    }

    private static void validateCase(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        BlockPos center,
        ShapeCase shape
    ) {
        BlockPos blocker = center.offset(0, 1, 2);
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)player.level();
            positionPlayer(player, center);
            level.setBlockAndUpdate(blocker, shape.state());
        });
        waitForClientPosition(context, center);
        context.waitFor(minecraft -> minecraft.level != null
            && minecraft.level.getBlockState(blocker).equals(shape.state()));

        Prediction prediction = context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client unavailable for explosion shape " + shape.id());
            }
            PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
            WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
            Vec3 centerVec = minecraft.player.position().add(0d, 0.9d, EXPLOSION_DISTANCE);
            Vec3Snapshot explosionCenter = new Vec3Snapshot(centerVec.x, centerVec.y, centerVec.z);
            float seen = EXPOSURE.seenPercent(
                player.boundingBox(),
                explosionCenter,
                new SnapshotOcclusionView(world.blocks())
            );
            double distance = player.position().distanceTo(centerVec);
            float raw = EXPOSURE.rawEntityDamage(RADIUS, distance, seen);
            return new Prediction(seen, raw);
        });

        Actual actual = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel)player.level();
            positionPlayer(player, center);
            Vec3 explosionCenter = player.position().add(0d, 0.9d, EXPLOSION_DISTANCE);
            float vanillaSeen = ServerExplosion.getSeenPercent(explosionCenter, player);
            float before = player.getHealth() + player.getAbsorptionAmount();
            new ServerExplosion(
                level,
                null,
                null,
                null,
                explosionCenter,
                RADIUS,
                false,
                Explosion.BlockInteraction.KEEP
            ).explode();
            float after = player.getHealth() + player.getAbsorptionAmount();
            return new Actual(vanillaSeen, before - after);
        });

        SurvivalValidationClientGameTest.assertClose(
            "explosion_shape_" + shape.id() + "_seen",
            actual.seenPercent(),
            prediction.seenPercent(),
            EPSILON
        );
        SurvivalValidationClientGameTest.assertClose(
            "explosion_shape_" + shape.id() + "_raw",
            actual.damage(),
            prediction.rawDamage(),
            EPSILON
        );
    }

    private static List<ShapeCase> cases() {
        return List.of(
            new ShapeCase("open_air", Blocks.AIR.defaultBlockState()),
            new ShapeCase("bottom_slab", Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM)),
            new ShapeCase("stair", Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)),
            new ShapeCase("fence", Blocks.OAK_FENCE.defaultBlockState()),
            new ShapeCase("wall", Blocks.COBBLESTONE_WALL.defaultBlockState()),
            new ShapeCase("trapdoor_closed", Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.OPEN, false)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)),
            new ShapeCase("trapdoor_open", Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH)
                .setValue(TrapDoorBlock.OPEN, true)
                .setValue(TrapDoorBlock.HALF, Half.BOTTOM)),
            new ShapeCase("compound_hopper", Blocks.HOPPER.defaultBlockState())
        );
    }

    private static void positionPlayer(ServerPlayer player, BlockPos center) {
        SurvivalValidationClientGameTest.reset(player, 20f);
        player.setNoGravity(true);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0d;
        player.teleportTo(center.getX() + 0.5d, center.getY(), center.getZ() + 0.5d);
    }

    private static Map<BlockPos, BlockState> clearArena(ServerLevel level, BlockPos center) {
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -1; dz <= 7; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    originals.put(pos, level.getBlockState(pos));
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
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

    private static void waitForClientPosition(ClientGameTestContext context, BlockPos center) {
        context.waitFor(minecraft -> minecraft.player != null
            && Math.abs(minecraft.player.getX() - (center.getX() + 0.5d)) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - center.getY()) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - (center.getZ() + 0.5d)) <= POSITION_EPSILON);
    }

    private record ShapeCase(String id, BlockState state) {
    }

    private record Prediction(float seenPercent, float rawDamage) {
    }

    private record Actual(float seenPercent, float damage) {
    }

    private record Arena(Vec3 originalPosition, BlockPos center, Map<BlockPos, BlockState> originals) {
    }
}
