package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.threat.ThreatOverflowCondenser;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class OpportunityTimelineAssembler {
    private static final Comparator<ThreatEvent> RISK_ORDER = Comparator
        .comparingLong((ThreatEvent event) -> event.impact().earliest())
        .thenComparing(Comparator.comparingDouble(
            (ThreatEvent event) -> event.damage().rawDamage().max()
        ).reversed())
        .thenComparing(ThreatEvent::id);

    public ThreatTimeline assemble(
        ThreatTimeline actual,
        List<LethalOpportunity> opportunities,
        int maxThreats
    ) {
        Objects.requireNonNull(actual, "actual");
        Objects.requireNonNull(opportunities, "opportunities");
        if (maxThreats <= 0) throw new IllegalArgumentException("maxThreats must be positive");

        Map<String, ThreatEvent> byId = new LinkedHashMap<>();
        for (ThreatEvent event : actual.events()) {
            ThreatEvent value = Objects.requireNonNull(event, "actual threat");
            byId.put(value.id(), value);
        }
        for (LethalOpportunity opportunity : opportunities) {
            LethalOpportunity value = Objects.requireNonNull(opportunity, "opportunity");
            byId.putIfAbsent(value.projectedThreat().id(), value.projectedThreat());
        }

        List<ThreatEvent> ordered = new ArrayList<>(byId.values());
        ordered.sort(RISK_ORDER);
        return new ThreatTimeline(ThreatOverflowCondenser.cap(
            ordered,
            maxThreats,
            "predictive_survival:planning_threat_overflow"
        ));
    }
}
