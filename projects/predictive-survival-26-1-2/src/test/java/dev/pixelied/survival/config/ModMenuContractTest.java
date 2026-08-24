package dev.pixelied.survival.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMenuContractTest {
    @Test
    void modMenuIsOptionalButExposesConfigEntrypoint() throws Exception {
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        String properties = Files.readString(Path.of("gradle.properties"));
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(metadata.contains("\"modmenu\""));
        assertTrue(metadata.contains("PredictiveSurvivalModMenu"));
        assertTrue(metadata.contains("\"suggests\""));
        String depends = metadata.substring(metadata.indexOf("\"depends\""));
        depends = depends.substring(0, depends.indexOf('}') + 1);
        assertFalse(depends.contains("modmenu"), "Mod Menu must not become a runtime requirement");
        assertTrue(properties.contains("modmenu_version=18.0.0"));
        assertTrue(build.contains("maven.terraformersmc.com"));
        assertTrue(build.contains("com.terraformersmc:modmenu"));
    }

    @Test
    void nativeConfigScreenHasLocalizedLabelsAndDescriptions() throws Exception {
        String lang = Files.readString(Path.of("src/main/resources/assets/predictive_survival/lang/en_us.json"));
        assertTrue(lang.contains("predictive_survival.config.title"));
        assertTrue(lang.contains("predictive_survival.config.safety_mode.description"));
        assertTrue(lang.contains("predictive_survival.config.restore_hand.description"));
        assertFalse(lang.contains("predictive_survival.config.automatic_movement.description"));
        assertFalse(lang.contains("predictive_survival.config.clutches.description"));
        String screen = Files.readString(Path.of("src/client/java/dev/pixelied/survival/config/PredictiveSurvivalConfigScreen.java"));
        assertFalse(screen.contains("predictive_survival.config.automatic_movement"));
        assertFalse(screen.contains("predictive_survival.config.clutches"));
        assertTrue(lang.contains("predictive_survival.config.debug.description"));
        assertTrue(lang.contains("predictive_survival.config.reset_defaults"));
    }
}
