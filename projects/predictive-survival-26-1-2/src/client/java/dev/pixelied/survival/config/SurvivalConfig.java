package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;

import java.util.Objects;

public record SurvivalConfig(
    SafetyMode safetyMode,
    RescueProfile rescueProfile,
    RescuePolicy customPolicy,
    boolean restoreHandState,
    boolean automaticMovement,
    boolean blockPlacementAndClutches,
    boolean debugEnabled
) {
    public SurvivalConfig {
        safetyMode = Objects.requireNonNull(safetyMode, "safetyMode");
        rescueProfile = Objects.requireNonNull(rescueProfile, "rescueProfile");
        customPolicy = Objects.requireNonNull(customPolicy, "customPolicy");
    }

    /** Compatibility constructor for the original five-setting configuration. */
    public SurvivalConfig(
        SafetyMode safetyMode,
        boolean restoreHandState,
        boolean automaticMovement,
        boolean blockPlacementAndClutches,
        boolean debugEnabled
    ) {
        this(
            safetyMode,
            RescueProfile.CONSERVATIVE_SMART,
            RescuePolicy.smartDefaults(),
            restoreHandState,
            automaticMovement,
            blockPlacementAndClutches,
            debugEnabled
        );
    }

    /** Effective action policy after resolving the selected profile. */
    public RescuePolicy rescuePolicy() {
        return rescueProfile.resolve(customPolicy);
    }

    public static SurvivalConfig defaults() {
        return new SurvivalConfig(
            SafetyMode.SAFE,
            RescueProfile.CONSERVATIVE_SMART,
            RescuePolicy.smartDefaults(),
            true,
            false,
            false,
            false
        );
    }
}
