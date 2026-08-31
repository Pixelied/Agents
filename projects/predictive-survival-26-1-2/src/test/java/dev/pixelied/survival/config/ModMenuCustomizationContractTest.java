package dev.pixelied.survival.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMenuCustomizationContractTest {
    @Test
    void mainScreenExposesProfilesHandPriorityAndCustomPolicyEditorWithoutUnsupportedActions() throws Exception {
        String screen = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/config/PredictiveSurvivalConfigScreen.java"
        ));
        String lang = Files.readString(Path.of(
            "src/main/resources/assets/predictive_survival/lang/en_us.json"
        ));

        assertTrue(screen.contains("RescueProfile"));
        assertTrue(screen.contains("PredictiveSurvivalPolicyScreen"));
        assertTrue(screen.contains("predictive_survival.config.rescue_profile"));
        assertTrue(screen.contains("predictive_survival.config.customize_policy"));
        assertTrue(screen.contains("TotemHandPriority"));
        assertTrue(screen.contains("predictive_survival.config.totem_hand_priority"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.description"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.totem_only"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.totem_and_shield"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.conservative_smart"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.smart"));
        assertTrue(lang.contains("predictive_survival.config.rescue_profile.custom"));
        assertTrue(lang.contains("predictive_survival.config.customize_policy.description"));
        assertTrue(lang.contains("predictive_survival.config.totem_hand_priority.description"));
        assertTrue(lang.contains("predictive_survival.config.totem_hand_priority.smart"));
        assertTrue(lang.contains("predictive_survival.config.totem_hand_priority.off_hand"));
        assertTrue(lang.contains("predictive_survival.config.totem_hand_priority.main_hand"));

        assertFalse(screen.contains("automatic_movement"));
        assertFalse(screen.contains("clutches"));
        assertFalse(screen.contains("pearl_rescue"));
    }

    @Test
    void customPolicyScreenExposesEveryProductionSafeRescueControl() throws Exception {
        Path path = Path.of(
            "src/client/java/dev/pixelied/survival/config/PredictiveSurvivalPolicyScreen.java"
        );
        assertTrue(Files.exists(path), "Custom rescue policy screen must exist");
        String screen = Files.readString(path);
        String lang = Files.readString(Path.of(
            "src/main/resources/assets/predictive_survival/lang/en_us.json"
        ));

        for (String setting : new String[] {
            "death_protection", "shields", "consumables", "equipment",
            "inventory_routing", "main_hand_takeover", "proactive_dual_protection"
        }) {
            assertTrue(screen.contains("predictive_survival.config.policy." + setting));
            assertTrue(lang.contains("predictive_survival.config.policy." + setting));
            assertTrue(lang.contains("predictive_survival.config.policy." + setting + ".description"));
        }

        assertFalse(screen.contains("automatic_movement"));
        assertFalse(screen.contains("clutches"));
        assertFalse(screen.contains("pearl_rescue"));
    }
}
