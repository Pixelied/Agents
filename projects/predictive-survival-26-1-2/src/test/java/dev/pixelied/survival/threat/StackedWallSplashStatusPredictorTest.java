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

class StackedWallSplashStatusPredictorTest {
    private final StackedPotionStatusPredictor predictor = new StackedPotionStatusPredictor();

    @Test
    void staleWallSplashKeepsConservativeHiddenWitherTail() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("potion_wither_duration_ticks", "40");
        properties.put("potion_wither_amplifier", "1");
        properties.put("potion_duration_scale", "1.0");
        properties.put("potion_splash_radius", "4.0");
        properties.put("observation_age_ticks", "1");
        properties.put("projectile_age_ticks", "1");
        properties.put("potion_status_count", "2");
        properties.put("potion_status_0_kind", "wither");
        properties.put("potion_status_0_duration_ticks", "240");
        properties.put("potion_status_0_amplifier", "0");
        properties.put("potion_status_1_kind", "wither");
        properties.put("potion_status_1_duration_ticks", "40");
        properties.put("potion_status_1_amplifier", "1");

        List<ThreatEvent> tail = predictor.predict(context(splash(properties), wallAt(5))).stream()
            .filter(event -> event.id().contains(":stacked_status:wither:"))
            .toList();

        assertFalse(tail.isEmpty(),
            "one-tick observation uncertainty must not discard a damaging hidden Wither tail after a wall splash");
        assertTrue(tail.stream().anyMatch(event -> event.impact().latest() > 30),
            "the bounded forecast must extend beyond the short Wither II schedule");
        for (ThreatEvent event : tail) {
            assertEquals(Confidence.BOUNDED, event.confidence());
            assertEquals(new DamageRange(0f, 1f), event.damage().rawDamage());
            assertEquals("minecraft:wither", event.damage().sourceKey());
            assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
            assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
            assertFalse(event.blockable());
        }
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        WorldSnapshot.BlockSnapshot wall
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of(wall)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot splash(Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            "stacked:wall:wither",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            properties
        );
    }

    private static WorldSnapshot.BlockSnapshot wallAt(int x) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
    }
}
