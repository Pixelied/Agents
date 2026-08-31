package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalConfigDraftTest {
    @Test
    void editsAreStagedUntilSnapshotIsApplied() {
        SurvivalConfig original = SurvivalConfig.defaults();
        SurvivalConfigDraft draft = new SurvivalConfigDraft(original);

        draft.setSafetyMode(SafetyMode.EXPERIMENTAL);
        draft.setTotemHandPriority(TotemHandPriority.OFF_HAND);
        draft.setRestoreHandState(false);
        draft.setAutomaticMovement(true);
        draft.setBlockPlacementAndClutches(false);
        draft.setDebugEnabled(true);

        assertEquals(SurvivalConfig.defaults(), original);
        SurvivalConfig edited = draft.snapshot();
        assertEquals(SafetyMode.EXPERIMENTAL, edited.safetyMode());
        assertEquals(TotemHandPriority.OFF_HAND, edited.totemHandPriority());
        assertFalse(edited.restoreHandState());
        assertTrue(edited.automaticMovement());
        assertFalse(edited.blockPlacementAndClutches());
        assertTrue(edited.debugEnabled());
    }

    @Test
    void resetDefaultsOnlyChangesDraft() {
        SurvivalConfig original = new SurvivalConfig(
            SafetyMode.BALANCED,
            RescueProfile.CONSERVATIVE_SMART,
            RescuePolicy.smartDefaults(),
            TotemHandPriority.MAIN_HAND,
            false,
            true,
            false,
            true
        );
        SurvivalConfigDraft draft = new SurvivalConfigDraft(original);

        draft.resetDefaults();

        assertEquals(SurvivalConfig.defaults(), draft.snapshot());
        assertEquals(SafetyMode.BALANCED, original.safetyMode());
        assertEquals(TotemHandPriority.MAIN_HAND, original.totemHandPriority());
    }
}
