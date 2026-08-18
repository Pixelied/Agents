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
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CombinedLingeringCloudAttributionTest {
    @Test
    void oneCloudRetainsPoisonAndWitherFromSameProjectile() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        Vec3Snapshot origin = new Vec3Snapshot(0.3, 0, 0.3);
        tracker.observePredictedThreats(100, List.of(
            statusThreat("52", "poison", origin, new TickWindow(10, 10), "minecraft:magic", 1f),
            statusThreat("52", "wither", origin, new TickWindow(20, 20), "minecraft:wither", 0f)
        ));

        WorldSnapshot annotated = tracker.annotate(104, new WorldSnapshot(List.of(cloud("81", origin)), List.of()));
        List<ThreatEvent> threats = new AreaEffectCloudPredictor().predict(context(annotated));

        assertEquals(2, threats.size());
        ThreatEvent poison = threats.stream()
            .filter(event -> "minecraft:magic".equals(event.damage().sourceKey()))
            .findFirst().orElseThrow();
        ThreatEvent wither = threats.stream()
            .filter(event -> "minecraft:wither".equals(event.damage().sourceKey()))
            .findFirst().orElseThrow();

        assertEquals(new TickWindow(6, 6), poison.impact());
        assertEquals(1f, poison.damage().applicationHealthThresholdExclusive(), 0.0001f);
        assertEquals(new TickWindow(16, 16), wither.impact());
        assertEquals(0f, wither.damage().applicationHealthThresholdExclusive(), 0.0001f);
    }

    private static ThreatEvent statusThreat(
        String projectileId,
        String status,
        Vec3Snapshot origin,
        TickWindow impact,
        String sourceKey,
        float floor
    ) {
        return new ThreatEvent(
            "projectile:" + projectileId + ":lingering_status:" + status + ":0",
            ThreatKind.ENVIRONMENT,
            impact,
            new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(origin),
                sourceKey,
                floor
            ),
            Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 0)),
            Optional.of(origin),
            true,
            false,
            true,
            false
        );
    }

    private static WorldSnapshot.EntitySnapshot cloud(String id, Vec3Snapshot position) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:area_effect_cloud",
            position,
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(-2.7, 0, -2.7, 3.3, 0.5, 3.3),
            Map.of("cloud_waiting", "true", "observation_age_ticks", "1")
        );
    }

    private static PredictionContext context(WorldSnapshot world) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            world,
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
