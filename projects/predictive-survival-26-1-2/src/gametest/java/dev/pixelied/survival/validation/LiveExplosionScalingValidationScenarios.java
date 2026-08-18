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
import dev.pixelied.survival.threat.ExplosionExposure;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class LiveExplosionScalingValidationScenarios {
    private static final float EPSILON = 0.0001f;
    private static final float RADIUS = 4f;
    private static final double DISTANCE = 7d;
    private static final ExplosionExposure EXPOSURE = new ExplosionExposure();
    private static final DamageSimulator SIMULATOR = new DamageSimulator();

    private LiveExplosionScalingValidationScenarios() {
    }

    static List<ValidationResult> runtimeSlice(
        ClientGameTestContext context,
        TestSingleplayerContext singleplayer
    ) {
        if (context == null) throw new NullPointerException("context");
        return List.of(
            validate(singleplayer, Difficulty.EASY, DifficultySnapshot.EASY, "explosion_difficulty_easy"),
            validate(singleplayer, Difficulty.HARD, DifficultySnapshot.HARD, "explosion_difficulty_hard")
        );
    }

    private static ValidationResult validate(
        TestSingleplayerContext singleplayer,
        Difficulty vanillaDifficulty,
        DifficultySnapshot predictedDifficulty,
        String id
    ) {
        return singleplayer.getServer().computeOnServer(server -> {
            server.setDifficulty(vanillaDifficulty, true);
            ServerPlayer player = SurvivalValidationClientGameTest.onlyPlayer(server);
            SurvivalValidationClientGameTest.reset(player, 20f);
            player.setDeltaMovement(Vec3.ZERO);
            ServerLevel level = (ServerLevel) player.level();

            Vec3 center = player.position().add(0d, 0.9d, DISTANCE);
            float seen = ServerExplosion.getSeenPercent(center, player);
            double distance = Math.sqrt(player.distanceToSqr(center));
            float rawDamage = EXPOSURE.rawEntityDamage(RADIUS, distance, seen);

            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(rawDamage),
                EnumSet.of(DamageFlag.IS_EXPLOSION),
                true,
                1f,
                false,
                Optional.of(new Vec3Snapshot(center.x, center.y, center.z)),
                "minecraft:explosion"
            );
            float predictedHealth = SIMULATOR.simulate(cleanSnapshot(predictedDifficulty), source).after().health();

            new ServerExplosion(
                level,
                null,
                null,
                null,
                center,
                RADIUS,
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
        });
    }

    private static PlayerSnapshot cleanSnapshot(DifficultySnapshot difficulty) {
        return new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            difficulty,
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
}
