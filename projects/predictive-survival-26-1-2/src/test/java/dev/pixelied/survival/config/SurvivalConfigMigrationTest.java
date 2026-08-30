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
        assertEquals(TotemHandPriority.SMART, loaded.totemHandPriority());
    }

    @Test
    void schemaV2ConfigMigratesToSmartHandPriorityWithoutResettingPolicy() throws Exception {
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
        assertEquals(TotemHandPriority.SMART, loaded.totemHandPriority());
        assertFalse(loaded.restoreHandState());
        assertTrue(loaded.debugEnabled());
        assertFalse(loaded.automaticMovement(), "missing legacy field should use the safe default rather than reset the config");
        assertFalse(loaded.blockPlacementAndClutches(), "missing legacy field should use the safe default rather than reset the config");
    }

    @Test
    void schemaV3RoundTripsExplicitPriorityAndWritesCurrentSchema() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        SurvivalConfigStore store = new SurvivalConfigStore(path);
        SurvivalConfig source = new SurvivalConfig(
            SafetyMode.BALANCED,
            RescueProfile.TOTEM_ONLY,
            RescuePolicy.smartDefaults(),
            TotemHandPriority.MAIN_HAND,
            false,
            false,
            false,
            true
        );

        store.save(source);
        String json = Files.readString(path);
        SurvivalConfig loaded = store.load();

        assertTrue(json.contains("\"schemaVersion\": 3"));
        assertTrue(json.contains("\"totemHandPriority\": \"MAIN_HAND\""));
        assertEquals(source, loaded);
    }

    @Test
    void invalidPriorityFallsBackToSmartOnly() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        Files.writeString(path, """
            {
              "schemaVersion": 3,
              "safetyMode": "BALANCED",
              "totemHandPriority": "BROKEN",
              "restoreHandState": false,
              "debugEnabled": true
            }
            """);

        SurvivalConfig loaded = new SurvivalConfigStore(path).load();

        assertEquals(SafetyMode.BALANCED, loaded.safetyMode());
        assertEquals(TotemHandPriority.SMART, loaded.totemHandPriority());
        assertFalse(loaded.restoreHandState());
        assertTrue(loaded.debugEnabled());
    }
}
