package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.threat.ElytraFlightCollisionSolver;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Diagnostic guard for the exact live inputs feeding the pitched Elytra solver. */
final class ElytraPitchedSnapshotDiagnosticScenarios {
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final double EPSILON = 0.000001d;

    private ElytraPitchedSnapshotDiagnosticScenarios() {
    }

    static void validateLivePitchedInputs(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Fixture fixture = prepare(context, singleplayer);
        try {
            SnapshotDiagnostic diagnostic = context.computeOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable during Elytra pitched diagnostic");
                }
                minecraft.player.setNoGravity(false);
                minecraft.player.setOnGround(false);
                minecraft.player.setYRot(-90f);
                minecraft.player.setXRot(20f);
                minecraft.player.setDeltaMovement(new Vec3(0.9d, 0d, 0d));
                minecraft.player.startFallFlying();

                PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                PredictionContext prediction = new PredictionContext(
                    player,
                    world,
                    new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                    LIMITS
                );
                var collision = new ElytraFlightCollisionSolver().solve(prediction).orElse(null);
                List<String> nearest = nearestForwardCollisionBoxes(player.boundingBox(), world.blocks());
                return new SnapshotDiagnostic(
                    player.position().toString(),
                    player.boundingBox().toString(),
                    player.velocity().toString(),
                    player.state("elytra_pitch_degrees"),
                    player.state("elytra_look_x"),
                    player.state("elytra_look_y"),
                    player.state("elytra_look_z"),
                    collision == null ? -1L : collision.tick(),
                    collision == null ? "none" : collision.toString(),
                    nearest
                );
            });

            if (Math.abs(Double.parseDouble(component(diagnostic.position(), 0)) - fixture.start().x) > EPSILON) {
                throw new AssertionError("live Elytra snapshot X drifted before prediction: " + diagnostic);
            }
            if (!diagnostic.velocity().contains("0.9")) {
                throw new AssertionError("live Elytra snapshot lost requested 0.9 X velocity: " + diagnostic);
            }
            if (diagnostic.nearestColliders().isEmpty()
                || !diagnostic.nearestColliders().getFirst().contains("minX=" + fixture.wallX() + ".0")) {
                throw new AssertionError("live Elytra snapshot nearest forward collider is not the intended wall: " + diagnostic);
            }
            if (diagnostic.collisionTick() != 5L) {
                throw new AssertionError("live pitched solver input predicts wrong collision tick: " + diagnostic);
            }
        } finally {
            cleanup(context, singleplayer, fixture);
        }
    }

    private static List<String> nearestForwardCollisionBoxes(
        AabbSnapshot player,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        List<AabbSnapshot> boxes = new ArrayList<>();
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!block.collision()) continue;
            for (AabbSnapshot box : block.collisionBoxes()) {
                if (box.minX() + EPSILON < player.maxX()) continue;
                if (box.maxY() <= player.minY() + EPSILON || box.minY() >= player.maxY() - EPSILON) continue;
                if (box.maxZ() <= player.minZ() + EPSILON || box.minZ() >= player.maxZ() - EPSILON) continue;
                boxes.add(box);
            }
        }
        boxes.sort(Comparator.comparingDouble(AabbSnapshot::minX));
        return boxes.stream().limit(8).map(box ->
            "minX=" + box.minX() + ",maxX=" + box.maxX()
                + ",y=" + box.minY() + ".." + box.maxY()
                + ",z=" + box.minZ() + ".." + box.maxZ()
        ).toList();
    }

    private static Fixture prepare(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
        Fixture fixture = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 original = player.position();
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.stopFallFlying();
            player.setNoGravity(true);
            player.setOnGround(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));

            int cellX = (int) Math.floor(original.x);
            int cellZ = (int) Math.floor(original.z);
            Vec3 start = new Vec3(cellX + 0.5d, 260d, cellZ + 0.5d);
            int wallX = cellX + 5;
            ServerLevel level = (ServerLevel) player.level();
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            for (int x = cellX - 3; x <= cellX + 8; x++) {
                for (int y = 254; y <= 266; y++) {
                    for (int z = cellZ - 3; z <= cellZ + 3; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        originals.put(pos, level.getBlockState(pos));
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            for (int y = 255; y <= 265; y++) {
                for (int z = cellZ - 2; z <= cellZ + 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(wallX, y, z), Blocks.OBSIDIAN.defaultBlockState());
                }
            }
            player.teleportTo(start.x, start.y, start.z);
            player.containerMenu.broadcastChanges();
            return new Fixture(player.getUUID(), original, start, wallX, cellZ, Map.copyOf(originals));
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && Math.abs(minecraft.player.getX() - fixture.start().x) <= 0.05d
            && Math.abs(minecraft.player.getY() - fixture.start().y) <= 0.05d
            && minecraft.level.getBlockState(new BlockPos(fixture.wallX(), 260, fixture.cellZ())).is(Blocks.OBSIDIAN));
        return fixture;
    }

    private static void cleanup(ClientGameTestContext context, TestSingleplayerContext singleplayer, Fixture fixture) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(fixture.playerId());
            if (player != null) {
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.stopFallFlying();
                player.setNoGravity(false);
                player.teleportTo(fixture.original().x, fixture.original().y, fixture.original().z);
                player.setDeltaMovement(Vec3.ZERO);
                player.containerMenu.broadcastChanges();
            }
            ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
            fixture.originals().forEach(level::setBlockAndUpdate);
        });
        context.waitTick();
    }

    private static String component(String vector, int index) {
        int open = vector.indexOf('[');
        int close = vector.lastIndexOf(']');
        if (open < 0 || close <= open) return "NaN";
        String[] parts = vector.substring(open + 1, close).split(",");
        if (index >= parts.length) return "NaN";
        String part = parts[index];
        int equals = part.indexOf('=');
        return (equals >= 0 ? part.substring(equals + 1) : part).trim();
    }

    private record Fixture(
        UUID playerId,
        Vec3 original,
        Vec3 start,
        int wallX,
        int cellZ,
        Map<BlockPos, BlockState> originals
    ) {
    }

    private record SnapshotDiagnostic(
        String position,
        String boundingBox,
        String velocity,
        String pitch,
        String lookX,
        String lookY,
        String lookZ,
        long collisionTick,
        String collision,
        List<String> nearestColliders
    ) {
    }
}
