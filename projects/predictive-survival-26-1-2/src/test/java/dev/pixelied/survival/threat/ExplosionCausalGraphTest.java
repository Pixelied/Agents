package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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
import dev.pixelied.survival.timeline.CausalThreatTimeline;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTransition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionCausalGraphTest {
    @Test
    void observedCrystalBlastRemovesOnlyCrystalSourcesInsideVanillaDoubleRadius() {
        WorldSnapshot.EntitySnapshot source = crystal("201", 0.0);
        WorldSnapshot.EntitySnapshot inside = crystal("202", 11.9);
        WorldSnapshot.EntitySnapshot outside = crystal("203", 12.1);
        PredictionContext context = context(List.of(source, inside, outside));
        ExplosionPredictor predictor = new ExplosionPredictor();
        List<ThreatEvent> predicted = predictor.predict(context);

        CausalThreatTimeline causal = predictor.causalize(context, new ThreatTimeline(predicted));

        ThreatEvent sourceEvent = event(predicted, "explosion:201");
        ThreatEvent insideEvent = event(predicted, "explosion:202");
        ThreatEvent outsideEvent = event(predicted, "explosion:203");
        assertEquals("entity:201", causal.sourceId(sourceEvent));
        assertEquals("entity:202", causal.sourceId(insideEvent));
        assertEquals("entity:203", causal.sourceId(outsideEvent));
        assertTrue(causal.transitionsAfter(sourceEvent.id()).contains(
            new ThreatTransition.RemoveSource("entity:202")
        ));
        assertFalse(causal.transitionsAfter(sourceEvent.id()).contains(
            new ThreatTransition.RemoveSource("entity:203")
        ));
    }

    private static ThreatEvent event(List<ThreatEvent> events, String id) {
        return events.stream().filter(event -> event.id().equals(id)).findFirst().orElseThrow();
    }

    private static WorldSnapshot.EntitySnapshot crystal(String id, double x) {
        return new WorldSnapshot.EntitySnapshot(
            id,
            "minecraft:end_crystal",
            new Vec3Snapshot(x, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(x - 1, 0, -1, x + 1, 2, 1),
            Map.of(
                "explosion_radius", "6.0",
                "triggerable", "true",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
        Vec3Snapshot position = new Vec3Snapshot(6.0, 0, 0);
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
            new AabbSnapshot(5.7, 0, -0.3, 6.3, 1.8, 0.3),
            position,
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(0, 100, 0, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
