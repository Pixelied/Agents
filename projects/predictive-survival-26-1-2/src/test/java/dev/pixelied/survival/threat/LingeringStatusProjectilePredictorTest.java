package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LingeringStatusProjectilePredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void lingeringWitherTwoHundredSchedulesQuarterDurationFirstTick() {
        List<ThreatEvent> wither = predictor.predict(context(lingering(Map.of(
            "potion_lingering", "true",
            "potion_wither_duration_ticks", "200",
            "potion_wither_amplifier", "0"
        )))).stream().filter(event -> "minecraft:wither".equals(event.damage().sourceKey())).toList();

        assertEquals(1, wither.size());
        ThreatEvent event = wither.getFirst();
        assertEquals(new TickWindow(25, 25), event.impact());
        assertEquals(DamageRange.exact(1f), event.damage().rawDamage());
        assertEquals(0f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
        assertFalse(event.blockable());
        assertTrue(event.id().contains(":lingering_status:wither:"));
    }

    @Test
    void lingeringWitherHundredDoesNotInventDamageBeforeEffectExpires() {
        List<ThreatEvent> events = predictor.predict(context(lingering(Map.of(
            "potion_lingering", "true",
            "potion_wither_duration_ticks", "100",
            "potion_wither_amplifier", "0"
        ))));

        assertTrue(events.stream().noneMatch(event -> "minecraft:wither".equals(event.damage().sourceKey())));
    }

    @Test
    void lingeringPoisonHundredSchedulesCloudApplicationDamageWithHealthFloor() {
        List<ThreatEvent> poison = predictor.predict(context(lingering(Map.of(
            "potion_lingering", "true",
            "potion_poison_duration_ticks", "100",
            "potion_poison_amplifier", "0"
        )))).stream().filter(event -> "minecraft:magic".equals(event.damage().sourceKey())).toList();

        assertEquals(1, poison.size());
        ThreatEvent event = poison.getFirst();
        assertEquals(new TickWindow(15, 15), event.impact());
        assertEquals(DamageRange.exact(1f), event.damage().rawDamage());
        assertEquals(1f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
        assertFalse(event.blockable());
        assertTrue(event.id().contains(":lingering_status:poison:"));
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
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot lingering(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "lingering:status",
            "minecraft:lingering_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }
}
