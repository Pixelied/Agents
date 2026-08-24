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
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObservationOverflowPredictorTest {
    @Test
    void observationOverflowFailsClosedAcrossDeathProtection() {
        PredictionContext context = context(marker(3));

        List<ThreatEvent> events = new ObservationOverflowPredictor().predict(context);

        assertFailClosedOverflow(context, events);
    }

    @Test
    void topLevelRegistryCannotForgetObservationOverflowSafety() {
        PredictionContext context = context(marker(2));

        List<ThreatEvent> events = new ThreatPredictorRegistry(List.of()).predictAll(context);

        assertFailClosedOverflow(context, events);
    }

    private static void assertFailClosedOverflow(PredictionContext context, List<ThreatEvent> events) {
        assertEquals(1, events.size());
        ThreatEvent event = events.getFirst();
        assertEquals(Confidence.UNKNOWN, event.confidence());
        assertEquals(new TickWindow(0, context.limits().maxDecisionHistory()), event.impact());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_INVULNERABILITY));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_COOLDOWN));
        assertFalse(
            new ThreatTimelineSimulator().simulate(context.player(), new ThreatTimeline(events)).survived(),
            "a single death-protection item must never convert an observation-budget overflow into a safe timeline"
        );
    }

    private static WorldSnapshot.EntitySnapshot marker(int omitted) {
        return new WorldSnapshot.EntitySnapshot(
            ObservationOverflowPredictor.MARKER_TYPE,
            ObservationOverflowPredictor.MARKER_TYPE,
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(0, 0, 0, 0, 0, 0),
            Map.of("omitted_relevant_entities", Integer.toString(omitted))
        );
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot marker) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            new DeathProtectionSnapshot(true, false),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(marker), List.of()),
            new TimingSnapshot(0, 0d, 0d, new TickWindow(0, 0)),
            new EngineLimits(8, 32, 80, 128)
        );
    }
}
