package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
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
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightningPredictorTest {
    @Test
    void visibleStrikeCoveringPlayerEmitsMaximumCooldownEligibleDamageSequence() {
        List<ThreatEvent> events = new LightningPredictor().predict(context(lightning(0.0, 0.0, 0.0)));

        assertEquals(4, events.size());
        assertEquals(List.of(
            new TickWindow(0, 0),
            new TickWindow(10, 10),
            new TickWindow(20, 20),
            new TickWindow(30, 30)
        ), events.stream().map(ThreatEvent::impact).toList());

        for (ThreatEvent event : events) {
            assertEquals(Confidence.POTENTIAL, event.confidence());
            assertEquals("minecraft:lightning_bolt", event.damage().sourceKey());
            assertEquals(5f, event.damage().rawDamage().min(), 0.0001f);
            assertEquals(5f, event.damage().rawDamage().max(), 0.0001f);
            assertFalse(event.damage().scalesWithDifficulty());
            assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
            assertTrue(event.damage().flags().contains(DamageFlag.IS_LIGHTNING));
            assertFalse(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
            assertFalse(event.blockable());
        }
    }

    @Test
    void strikeOutsideVanillaThunderHitBoxProducesNoThreat() {
        assertTrue(new LightningPredictor().predict(context(lightning(10.0, 0.0, 0.0))).isEmpty());
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot lightning) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(lightning), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot lightning(double x, double y, double z) {
        return new WorldSnapshot.EntitySnapshot(
            "lightning:1",
            "minecraft:lightning_bolt",
            new Vec3Snapshot(x, y, z),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(x, y, z, x, y, z),
            Map.of()
        );
    }
}
