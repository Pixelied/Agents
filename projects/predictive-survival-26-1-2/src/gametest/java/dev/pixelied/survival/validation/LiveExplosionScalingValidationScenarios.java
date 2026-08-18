package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSnapshotFactory;
import dev.pixelied.survival.core.MinecraftWorldSnapshotFactory;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

final class LiveExplosionScalingValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final EngineLimits LIMITS = EngineLimits.defaults();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private LiveExplosionScalingValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        ValidationResult tnt = validateHardModeTnt(context, singleplayer);
        validateCrystalSnapshot(context, singleplayer);
        validateBadRespawnSnapshot(context, singleplayer);
        singleplayer.getServer().runOnServer(server -> server.setDifficulty(Difficulty.NORMAL, true));
        context.waitTick();
        return List.of(tnt);
    }

    private static ValidationResult validateHardModeTnt(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            server.setDifficulty(Difficulty.HARD, true);
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();
            Vec3 spawn = player.position().add(0d, 0.9d, 6.5d);
            PrimedTnt tnt = new PrimedTnt(level, spawn.x, spawn.y, spawn.z, null);
            tnt.setNoGravity(true);
            tnt.setDeltaMovement(Vec3.ZERO);
            tnt.setFuse(80);
            level.addFreshEntity(tnt);
            return tnt.getId();
        });

        context.waitFor(minecraft -> minecraft.player != null
            && minecraft.level != null
            && minecraft.level.getDifficulty() == Difficulty.HARD
            && minecraft.level.getEntity(entityId) != null);

        LivePrediction prediction = context.computeOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable for live TNT scaling validation");
            }
            PlayerSnapshot player = new MinecraftSnapshotFactory().capture(minecraft.player);
            WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
            WorldSnapshot.EntitySnapshot tntSnapshot = world.entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(entityId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("live TNT missing from world snapshot"));
            assertDifficultyScaled("tnt_snapshot", tntSnapshot.properties().get("scales_with_difficulty"));

            PredictionContext predictionContext = new PredictionContext(
                player,
                world,
                new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
                LIMITS
            );
            ThreatEvent event = new ExplosionPredictor().predict(predictionContext).stream()
                .filter(candidate -> candidate.id().equals("explosion:" + entityId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("live TNT produced no explosion prediction"));
            if (!event.damage().scalesWithDifficulty()) {
                throw new AssertionError("live TNT explosion prediction did not scale with difficulty");
            }
            return new LivePrediction(player, event);
        });

        float predictedHealth = SIMULATOR.simulate(prediction.player(), prediction.event().damage()).after().health();
        float actualHealth = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof PrimedTnt tnt)) {
                throw new AssertionError("server TNT disappeared before difficulty scaling validation");
            }
            Vec3 center = tnt.position();
            tnt.discard();
            player.invulnerableTime = 0;
            player.setHealth(20f);
            new ServerExplosion(
                level,
                null,
                player.damageSources().explosion(tnt, null),
                null,
                center,
                4f,
                false,
                Explosion.BlockInteraction.KEEP
            ).explode();
            return player.getHealth();
        });

        return new ValidationResult(
            "live_tnt_hard_scaling",
            predictedHealth,
            actualHealth,
            ValidationStatus.RUNTIME_CONFIRMED,
            EPSILON
        );
    }

    private static void validateCrystalSnapshot(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        int entityId = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            EndCrystal crystal = new EndCrystal(level, player.getX() + 5d, player.getY() + 1d, player.getZ());
            level.addFreshEntity(crystal);
            return crystal.getId();
        });

        context.waitFor(minecraft -> minecraft.level != null && minecraft.level.getEntity(entityId) != null);
        context.runOnClient(minecraft -> {
            if (minecraft.player == null || minecraft.level == null) {
                throw new AssertionError("client player/level unavailable for crystal scaling validation");
            }
            WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
            WorldSnapshot.EntitySnapshot crystal = world.entities().stream()
                .filter(entity -> entity.id().equals(Integer.toString(entityId)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("live crystal missing from world snapshot"));
            assertDifficultyScaled("crystal_snapshot", crystal.properties().get("scales_with_difficulty"));
        });

        singleplayer.getServer().runOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            Entity crystal = ((ServerLevel) player.level()).getEntity(entityId);
            if (crystal != null) crystal.discard();
        });
        context.waitTick();
    }

    private static void validateBadRespawnSnapshot(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        AnchorSetup setup = singleplayer.getServer().computeOnServer(server -> {
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            ServerLevel level = (ServerLevel) player.level();
            BlockPos pos = player.blockPosition().offset(3, 0, 0);
            BlockState original = level.getBlockState(pos);
            level.setBlockAndUpdate(
                pos,
                Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1)
            );
            return new AnchorSetup(pos, original);
        });

        try {
            context.waitFor(minecraft -> minecraft.level != null
                && minecraft.level.getBlockState(setup.pos()).is(Blocks.RESPAWN_ANCHOR)
                && minecraft.level.getBlockState(setup.pos()).getValue(RespawnAnchorBlock.CHARGE) == 1);
            context.runOnClient(minecraft -> {
                if (minecraft.player == null || minecraft.level == null) {
                    throw new AssertionError("client player/level unavailable for respawn-anchor scaling validation");
                }
                WorldSnapshot world = new MinecraftWorldSnapshotFactory().capture(minecraft.level, minecraft.player, LIMITS);
                WorldSnapshot.BlockSnapshot anchor = world.blocks().stream()
                    .filter(block -> block.blockId().equals("minecraft:respawn_anchor"))
                    .filter(block -> BlockPos.containing(
                        block.position().x(), block.position().y(), block.position().z()
                    ).equals(setup.pos()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("charged respawn anchor missing from world snapshot"));
                if (!"minecraft:bad_respawn_point".equals(anchor.properties().get("source_key"))) {
                    throw new AssertionError("charged Overworld respawn anchor did not expose bad-respawn source");
                }
                assertDifficultyScaled("bad_respawn_snapshot", anchor.properties().get("scales_with_difficulty"));
            });
        } finally {
            singleplayer.getServer().runOnServer(server -> {
                ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
                ((ServerLevel) player.level()).setBlockAndUpdate(setup.pos(), setup.original());
            });
            context.waitTick();
        }
    }

    private static void assertDifficultyScaled(String id, String value) {
        if (!Boolean.parseBoolean(value)) {
            throw new AssertionError(id + " did not expose scales_with_difficulty=true");
        }
    }

    private record LivePrediction(PlayerSnapshot player, ThreatEvent event) {
    }

    private record AnchorSetup(BlockPos pos, BlockState original) {
    }
}
