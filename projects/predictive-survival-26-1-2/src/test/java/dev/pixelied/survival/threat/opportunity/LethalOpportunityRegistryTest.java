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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LethalOpportunityRegistryTest {
    @Test
    void registryOrdersByEarliestImpactThenWorstDamageAndCaps() {
        EngineLimits limits = new EngineLimits(4, 32, 80, 128, 2);
        PredictionContext context = context(limits);
        LethalOpportunity late = opportunity("opportunity:test:late", 3, 30f);
        LethalOpportunity earlyLow = opportunity("opportunity:test:early-low", 1, 5f);
        LethalOpportunity earlyHigh = opportunity("opportunity:test:early-high", 1, 20f);

        LethalOpportunityRegistry registry = new LethalOpportunityRegistry(List.of(
            ignored -> List.of(late, earlyLow, earlyHigh)
        ));

        List<LethalOpportunity> result = registry.predictAll(context);

        assertEquals(2, result.size());
        assertEquals("opportunity:test:early-high", result.getFirst().id());
        assertEquals("opportunity:test:early-low", result.get(1).id());
        assertThrows(UnsupportedOperationException.class, () -> result.add(late));
    }

    private static LethalOpportunity opportunity(String id, long impactTick, float damage) {
        ThreatEvent event = new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(impactTick, impactTick),
            new DamageSourceSnapshot(
                DamageRange.exact(damage), Set.of(), false, 1f, false, Optional.empty(), "test:" + id
            ),
            Confidence.BOUNDED,
            Optional.empty(), Optional.empty(), false, false, false, false
        );
        return new LethalOpportunity(
            id, OpportunityFamily.OTHER, event, Confidence.BOUNDED, 1, Map.of("test", "true")
        );
    }

    private static PredictionContext context(EngineLimits limits) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)), limits
        );
    }
}
