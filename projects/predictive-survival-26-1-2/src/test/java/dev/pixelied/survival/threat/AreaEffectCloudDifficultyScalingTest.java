package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaEffectCloudDifficultyScalingTest {
    @Test
    void attributedCloudRetainsOwnerDifficultyScaling() {
        Vec3Snapshot origin = new Vec3Snapshot(0d, 0d, 0d);
        ThreatEvent predictedCloud = new ThreatEvent(
            "projectile:dragon:dragon_breath:0",
            ThreatKind.ENVIRONMENT,
            new TickWindow(1, 21),
            new DamageSourceSnapshot(
                DamageRange.exact(6f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                true,
                1f,
                false,
                Optional.of(origin),
                "minecraft:indirect_magic"
            ),
            Confidence.BOUNDED,
            Optional.of(origin),
            Optional.of(origin),
            true,
            false,
            true,
            false
        );

        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(100, List.of(predictedCloud));
        WorldSnapshot annotated = tracker.annotate(101, new WorldSnapshot(List.of(cloud("cloud", origin)), List.of()));
        WorldSnapshot.EntitySnapshot cloud = annotated.entities().getFirst();

        assertEquals("true", cloud.properties().get("cloud_scales_with_difficulty"));
        ThreatEvent liveThreat = new AreaEffectCloudPredictor().predict(context(annotated)).getFirst();
        assertTrue(liveThreat.damage().scalesWithDifficulty());
    }

    private static WorldSnapshot.EntitySnapshot cloud(String id, Vec3Snapshot position) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:area_effect_cloud",
            position,
            new Vec3Snapshot(0d, 0d, 0d),
            new AabbSnapshot(-3d, 0d, -3d, 3d, 0.5d, 3d),
            Map.of("cloud_waiting", "true", "observation_age_ticks", "1")
        );
    }

    private static PredictionContext context(WorldSnapshot world) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.HARD,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(-0.3d, 0d, -0.3d, 0.3d, 1.8d, 0.3d),
            new Vec3Snapshot(0d, 0d, 0d),
            new Vec3Snapshot(0d, 0d, 0d),
            Map.of()
        );
        return new PredictionContext(
            player,
            world,
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
    }
}
