package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpaqueTippedArrowPredictorTest {
    private final ProjectilePredictor projectilePredictor = new ProjectilePredictor();
    private final EnvironmentPredictorRegistry supplementalPredictors = EnvironmentPredictorRegistry.defaults();

    @Test
    void tippedArrowWithOpaquePotionPayloadAddsConservativeCollisionHazard() {
        PredictionContext context = context(arrow(Map.of(
            "base_damage", "2.0",
            "critical", "false",
            "arrow_tipped", "true"
        )));
        ThreatEvent direct = projectilePredictor.predict(context).stream()
            .filter(event -> event.id().equals("projectile:arrow:tipped:direct"))
            .findFirst()
            .orElseThrow();
        ThreatEvent payload = supplementalPredictors.predict(context).stream()
            .filter(event -> event.id().equals("projectile:arrow:tipped:opaque_potion"))
            .findFirst()
            .orElseThrow();

        assertEquals(direct.impact(), payload.impact());
        assertEquals(new DamageRange(0f, Float.MAX_VALUE), payload.damage().rawDamage());
        assertEquals("minecraft:indirect_magic", payload.damage().sourceKey());
        assertTrue(payload.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertFalse(payload.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertEquals(direct.blockable(), payload.blockable());
        assertEquals(Confidence.UNKNOWN, payload.confidence());
    }

    @Test
    void ordinaryArrowDoesNotInventOpaquePotionPayload() {
        List<ThreatEvent> events = supplementalPredictors.predict(context(arrow(Map.of(
            "base_damage", "2.0",
            "critical", "false",
            "arrow_tipped", "false"
        ))));

        assertTrue(events.stream().noneMatch(event -> event.id().contains(":opaque_potion")));
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot entity) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of()),
            new TimingSnapshot(0, 100, 10, new dev.pixelied.survival.core.TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot arrow(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "arrow:tipped",
            "minecraft:arrow",
            new Vec3Snapshot(0, 1.9, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(-0.125, 1.775, 0.175, 0.125, 2.025, 0.425),
            properties
        );
    }
}
