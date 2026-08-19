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
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StackedPotionStatusPredictorTest {
    private final EnvironmentPredictorRegistry predictors = EnvironmentPredictorRegistry.defaults();

    @Test
    void directStackedWitherEmitsOnlyHiddenTailBeyondLegacyStrongSchedule() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("potion_wither_duration_ticks", "40");
        properties.put("potion_wither_amplifier", "1");
        properties.put("potion_splash_radius", "4.0");
        properties.put("potion_duration_scale", "1.0");
        properties.put("potion_status_count", "2");
        properties.put("potion_status_0_kind", "wither");
        properties.put("potion_status_0_duration_ticks", "240");
        properties.put("potion_status_0_amplifier", "0");
        properties.put("potion_status_1_kind", "wither");
        properties.put("potion_status_1_duration_ticks", "40");
        properties.put("potion_status_1_amplifier", "1");

        List<ThreatEvent> tail = predictors.predict(context(splash(properties))).stream()
            .filter(event -> event.id().contains(":stacked_status:wither:"))
            .toList();

        assertFalse(tail.isEmpty());
        assertEquals(new TickWindow(45, 45), tail.getFirst().impact());
        for (ThreatEvent event : tail) {
            assertEquals(DamageRange.exact(1f), event.damage().rawDamage());
            assertEquals("minecraft:wither", event.damage().sourceKey());
            assertEquals(0f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
            assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
            assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
            assertFalse(event.blockable());
            assertTrue(event.impact().earliest() >= 45);
        }
    }

    @Test
    void sameAmplifierLongerReplacementDoesNotInventHiddenTail() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("potion_wither_duration_ticks", "100");
        properties.put("potion_wither_amplifier", "0");
        properties.put("potion_splash_radius", "4.0");
        properties.put("potion_duration_scale", "1.0");
        properties.put("potion_status_count", "2");
        properties.put("potion_status_0_kind", "wither");
        properties.put("potion_status_0_duration_ticks", "40");
        properties.put("potion_status_0_amplifier", "0");
        properties.put("potion_status_1_kind", "wither");
        properties.put("potion_status_1_duration_ticks", "100");
        properties.put("potion_status_1_amplifier", "0");

        List<ThreatEvent> tail = predictors.predict(context(splash(properties))).stream()
            .filter(event -> event.id().contains(":stacked_status:wither:"))
            .toList();

        assertTrue(tail.isEmpty());
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

    private static WorldSnapshot.EntitySnapshot splash(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "stacked:wither",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }
}
