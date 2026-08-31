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

/** Exact-runtime parity for the 26.1.2 LivingEntity#travelFallFlying collision path. */
final class ElytraCollisionValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final double POSITION_EPSILON = 0.05d;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private ElytraCollisionValidationScenarios() {
    }

    static void validateHeadOnAndGlancingWallParity(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        validateHeadOnWall(context, singleplayer);
        validateGlancingWall(context, singleplayer);
    }

    private static void validateHeadOnWall(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        Vec3 velocity = new Vec3(1.0d, 0.0d, 0.0d);
        Fixture fixture = prepareFixture(context, singleplayer, "elytra_head_on");
        try {
            Prediction prediction = predictClientCollision(context, velocity, -90f, true);
            if (prediction.event().impact().earliest() != 1L || prediction.event().impact().latest() != 1L) {
                throw new AssertionError("head-on Elytra wall impact was not predicted on the first movement tick: "
                    + prediction.event().impact());
            }

            ActualCollision actual = runAuthoritativeTravel(singleplayer, fixture, velocity, -90f);
            if (!actual.horizontalCollision()) {
                throw new AssertionError("real server Elytra head-on travel did not report horizontal collision");
            }
            if (actual.health() >= actual.initialHealth()) {
                throw new AssertionError("real server Elytra head-on collision caused no fly_into_wall damage: " + actual);
            }
            SurvivalValidationClientGameTest.assertClose(
                "elytra_head_on_final_health",
                prediction.predictedHealth(),
                actual.health(),
                EPSILON
            );
            SurvivalValidationClientGameTest.assertClose(
                "elytra_head_on_raw_damage",
                prediction.event().damage().rawDamage().min(),
                actual.initialHealth() - actual.health(),
                EPSILON
            );
        } finally {
            cleanupFixture(context, singleplayer, fixture);
        }
    }

    private static void validateGlancingWall(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        double diagonal = Math.sqrt(0.5d);
        Vec3 velocity = new Vec3(diagonal, 0.0d, diagonal);
        Fixture fixture = prepareFixture(context, singleplayer, "elytra_glancing");
        try {
            Prediction prediction = predictClientCollision(context, velocity, -45f, false);
            if (prediction.event() != null) {
                throw new AssertionError("equal-speed glancing wall incorrectly predicted positive fly_into_wall damage: "
                    + prediction.event().damage().rawDamage());
            }

            ActualCollision actual = runAuthoritativeTravel(singleplayer, fixture, velocity, -45f);
            if (!actual.horizontalCollision()) {
                throw new AssertionError("real server Elytra glancing travel did not report horizontal collision");
            }
            if (actual.postHorizontalSpeed() <= 0.6d) {
                throw new AssertionError("glancing collision did not preserve the tangential velocity component: " + actual);
            }
            SurvivalValidationClientGameTest.assertClose(
                "elytra_glancing_no_damage",
                actual.initialHealth(),
                actual.health(),
                EPSILON
            );
        } finally {
            cleanupFixture(context, singleplayer, fixture);
        }
    }

    private static Fixture prepareFixture(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer,
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
            int startY = 200;
            Vec3 start = new Vec3(cellX + 0.5d, startY, cellZ + 0.5d);
            BlockPos wallCenter = new BlockPos(cellX + 1, startY, cellZ);

            ServerLevel level = (ServerLevel) player.level();
            Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
            for (int x = cellX - 2; x <= cellX + 3; x++) {
                for (int y = startY - 2; y <= startY + 3; y++) {
                    for (int z = cellZ - 3; z <= cellZ + 3; z++) {
                        BlockPos pos = new BlockPos(x, y, z);
                        originals.put(pos, level.getBlockState(pos));
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
            for (int y = startY - 1; y <= startY + 2; y++) {
                for (int z = cellZ - 2; z <= cellZ + 2; z++) {
                    level.setBlockAndUpdate(new BlockPos(cellX + 1, y, z), Blocks.OBSIDIAN.defaultBlockState());
                }
            }

            player.teleportTo(start.x, start.y, start.z);
            player.setYRot(-90f);
            player.setXRot(0f);
            player.setDeltaMovement(Vec3.ZERO);
            player.containerMenu.broadcastChanges();
            return new Fixture(player.getUUID(), originalPosition, start, wallCenter, Map.copyOf(originals), id);
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && minecraft.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
            && Math.abs(minecraft.player.getX() - fixture.start().x) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getY() - fixture.start().y) <= POSITION_EPSILON
            && Math.abs(minecraft.player.getZ() - fixture.start().z) <= POSITION_EPSILON
            && minecraft.level.getBlockState(fixture.wallCenter()).is(Blocks.OBSIDIAN));
        return fixture;
    }

    private static Prediction predictClientCollision(
        ClientGameTestContext context,
        Vec3 velocity,
        float yaw,
        boolean requireEvent
    ) {
        return context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable during Elytra collision validation");
            }
            minecraft.player.setNoGravity(false);
            minecraft.player.setOnGround(false);
            minecraft.player.setYRot(yaw);
            minecraft.player.setXRot(0f);
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
            if (requireEvent && event.isEmpty()) {
                throw new AssertionError("production predictor emitted no head-on fly_into_wall threat");
            }
            float predictedHealth = event
                .map(value -> SIMULATOR.simulate(snapshot, value.damage()).after().health())
                .orElse(snapshot.health());
            return new Prediction(event.orElse(null), predictedHealth);
        });
    }

    private static ActualCollision runAuthoritativeTravel(
        TestSingleplayerContext singleplayer,
        Fixture fixture,
        Vec3 velocity,
        float yaw
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
            player.setXRot(0f);
            player.setDeltaMovement(velocity);
            player.invulnerableTime = 0;
            player.startFallFlying();

            float initialHealth = player.getHealth();
            player.travel(Vec3.ZERO);
            return new ActualCollision(
                initialHealth,
                player.getHealth(),
                player.horizontalCollision,
                player.getDeltaMovement().horizontalDistance()
            );
        });
    }

    private static void cleanupFixture(
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
        BlockPos wallCenter,
        Map<BlockPos, BlockState> originals,
        String id
    ) {
    }

    private record Prediction(ThreatEvent event, float predictedHealth) {
    }

    private record ActualCollision(
        float initialHealth,
        float health,
        boolean horizontalCollision,
        double postHorizontalSpeed
    ) {
    }
}
