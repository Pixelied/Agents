package dev.pixelied.survival.validation;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.threat.CoverCandidate;
import dev.pixelied.survival.threat.ExplosionExposure;
import dev.pixelied.survival.threat.OcclusionView;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ExplosionValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final ExplosionExposure EXPOSURE = new ExplosionExposure();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private ExplosionValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(TestSingleplayerContext singleplayer) {
        List<ValidationResult> results = new ArrayList<>();
        results.add(validateExplosion(singleplayer, 4f, 7d, false, "tnt_open"));
        results.add(validateExplosion(singleplayer, 4f, 7d, true, "tnt_cover"));
        results.add(validateExplosion(singleplayer, 6f, 9d, false, "crystal_scale_open"));
        results.add(validateExplosion(singleplayer, 6f, 9d, true, "crystal_scale_cover"));
        return List.copyOf(results);
    }

    private static ValidationResult validateExplosion(
        TestSingleplayerContext singleplayer,
        float radius,
        double horizontalDistance,
        boolean covered,
        String id
    ) {
        return singleplayer.getServer().computeOnServer(server -> {
            server.setDifficulty(Difficulty.NORMAL, true);
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Vec3 center = player.position().add(0d, 0.9d, horizontalDistance);
            Map<BlockPos, BlockState> restoredBlocks = covered ? placeCover(level, player, center) : Map.of();
            try {
                float vanillaExposure = ServerExplosion.getSeenPercent(center, player);
                float predictedExposure = EXPOSURE.seenPercent(
                    box(player),
                    vec(center),
                    new ServerOcclusionView(level, player)
                );
                SurvivalValidationClientGameTest.assertClose(
                    id + "_exposure", vanillaExposure, predictedExposure, EPSILON
                );

                double distance = Math.sqrt(player.distanceToSqr(center));
                float rawDamage = EXPOSURE.rawEntityDamage(radius, distance, predictedExposure);
                DamageSourceSnapshot predictedSource = new DamageSourceSnapshot(
                    DamageRange.exact(rawDamage),
                    EnumSet.of(DamageFlag.IS_EXPLOSION),
                    true,
                    1f,
                    false,
                    Optional.of(vec(center)),
                    "minecraft:explosion"
                );
                float predictedHealth = SIMULATOR.simulate(cleanSnapshot(20f), predictedSource).after().health();

                new ServerExplosion(
                    level,
                    null,
                    null,
                    null,
                    center,
                    radius,
                    false,
                    Explosion.BlockInteraction.KEEP
                ).explode();

                return new ValidationResult(
                    id,
                    predictedHealth,
                    player.getHealth(),
                    ValidationStatus.RUNTIME_CONFIRMED,
                    EPSILON
                );
            } finally {
                restore(level, restoredBlocks);
            }
        });
    }

    private static Map<BlockPos, BlockState> placeCover(ServerLevel level, ServerPlayer player, Vec3 center) {
        Vec3 delta = center.subtract(player.position());
        Vec3 horizontal = new Vec3(delta.x, 0d, delta.z).normalize();
        Vec3 lateral = new Vec3(-horizontal.z, 0d, horizontal.x);
        Vec3 wallCenter = player.position().add(horizontal.scale(Math.max(2d, delta.horizontalDistance() * 0.5d)));
        int baseY = BlockPos.containing(player.position()).getY();
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();

        for (int lateralOffset = -1; lateralOffset <= 1; lateralOffset++) {
            for (int yOffset = 0; yOffset <= 2; yOffset++) {
                Vec3 sample = wallCenter.add(lateral.scale(lateralOffset));
                BlockPos pos = BlockPos.containing(sample.x, baseY + yOffset, sample.z);
                originals.putIfAbsent(pos, level.getBlockState(pos));
                level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
            }
        }
        return Map.copyOf(originals);
    }

    private static void restore(ServerLevel level, Map<BlockPos, BlockState> originals) {
        for (Map.Entry<BlockPos, BlockState> entry : originals.entrySet()) {
            level.setBlockAndUpdate(entry.getKey(), entry.getValue());
        }
    }

    private static PlayerSnapshot cleanSnapshot(float health) {
        return new PlayerSnapshot(
            health,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static AabbSnapshot box(ServerPlayer player) {
        var box = player.getBoundingBox();
        return new AabbSnapshot(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    private static Vec3Snapshot vec(Vec3 value) {
        return new Vec3Snapshot(value.x, value.y, value.z);
    }

    private record ServerOcclusionView(ServerLevel level, ServerPlayer player) implements OcclusionView {
        @Override
        public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
            HitResult result = level.clip(new ClipContext(
                new Vec3(from.x(), from.y(), from.z()),
                new Vec3(to.x(), to.y(), to.z()),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                player
            ));
            return result.getType() != HitResult.Type.MISS;
        }

        @Override
        public OcclusionView withCandidateBlock(CoverCandidate candidate) {
            return this;
        }
    }
}
