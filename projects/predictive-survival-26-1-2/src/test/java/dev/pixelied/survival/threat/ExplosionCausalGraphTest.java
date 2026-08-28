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

    @Test
    void adjacentChargedAnchorsKeepDistinctStableBlockSourcesWithoutChainRemoval() {
        WorldSnapshot.BlockSnapshot first = anchor(0.5, "anchor:0,0,0");
        WorldSnapshot.BlockSnapshot second = anchor(4.5, "anchor:4,0,0");
        PredictionContext context = context(List.of(), List.of(first, second));
        ExplosionPredictor predictor = new ExplosionPredictor();
        List<ThreatEvent> predicted = predictor.predict(context);

        CausalThreatTimeline causal = predictor.causalize(context, new ThreatTimeline(predicted));

        assertEquals(2, predicted.size());
        ThreatEvent firstEvent = eventAt(predicted, first.position());
        ThreatEvent secondEvent = eventAt(predicted, second.position());
        assertEquals("block:anchor:0,0,0", causal.sourceId(firstEvent));
        assertEquals("block:anchor:4,0,0", causal.sourceId(secondEvent));
        assertFalse(causal.transitionsAfter(firstEvent.id()).contains(
            new ThreatTransition.RemoveSource("block:anchor:4,0,0")
        ));
        assertFalse(causal.transitionsAfter(secondEvent.id()).contains(
            new ThreatTransition.RemoveSource("block:anchor:0,0,0")
        ));
    }

    private static ThreatEvent event(List<ThreatEvent> events, String id) {
        return events.stream().filter(event -> event.id().equals(id)).findFirst().orElseThrow();
    }

    private static ThreatEvent eventAt(List<ThreatEvent> events, Vec3Snapshot position) {
        return events.stream()
            .filter(event -> event.sourcePosition().filter(position::equals).isPresent())
            .findFirst()
            .orElseThrow();
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

    private static WorldSnapshot.BlockSnapshot anchor(double x, String removalGroup) {
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(x, 0.5, 0.5),
            "minecraft:respawn_anchor",
            true,
            Map.of(
                "full_collision_cube", "true",
                "anchor_explodes", "true",
                "anchor_charge", "1",
                "explosion_radius", "5.0",
                "triggerable", "true",
                "source_key", "minecraft:bad_respawn_point",
                "scales_with_difficulty", "true",
                "pre_explosion_remove_group", removalGroup
            )
        );
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
        return context(entities, List.of());
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        Vec3Snapshot position = new Vec3Snapshot(2.5, 0, 0.5);
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
            new AabbSnapshot(2.2, 0, 0.2, 2.8, 1.8, 0.8),
            position,
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 100, 0, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
