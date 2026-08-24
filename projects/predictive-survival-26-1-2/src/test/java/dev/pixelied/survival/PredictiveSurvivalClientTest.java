package dev.pixelied.survival;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PredictiveSurvivalClientTest {
    @Test
    void automationDoesNotStartInsideClientGametestHarness() {
        assertFalse(PredictiveSurvivalClient.shouldStartAutomation(
            Set.of("predictive_survival_gametest")::contains
        ));
    }

    @Test
    void automationStartsInNormalClient() {
        assertTrue(PredictiveSurvivalClient.shouldStartAutomation(Set.<String>of()::contains));
    }
}
