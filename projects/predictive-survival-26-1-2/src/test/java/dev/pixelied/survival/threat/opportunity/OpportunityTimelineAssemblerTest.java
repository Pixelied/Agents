package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
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

class OpportunityTimelineAssemblerTest {
    @Test
    void opportunityProjectionDoesNotReplaceActualThreatWithSameSourceFamily() {
        ThreatTimeline actual = new ThreatTimeline(List.of(threat("explosion:7", 2, 10f)));
        LethalOpportunity opportunity = opportunity("opportunity:crystal:7", 0, 40f);

        ThreatTimeline planning = new OpportunityTimelineAssembler().assemble(actual, List.of(opportunity), 8);

        assertEquals(1, actual.events().size());
        assertEquals(2, planning.events().size());
        assertTrue(planning.events().stream().anyMatch(event -> event.id().equals("explosion:7")));
        assertTrue(planning.events().stream().anyMatch(event -> event.id().equals("opportunity:crystal:7")));
    }

    private static LethalOpportunity opportunity(String id, long tick, float damage) {
        ThreatEvent event = threat(id, tick, damage);
        return new LethalOpportunity(
            id, OpportunityFamily.CRYSTAL, event, Confidence.POTENTIAL, 1, Map.of("source", "test")
        );
    }

    private static ThreatEvent threat(String id, long tick, float damage) {
        return new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            new TickWindow(tick, tick),
            new DamageSourceSnapshot(
                DamageRange.exact(damage), Set.of(), false, 1f, false, Optional.empty(), "minecraft:explosion"
            ),
            Confidence.BOUNDED,
            Optional.empty(), Optional.empty(), false, false, false, false
        );
    }
}
