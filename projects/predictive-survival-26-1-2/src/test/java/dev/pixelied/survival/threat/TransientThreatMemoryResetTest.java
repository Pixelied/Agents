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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransientThreatMemoryResetTest {
    @Test
    void cloudAttributionResetPreventsEntityIdReuseAcrossSessions() {
        AreaEffectCloudAttributionTracker tracker = new AreaEffectCloudAttributionTracker();
        tracker.observePredictedThreats(100, List.of(dragonBreath("42")));

        tracker.reset();
        WorldSnapshot annotated = tracker.annotate(101, new WorldSnapshot(
            List.of(cloud("77", new Vec3Snapshot(0.1, 0, 0.1))),
            List.of()
        ));

        assertFalse(annotated.entities().getFirst().properties().containsKey("cloud_instant_damage"));
    }

    @Test
    void splashStatusResetPreventsProjectileIdReuseAcrossSessions() {
        SplashStatusThreatMemory memory = new SplashStatusThreatMemory();
        PredictionContext visible = context(100, List.of(projectile("55")));
        memory.observePredictedThreats(visible, List.of(stackedWither("55")));

        memory.reset();
        List<ThreatEvent> replayed = memory.predict(context(101, List.of()));

        assertTrue(replayed.isEmpty(), "new session must not inherit a removed projectile's hidden status tail");
    }

    private static ThreatEvent dragonBreath(String projectileId) {
        Vec3Snapshot origin = new Vec3Snapshot(0, 0, 0);
        return new ThreatEvent(
            "projectile:" + projectileId + ":dragon_breath:0",
            ThreatKind.ENVIRONMENT,
            new TickWindow(1, 21),
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
            Optional.of(origin),
            Optional.of(origin),
            true,
            false,
            true,
            false
        );
    }

    private static ThreatEvent stackedWither(String projectileId) {
        return new ThreatEvent(
            "projectile:" + projectileId + ":stacked_status:wither:0",
            ThreatKind.PROJECTILE,
            new TickWindow(10, 20),
            new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.empty(),
                "minecraft:wither"
            ),
            Confidence.BOUNDED,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }

    private static WorldSnapshot.EntitySnapshot projectile(String id) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(-0.125, 0.875, -0.125, 0.125, 1.125, 0.125),
            Map.of()
        );
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
            Map.of("cloud_waiting", "true")
        );
    }

    private static PredictionContext context(long clientTick, List<WorldSnapshot.EntitySnapshot> entities) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
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
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(clientTick, 100, 10, new TickWindow(clientTick + 1, clientTick + 2)),
            EngineLimits.defaults()
        );
    }
}
