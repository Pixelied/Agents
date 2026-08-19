package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LingeringInstantDecisionHorizonPredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void lateLingeringHarmingCollisionKeepsFirstCloudDamagePastTrajectoryHorizon() {
        ThreatEvent cloud = predictor.predict(context(EngineLimits.defaults())).stream()
            .filter(event -> event.id().endsWith(":lingering_cloud:0"))
            .findFirst()
            .orElseThrow();

        assertTrue(cloud.impact().earliest() > 80L, "cloud consequence must occur after the trajectory search horizon");
        assertTrue(cloud.impact().latest() <= 128L, "cloud consequence must remain inside the decision horizon");
    }

    @Test
    void lateLingeringHarmingCloudIsDroppedWhenDecisionHorizonEndsBeforeApplication() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 85);
        assertTrue(
            predictor.predict(context(limits)).stream().noneMatch(event -> event.id().endsWith(":lingering_cloud:0")),
            "a cloud application after the configured decision horizon must not be emitted"
        );
    }

    private static PredictionContext context(EngineLimits limits) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot potion = new WorldSnapshot.EntitySnapshot(
            "lingering:late",
            "minecraft:lingering_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(0.121, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            Map.of(
                "no_gravity", "true",
                "potion_instant_damage", "12.0",
                "potion_lingering", "true",
                "potion_source_key", "minecraft:indirect_magic"
            )
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(potion), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            limits
        );
    }
}
