package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;

import java.util.Objects;

public record SurvivalConfig(
    SafetyMode safetyMode,
    boolean restoreHandState,
    boolean automaticMovement,
    boolean blockPlacementAndClutches,
    boolean debugEnabled
) {
    public SurvivalConfig {
        safetyMode = Objects.requireNonNull(safetyMode, "safetyMode");
    }

    public static SurvivalConfig defaults() {
        return new SurvivalConfig(SafetyMode.SAFE, true, false, true, false);
    }
}
