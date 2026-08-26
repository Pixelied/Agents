package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.Map;
import java.util.Objects;

public record LethalOpportunity(
    String id,
    OpportunityFamily family,
    ThreatEvent projectedThreat,
    Confidence confidence,
    int actionDepth,
    Map<String, String> evidence
) {
    public LethalOpportunity {
        id = Objects.requireNonNull(id, "id");
        family = Objects.requireNonNull(family, "family");
        projectedThreat = Objects.requireNonNull(projectedThreat, "projectedThreat");
        confidence = Objects.requireNonNull(confidence, "confidence");
        evidence = Map.copyOf(Objects.requireNonNull(evidence, "evidence"));
        if (!id.startsWith("opportunity:")) {
            throw new IllegalArgumentException("opportunity id prefix required");
        }
        if (!projectedThreat.id().equals(id)) {
            throw new IllegalArgumentException("projected threat id must equal opportunity id");
        }
        if (actionDepth < 0) throw new IllegalArgumentException("actionDepth must be non-negative");
    }
}
