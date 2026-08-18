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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AreaEffectCloudAttributionTrackerTest {
    @Test
    void matchingCloudInheritsObservedDragonBreathHazard() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(100, List.of(dragonBreath("42", new Vec3Snapshot(4, 0, 2), new TickWindow(3, 23))));

        WorldSnapshot annotated = tracker.annotate(104, world(
            cloud("77", new Vec3Snapshot(4.35, 0, 2.15)),
            cloud("78", new Vec3Snapshot(12, 0, 12))
        ));

        WorldSnapshot.EntitySnapshot matched = entity(annotated, "77");
        assertEquals("6.0", matched.properties().get("cloud_instant_damage"));
        assertEquals("minecraft:indirect_magic", matched.properties().get("cloud_source_key"));
        assertEquals("20", matched.properties().get("cloud_reapplication_delay_ticks"));
        assertFalse(entity(annotated, "78").properties().containsKey("cloud_instant_damage"));
    }

    @Test
    void lingeringPotionForecastAttributesOnlyMatchingCloud() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(100, List.of(lingeringCloud(
            "52",
            new Vec3Snapshot(4, 0, 2),
            new TickWindow(14, 14),
            6f
        )));

        WorldSnapshot annotated = tracker.annotate(105, world(
            cloud("81", new Vec3Snapshot(4.3, 0, 2.1)),
            cloud("82", new Vec3Snapshot(8, 0, 8))
        ));

        WorldSnapshot.EntitySnapshot matched = entity(annotated, "81");
        assertEquals("6.0", matched.properties().get("cloud_instant_damage"));
        assertEquals("minecraft:indirect_magic", matched.properties().get("cloud_source_key"));
        assertEquals("20", matched.properties().get("cloud_reapplication_delay_ticks"));
        assertEquals("lingering_potion", matched.properties().get("cloud_attribution"));
        assertFalse(entity(annotated, "82").properties().containsKey("cloud_instant_damage"));
    }

    @Test
    void matchedAttributionPersistsUntilCloudDisappears() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(50, List.of(dragonBreath("9", new Vec3Snapshot(0, 0, 0), new TickWindow(1, 21))));

        assertTrue(entity(tracker.annotate(51, world(cloud("31", new Vec3Snapshot(0.2, 0, 0.1)))), "31")
            .properties().containsKey("cloud_instant_damage"));
        assertTrue(entity(tracker.annotate(60, world(cloud("31", new Vec3Snapshot(1.5, 0, 0.1)))), "31")
            .properties().containsKey("cloud_instant_damage"));

        tracker.annotate(61, world());
        assertFalse(entity(tracker.annotate(62, world(cloud("31", new Vec3Snapshot(0.1, 0, 0.1)))), "31")
            .properties().containsKey("cloud_instant_damage"));
    }

    @Test
    void expiredPredictionCannotPoisonUnrelatedFutureCloud() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(10, List.of(dragonBreath("5", new Vec3Snapshot(2, 0, 2), new TickWindow(2, 22))));

        WorldSnapshot annotated = tracker.annotate(40, world(cloud("88", new Vec3Snapshot(2.1, 0, 2.1))));
        assertFalse(entity(annotated, "88").properties().containsKey("cloud_instant_damage"));
    }

    @Test
    void nonCloudProjectileThreatNeverAttributesCloud() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        ThreatEvent ordinary = new ThreatEvent(
            "projectile:5:direct",
            ThreatKind.PROJECTILE,
            new TickWindow(2, 2),
            new DamageSourceSnapshot(
                DamageRange.exact(6f), EnumSet.of(DamageFlag.IS_PROJECTILE), false, 1f, false,
                Optional.of(new Vec3Snapshot(2, 0, 2)), "minecraft:arrow"
            ),
            Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 0)),
            Optional.of(new Vec3Snapshot(2, 0, 2)),
            true,
            true,
            true,
            false
        );
        tracker.observePredictedThreats(10, List.of(ordinary));

        WorldSnapshot annotated = tracker.annotate(12, world(cloud("90", new Vec3Snapshot(2, 0, 2))));
        assertFalse(entity(annotated, "90").properties().containsKey("cloud_instant_damage"));
    }

    private static ThreatEvent dragonBreath(String projectileId, Vec3Snapshot origin, TickWindow impact) {
        return new ThreatEvent(
            "projectile:" + projectileId + ":dragon_breath:0",
            ThreatKind.ENVIRONMENT,
            impact,
            new DamageSourceSnapshot(
                DamageRange.exact(6f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(origin),
                "minecraft:indirect_magic"
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

    private static ThreatEvent lingeringCloud(
        String projectileId,
        Vec3Snapshot origin,
        TickWindow impact,
        float rawDamage
    ) {
        return new ThreatEvent(
            "projectile:" + projectileId + ":lingering_cloud:0",
            ThreatKind.ENVIRONMENT,
            impact,
            new DamageSourceSnapshot(
                DamageRange.exact(rawDamage),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(origin),
                "minecraft:indirect_magic"
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

    private static WorldSnapshot.EntitySnapshot entity(WorldSnapshot world, String id) {
        return world.entities().stream()
            .filter(entity -> entity.id().equals(id))
            .findFirst()
            .orElseThrow();
    }
}
