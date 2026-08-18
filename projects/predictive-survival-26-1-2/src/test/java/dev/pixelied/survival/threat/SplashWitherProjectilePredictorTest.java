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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SplashWitherProjectilePredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void splashWitherOneSchedulesVanillaTicksWithoutPoisonHealthFloor() {
        List<ThreatEvent> wither = predictor.predict(context(List.of(splashWither(100, 0)))).stream()
            .filter(event -> event.id().contains(":wither:"))
            .toList();

        assertEquals(2, wither.size());
        assertEquals(List.of(
            new TickWindow(25, 25),
            new TickWindow(65, 65)
        ), wither.stream().map(ThreatEvent::impact).toList());
        for (ThreatEvent event : wither) {
            assertEquals(DamageRange.exact(1f), event.damage().rawDamage());
            assertEquals("minecraft:wither", event.damage().sourceKey());
            assertEquals(0f, event.damage().applicationHealthThresholdExclusive(), 0.0001f);
            assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
            assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
            assertFalse(event.blockable());
        }
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot splashWither(int durationTicks, int amplifier) {
        return new WorldSnapshot.EntitySnapshot(
            "wither:1",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            Map.of(
                "potion_wither_duration_ticks", Integer.toString(durationTicks),
                "potion_wither_amplifier", Integer.toString(amplifier),
                "potion_splash_radius", "4.0"
            )
        );
    }
}
