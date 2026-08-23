package dev.pixelied.survival.planner;

import java.util.Objects;

public record PlannedStep(SurvivalAction action, long activationTick) {
    public PlannedStep {
        action = Objects.requireNonNull(action, "action");
        if (activationTick < 0L) throw new IllegalArgumentException("activationTick must be non-negative");
    }
}
