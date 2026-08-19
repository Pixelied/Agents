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

class EvokerFangsPredictorTest {
    @Test
    void startedFangsPredictSevenTickDamageWindow() {
        ThreatEvent event = new EvokerFangsPredictor().predict(context(Map.of(
            "evoker_fangs_started", "true",
            "evoker_fangs_elapsed_ticks", "3"
        ), 0.2)).getFirst();

        assertEquals(Confidence.BOUNDED, event.confidence());
        assertEquals(new TickWindow(0, 4), event.impact());
        assertEquals(6f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(6f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:indirect_magic", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertFalse(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertTrue(event.blockable());
    }

    @Test
    void opaqueFangsOwnerIsConservativelyDifficultyScaled() {
        ThreatEvent event = new EvokerFangsPredictor().predict(context(Map.of(
            "evoker_fangs_started", "true",
            "evoker_fangs_elapsed_ticks", "0"
        ), 0.2)).getFirst();

        assertTrue(event.damage().scalesWithDifficulty());
    }

    @Test
    void visiblePreEventFangsRemainPotentialBecauseWarmupIsServerOpaque() {
        ThreatEvent event = new EvokerFangsPredictor().predict(context(Map.of(
            "evoker_fangs_started", "false"
        ), 0.2)).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(0, EngineLimits.defaults().maxDecisionHistory()), event.impact());
    }

    @Test
    void fangsOutsideInflatedHitboxProduceNoThreat() {
        assertTrue(new EvokerFangsPredictor().predict(context(Map.of(
            "evoker_fangs_started", "true",
            "evoker_fangs_elapsed_ticks", "0"
        ), 2.0)).isEmpty());
    }

    private static PredictionContext context(Map<String, String> properties, double fangX) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot fangs = new WorldSnapshot.EntitySnapshot(
            "fangs:1", "minecraft:evoker_fangs",
            new Vec3Snapshot(fangX, 0, 0.3), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(fangX - 0.25, 0, 0.05, fangX + 0.25, 0.8, 0.55), properties
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(fangs), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
