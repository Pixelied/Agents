package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalConfigMigrationTest {
    @TempDir
    Path tempDir;

    @Test
    void legacyFiveFieldConfigMigratesWithoutResettingKnownValues() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        Files.writeString(path, """
            {
              "safetyMode": "BALANCED",
              "restoreHandState": false,
              "automaticMovement": false,
              "blockPlacementAndClutches": false,
              "debugEnabled": true
            }
            """);

        SurvivalConfig loaded = new SurvivalConfigStore(path).load();

        assertEquals(SafetyMode.BALANCED, loaded.safetyMode());
        assertFalse(loaded.restoreHandState());
        assertTrue(loaded.debugEnabled());
        assertEquals(RescueProfile.CONSERVATIVE_SMART, loaded.rescueProfile());
        assertEquals(RescuePolicy.smartDefaults(), loaded.rescuePolicy());
    }

    @Test
    void missingOrInvalidNewFieldsFallBackIndividually() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        Files.writeString(path, """
            {
              "schemaVersion": 2,
              "safetyMode": "SAFE",
              "rescueProfile": "CUSTOM",
              "deathProtection": true,
              "shields": false,
              "consumables": true,
              "equipment": false,
              "inventoryRouting": false,
              "mainHandTakeover": false,
              "proactiveDualProtection": false,
              "restoreHandState": false,
              "debugEnabled": true
            }
            """);

        SurvivalConfig loaded = new SurvivalConfigStore(path).load();

        assertEquals(RescueProfile.CUSTOM, loaded.rescueProfile());
        assertEquals(new RescuePolicy(true, false, true, false, false, false, false), loaded.rescuePolicy());
        assertFalse(loaded.restoreHandState());
        assertTrue(loaded.debugEnabled());
        assertFalse(loaded.automaticMovement(), "missing legacy field should use the safe default rather than reset the config");
        assertFalse(loaded.blockPlacementAndClutches(), "missing legacy field should use the safe default rather than reset the config");
    }
}
