package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;

import java.util.Objects;

/** Mutable UI-only draft. The live engine only ever receives immutable SurvivalConfig snapshots. */
public final class SurvivalConfigDraft {
    private SafetyMode safetyMode;
    private RescueProfile rescueProfile;
    private RescuePolicy customPolicy;
    private boolean restoreHandState;
    private boolean automaticMovement;
    private boolean blockPlacementAndClutches;
    private boolean debugEnabled;

    public SurvivalConfigDraft(SurvivalConfig initial) {
        load(Objects.requireNonNull(initial, "initial"));
    }

    public SurvivalConfig snapshot() {
        return new SurvivalConfig(
            safetyMode,
            rescueProfile,
            customPolicy,
            restoreHandState,
            automaticMovement,
            blockPlacementAndClutches,
            debugEnabled
        );
    }

    public void resetDefaults() {
        load(SurvivalConfig.defaults());
    }

    public SafetyMode safetyMode() { return safetyMode; }
    public RescueProfile rescueProfile() { return rescueProfile; }
    public RescuePolicy customPolicy() { return customPolicy; }
    public RescuePolicy rescuePolicy() { return rescueProfile.resolve(customPolicy); }
    public boolean restoreHandState() { return restoreHandState; }
    public boolean automaticMovement() { return automaticMovement; }
    public boolean blockPlacementAndClutches() { return blockPlacementAndClutches; }
    public boolean debugEnabled() { return debugEnabled; }

    public void setSafetyMode(SafetyMode value) { safetyMode = Objects.requireNonNull(value, "value"); }
    public void setRescueProfile(RescueProfile value) { rescueProfile = Objects.requireNonNull(value, "value"); }
    public void setCustomPolicy(RescuePolicy value) { customPolicy = Objects.requireNonNull(value, "value"); }
    public void setRestoreHandState(boolean value) { restoreHandState = value; }
    public void setAutomaticMovement(boolean value) { automaticMovement = value; }
    public void setBlockPlacementAndClutches(boolean value) { blockPlacementAndClutches = value; }
    public void setDebugEnabled(boolean value) { debugEnabled = value; }

    private void load(SurvivalConfig config) {
        safetyMode = config.safetyMode();
        rescueProfile = config.rescueProfile();
        customPolicy = config.customPolicy();
        restoreHandState = config.restoreHandState();
        automaticMovement = config.automaticMovement();
        blockPlacementAndClutches = config.blockPlacementAndClutches();
        debugEnabled = config.debugEnabled();
    }
}
