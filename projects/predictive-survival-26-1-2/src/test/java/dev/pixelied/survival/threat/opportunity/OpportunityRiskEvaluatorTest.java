package dev.pixelied.survival.threat.opportunity;

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
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
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

class OpportunityRiskEvaluatorTest {
    @Test
    void evaluatesMutuallyExclusiveLethalOpportunitiesAsSeparateRiskBranches() {
        PredictionContext context = context();
        ThreatTimeline actual = new ThreatTimeline(List.of());
        LethalOpportunity first = opportunity("opportunity:bed:test:first", 0, 100f);
        LethalOpportunity second = opportunity("opportunity:bed:test:second", 0, 80f);

        OpportunityRiskEvaluator.RiskAssessment assessment = new OpportunityRiskEvaluator().assess(
            context,
            actual,
            List.of(first, second)
        );

        assertTrue(assessment.requiresDeathProtection());
        assertEquals(2, assessment.lethalScenarios().size());
        assertEquals(1, assessment.criticalTimeline().orElseThrow().events().size());
        assertEquals("opportunity:bed:test:first", assessment.criticalTimeline().orElseThrow().events().getFirst().id());
    }

    private static LethalOpportunity opportunity(String id, long tick, float damage) {
        ThreatEvent projected = new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            new TickWindow(tick, tick),
            new DamageSourceSnapshot(
                DamageRange.exact(damage),
                Set.of(),
                false,
                1f,
                false,
                Optional.empty(),
                "minecraft:bad_respawn_point"
            ),
            Confidence.POTENTIAL,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
        return new LethalOpportunity(id, OpportunityFamily.BED, projected, Confidence.POTENTIAL, 2, Map.of());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            4f,
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
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
