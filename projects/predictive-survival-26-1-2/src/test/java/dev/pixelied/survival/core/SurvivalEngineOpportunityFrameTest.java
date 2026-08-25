package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityFamily;
import dev.pixelied.survival.threat.opportunity.OpportunityTimelineAssembler;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalEngineOpportunityFrameTest {
    @Test
    void opportunityCanMakePlanningLethalWithoutPollutingActualTimeline() {
        ThreatTimeline actual = new ThreatTimeline(List.of());
        LethalOpportunity opportunity = lethalOpportunityAtTick("opportunity:test:burst", 1);
        ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 128);
        SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(
            context(), actual, List.of(opportunity), planning, List.of()
        );

        assertTrue(frame.actualTimeline().events().isEmpty());
        assertEquals(1, frame.opportunities().size());
        assertEquals("opportunity:test:burst", frame.planningTimeline().events().getFirst().id());
    }

    private static LethalOpportunity lethalOpportunityAtTick(String id, long tick) {
        ThreatEvent event = new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(tick, tick),
            new DamageSourceSnapshot(
                DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:burst"
            ),
            Confidence.POTENTIAL,
            Optional.empty(), Optional.empty(), false, false, false, false
        );
        return new LethalOpportunity(id, OpportunityFamily.OTHER, event, Confidence.POTENTIAL, 1, Map.of());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)), EngineLimits.defaults()
        );
    }
}
