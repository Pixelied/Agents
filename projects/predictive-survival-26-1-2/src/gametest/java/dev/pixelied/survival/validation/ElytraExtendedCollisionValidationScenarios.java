package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.threat.FallPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Additional exact-runtime coverage for partial shapes, multi-tick pitch, and landing transitions. */
final class ElytraExtendedCollisionValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private ElytraExtendedCollisionValidationScenarios() {
    }

    static void validateExtendedCollisionMatrix(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateBottomSlabClearance(context, singleplayer);
        validatePitchedMultiTickWall(context, singleplayer);
        validateGroundOnlyLanding(context, singleplayer);
    }

    private static void validateBottomSlabClearance(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Fixture fixture = prepareEmptyFixture(context, singleplayer, 210.6d, "elytra_bottom_slab_clearance");
        BlockPos slabPos = new BlockPos(fixture.cellX() + 1, 210, fixture.cellZ());
        singleplayer.getServer().runOnServer(server -> {
            ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
            level.setBlockAndUpdate(slabPos, Blocks.STONE_SLAB.defaultBlockState());
        });
        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(slabPos).is(Blocks.STONE_SLAB));

            Prediction prediction = predict(context, new Vec3(1d, 0d, 0d), -90f, 0f);
            if (prediction.event() != null) {
                throw new AssertionError(
                    "bottom-slab clearance falsely predicted fly_into_wall: " + prediction.event().damage().rawDamage()
                );
            }

            Actual actual = authoritativeTravel(singleplayer, fixture, new Vec3(1d, 0d, 0d), -90f, 0f, 1);
            if (actual.horizontalCollision()) {
                throw new AssertionError("server collided with bottom slab below the player's feet: " + actual);
            }
            SurvivalValidationClientGameTest.assertClose(
                "elytra_bottom_slab_clearance_health",
                actual.initialHealth(),
                actual.health(),
                EPSILON
            );
        } finally {
            cleanup(context, singleplayer, fixture);
        }
    }

    private static void validatePitchedMultiTickWall(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Fixture fixture = prepareEmptyFixture(context, singleplayer, 230.0d, "elytra_pitched_multitick");
        int wallX = fixture.cellX() + 5;
        singleplayer.getServer().runOnServer(server -> {
            ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
            for (int y = 225; y <= 235; y++) {
                for (int z = fixture.cellZ() - 2; z <= fixture.cellZ() + 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(wallX, y, z), Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        });
        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(new BlockPos(wallX, 230, fixture.cellZ())).is(Blocks.OBSIDIAN));

            Vec3 velocity = new Vec3(0.9d, 0d, 0d);
            Prediction prediction = predict(context, velocity, -90f, 20f);
            if (prediction.event() == null) {
                throw new AssertionError("pitched multi-tick wall produced no fly_into_wall prediction");
            }
            long predictedTick = prediction.event().impact().earliest();
            if (predictedTick <= 1L) {
                throw new AssertionError("pitched multi-tick fixture collided too early for a multi-tick proof: " + predictedTick);
            }

            Actual actual = authoritativeTravel(singleplayer, fixture, velocity, -90f, 20f, 12);
            if (!actual.horizontalCollision()) {
                throw new AssertionError("real pitched Elytra travel never reached the wall: " + actual);
            }
            if (actual.collisionTick() != predictedTick) {
                throw new AssertionError(
                    "pitched Elytra collision tick mismatch predicted=" + predictedTick + " actual=" + actual.collisionTick()
                );
            }
            SurvivalValidationClientGameTest.assertClose(
                "elytra_pitched_multitick_final_health",
                prediction.predictedHealth(),
                actual.health(),
                EPSILON
            );
        } finally {
            cleanup(context, singleplayer, fixture);
        }
    }

    private static void validateGroundOnlyLanding(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Fixture fixture = prepareEmptyFixture(context, singleplayer, 240.65d, "elytra_ground_only");
        singleplayer.getServer().runOnServer(server -> {
            ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
            for (int x = fixture.cellX() - 2; x <= fixture.cellX() + 3; x++) {
                for (int z = fixture.cellZ() - 2; z <= fixture.cellZ() + 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 239, z), Blocks.OBSIDIAN.defaultBlockState());
                }
            }
        });
        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(new BlockPos(fixture.cellX(), 239, fixture.cellZ())).is(Blocks.OBSIDIAN));

            Vec3 velocity = new Vec3(0.35d, -0.9d, 0d);
            Prediction prediction = predict(context, velocity, -90f, 35f);
            if (prediction.event() != null) {
                throw new AssertionError("ground-only Elytra landing was mislabeled fly_into_wall: " + prediction.event());
            }

            Actual actual = authoritativeTravel(singleplayer, fixture, velocity, -90f, 35f, 1);
            if (actual.horizontalCollision()) {
                throw new AssertionError("ground-only landing incorrectly reported horizontal collision: " + actual);
            }
            SurvivalValidationClientGameTest.assertClose(
                "elytra_ground_only_no_wall_damage",
                actual.initialHealth(),
                actual.health(),
                EPSILON
            );
        } finally {
            cleanup(context, singleplayer, fixture);
        }
    }

    private static Fixture prepareEmptyFixture(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        double y,
        String id
    ) {
        Fixture fixture = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Vec3 originalPosition = player.position();
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.stopFallFlying();
            player.setNoGravity(true);
            player.setOnGround(false);
            player.setDeltaMovement(Vec3.ZERO);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));

            int cellX = (int) Math.floor(originalPosition.x);
            int cellZ = (int) Math.floor(originalPosition.z);
            Vec3 start = new Vec3(cellX + 0.5d, y, cellZ + 0.5d);
            ServerLevel level = (ServerLevel) player.level();
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            for (int x = cellX - 3; x <= cellX + 8; x++) {
                for (int blockY = (int) Math.floor(y) - 6; blockY <= (int) Math.floor(y) + 6; blockY++) {
                    for (int z = cellZ - 3; z <= cellZ + 3; z++) {
                        BlockPos pos = new BlockPos(x, blockY, z);
                        originals.put(pos, level.getBlockState(pos));
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }

            player.teleportTo(start.x, start.y, start.z);
            player.setYRot(-90f);
            player.setXRot(0f);
            player.setDeltaMovement(Vec3.ZERO);
            player.containerMenu.broadcastChanges();
            return new Fixture(player.getUUID(), originalPosition, start, cellX, cellZ, Map.copyOf(originals), id);
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
            && Math.abs(minecraft.player.getX() - fixture.start().x) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - fixture.start().y) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - fixture.start().z) <= POSITION_EPSILON);
        return fixture;
    }

    private static Prediction predict(
        ClientGameTestContext context,
        Vec3 velocity,
        float yaw,
        float pitch
    ) {
        return context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable during extended Elytra validation");
            }
            minecraft.player.setNoGravity(false);
            minecraft.player.setOnGround(false);
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(pitch);
            minecraft.player.setDeltaMovement(velocity);
            minecraft.player.startFallFlying();

            PlayerSnapshot snapshot = new MinecraftSnapshotFactory().capture(minecraft.player);
            PredictionContext predictionContext = new PredictionContext(
                snapshot,
                new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS),
                new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                LIMITS
            );
            Optional<ThreatEvent> event = new FallPredictor().predict(predictionContext).stream()
                .filter(candidate -> "minecraft:fly_into_wall".equals(candidate.damage().sourceKey()))
                .findFirst();
            float predictedHealth = event
                .map(value -> SIMULATOR.simulate(snapshot, value.damage()).after().health())
                .orElse(snapshot.health());
            return new Prediction(event.orElse(null), predictedHealth);
        });
    }

    private static Actual authoritativeTravel(
        TestSingleplayerContext singleplayer,
        Fixture fixture,
        Vec3 velocity,
        float yaw,
        float pitch,
        int maxTicks
    ) {
        return singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(fixture.playerId());
            if (player == null) throw new AssertionError("server player disappeared during " + fixture.id());
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
            player.teleportTo(fixture.start().x, fixture.start().y, fixture.start().z);
            player.setNoGravity(false);
            player.setOnGround(false);
            player.setYRot(yaw);
            player.setXRot(pitch);
            player.setDeltaMovement(velocity);
            player.invulnerableTime = 0;
            player.startFallFlying();

            float initialHealth = player.getHealth();
            int collisionTick = -1;
            for (int tick = 1; tick <= maxTicks; tick++) {
                player.travel(Vec3.ZERO);
                if (player.horizontalCollision) {
                    collisionTick = tick;
                    break;
                }
            }
            return new Actual(
                initialHealth,
                player.getHealth(),
                player.horizontalCollision,
                collisionTick,
                player.getDeltaMovement().horizontalDistance()
            );
        });
    }

    private static void cleanup(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
        Fixture fixture
    ) {
        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayer(fixture.playerId());
            if (player != null) {
                SurvivalValidationClientGameTest.reset(player, 20f);
                player.stopFallFlying();
                player.setNoGravity(false);
                player.setOnGround(false);
                player.teleportTo(
                    fixture.originalPosition().x,
                    fixture.originalPosition().y,
                    fixture.originalPosition().z
                );
                player.setDeltaMovement(Vec3.ZERO);
                player.containerMenu.broadcastChanges();
            }
            ServerLevel level = (ServerLevel) SurvivalValidationClientGameTest.onlyPlayer(server).level();
            for (Map.Entry<BlockPos, BlockState> entry : fixture.originals().entrySet()) {
                level.setBlockAndUpdate(entry.getKey(), entry.getValue());
            }
        });
        context.waitTick();
    }

    private record Fixture(
        UUID playerId,
        Vec3 originalPosition,
        Vec3 start,
        int cellX,
        int cellZ,
        Map<BlockPos, BlockState> originals,
        String id
    ) {
    }

    private record Prediction(ThreatEvent event, float predictedHealth) {
    }

    private record Actual(
        float initialHealth,
        float health,
        boolean horizontalCollision,
        int collisionTick,
        double postHorizontalSpeed
    ) {
    }
}
