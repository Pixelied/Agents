package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsAreSafeAndDebugOff() {
        SurvivalConfig config = SurvivalConfig.defaults();

        assertEquals(SafetyMode.SAFE, config.safetyMode());
        assertTrue(config.restoreHandState());
        assertFalse(config.automaticMovement());
        assertFalse(config.blockPlacementAndClutches());
        assertFalse(config.debugEnabled());
    }

    @Test
    void storeRoundTripsOnlyTheFivePersistedSettings() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        SurvivalConfigStore store = new SurvivalConfigStore(path);
        SurvivalConfig expected = new SurvivalConfig(
            SafetyMode.BALANCED,
            false,
            true,
            false,
            true
        );

        store.save(expected);
        SurvivalConfig loaded = store.load();

        assertEquals(expected, loaded);
    }

    @Test
    void missingConfigLoadsDefaults() throws Exception {
        SurvivalConfigStore store = new SurvivalConfigStore(tempDir.resolve("missing.json"));

        assertEquals(SurvivalConfig.defaults(), store.load());
    }
}
