package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LingeringStatusCloudAttributionTrackerTest {
    @Test
    void witherCloudKeepsSourceFloorAndAbsoluteFirstDamageWindow() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(100, List.of(statusThreat(
            "52", "lingering_status", "wither", new Vec3Snapshot(4, 0, 2),
            new TickWindow(20, 21), "minecraft:wither", 0f
        )));

        WorldSnapshot annotated = tracker.annotate(104, world(cloud("81", new Vec3Snapshot(4.3, 0, 2.1))));
        WorldSnapshot.EntitySnapshot matched = annotated.entities().getFirst();

        assertEquals("1.0", matched.properties().get("cloud_instant_damage"));
        assertEquals("minecraft:wither", matched.properties().get("cloud_source_key"));
        assertEquals("0.0", matched.properties().get("cloud_application_health_threshold_exclusive"));
        assertEquals("16", matched.properties().get("cloud_first_damage_earliest_ticks"));
        assertEquals("17", matched.properties().get("cloud_first_damage_latest_ticks"));
        assertEquals("lingering_status", matched.properties().get("cloud_attribution"));
    }

    @Test
    void poisonCloudKeepsOneHealthApplicationFloor() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(50, List.of(statusThreat(
            "12", "lingering_status", "poison", new Vec3Snapshot(1, 0, 1),
            new TickWindow(10, 10), "minecraft:magic", 1f
        )));

        WorldSnapshot annotated = tracker.annotate(54, world(cloud("90", new Vec3Snapshot(1.2, 0, 1.1))));
        assertEquals(
            "1.0",
            annotated.entities().getFirst().properties().get("cloud_application_health_threshold_exclusive")
        );
    }

    @Test
    void hiddenWitherTailStaysSeparateFromStrongLingeringPhase() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        Vec3Snapshot origin = new Vec3Snapshot(4, 0, 2);
        tracker.observePredictedThreats(100, List.of(
            statusThreat(
                "52", "lingering_status", "wither", origin,
                new TickWindow(15, 15), "minecraft:wither", 0f
            ),
            statusThreat(
                "52", "lingering_stacked_status", "wither", origin,
                new TickWindow(55, 55), "minecraft:wither", 0f
            )
        ));

        WorldSnapshot annotated = tracker.annotate(104, world(cloud("81", new Vec3Snapshot(4.3, 0, 2.1))));
        WorldSnapshot.EntitySnapshot matched = annotated.entities().getFirst();

        assertEquals("2", matched.properties().get("cloud_hazard_count"));
        assertEquals("lingering_status", matched.properties().get("cloud_hazard_0_attribution"));
        assertEquals("11", matched.properties().get("cloud_hazard_0_first_damage_earliest_ticks"));
        assertEquals("lingering_stacked_status", matched.properties().get("cloud_hazard_1_attribution"));
        assertEquals("51", matched.properties().get("cloud_hazard_1_first_damage_earliest_ticks"));
        assertEquals("minecraft:wither", matched.properties().get("cloud_hazard_1_source_key"));
    }

    private static ThreatEvent statusThreat(
        String projectileId,
        String marker,
        String status,
        Vec3Snapshot origin,
        TickWindow impact,
        String sourceKey,
        float floor
    ) {
        return new ThreatEvent(
            "projectile:" + projectileId + ":" + marker + ":" + status + ":0",
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
            Confidence.BOUNDED,
            Optional.of(new Vec3Snapshot(0, 0, 0)),
            Optional.of(origin),
            true,
            false,
            true,
            false
        );
    }

    private static WorldSnapshot world(WorldSnapshot.EntitySnapshot... entities) {
        return new WorldSnapshot(List.of(entities), List.of());
    }

    private static WorldSnapshot.EntitySnapshot cloud(String id, Vec3Snapshot position) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:area_effect_cloud",
            position,
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(
                position.x() - 3, position.y(), position.z() - 3,
                position.x() + 3, position.y() + 0.5, position.z() + 3
            ),
            Map.of("cloud_waiting", "true", "observation_age_ticks", "1")
        );
    }
}
