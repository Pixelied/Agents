package dev.pixelied.survival.planner;

import dev.pixelied.survival.timeline.ThreatEvent;

import java.util.Objects;

public record HurtCooldownCandidate(
    String strategyId,
    ThreatEvent precursor,
    SurvivalAction action,
    boolean runtimeValidated
) {
    public HurtCooldownCandidate {
        strategyId = Objects.requireNonNull(strategyId, "strategyId");
        if (strategyId.isBlank()) throw new IllegalArgumentException("strategyId must not be blank");
        precursor = Objects.requireNonNull(precursor, "precursor");
        action = Objects.requireNonNull(action, "action");
    }
}
