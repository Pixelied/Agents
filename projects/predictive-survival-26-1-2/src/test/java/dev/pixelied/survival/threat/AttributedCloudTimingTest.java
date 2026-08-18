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

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttributedCloudTimingTest {
    @Test
    void cloudUsesPreservedFirstDamageWindowInsteadOfGenericReapplicationGuess() {
        ThreatEvent event = new AreaEffectCloudPredictor().predict(context(cloud(Map.of(
            "cloud_waiting", "true",
            "cloud_reapplication_delay_ticks", "20",
            "cloud_instant_damage", "1",
            "cloud_source_key", "minecraft:wither",
            "cloud_application_health_threshold_exclusive", "0",
            "cloud_first_damage_earliest_ticks", "16",
            "cloud_first_damage_latest_ticks", "17",
            "cloud_attribution", "lingering_status"
        )))).getFirst();

        assertEquals(new TickWindow(16, 17), event.impact());
        assertEquals("minecraft:wither", event.damage().sourceKey());
        assertEquals(0f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
    }

    @Test
    void poisonCloudPreservesOneHealthFloor() {
        ThreatEvent event = new AreaEffectCloudPredictor().predict(context(cloud(Map.of(
            "cloud_waiting", "true",
            "cloud_reapplication_delay_ticks", "20",
            "cloud_instant_damage", "1",
            "cloud_source_key", "minecraft:magic",
            "cloud_application_health_threshold_exclusive", "1",
            "cloud_first_damage_earliest_ticks", "6",
            "cloud_first_damage_latest_ticks", "6",
            "cloud_attribution", "lingering_status"
        )))).getFirst();

        assertEquals(new TickWindow(6, 6), event.impact());
        assertEquals(1f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot cloud) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(cloud), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot cloud(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "cloud:timed",
            "minecraft:area_effect_cloud",
            new Vec3Snapshot(0.3, 0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(-2.7, 0, -2.7, 3.3, 0.5, 3.3),
            properties
        );
    }
}
