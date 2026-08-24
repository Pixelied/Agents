package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModMenuIntegrationArchitectureTest {
    @Test
    void modMenuIsOptionalAndExposesOnlyNormalSettingsOnMainScreen() throws Exception {
        String metadata = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        String integration = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/integration/CrystalOptimizerModMenu.java"
        ));
        String screen = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerConfigScreen.java"
        ));
        String diagnostics = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/config/OptimizerDiagnosticsScreen.java"
        ));

        assertTrue(metadata.contains("\"modmenu\""));
        assertTrue(metadata.contains("\"suggests\""));
        int depends = metadata.indexOf("\"depends\"");
        int suggests = metadata.indexOf("\"suggests\"");
        assertTrue(suggests > depends);
        assertFalse(metadata.substring(depends, suggests).contains("\"modmenu\""),
            "Mod Menu must remain optional, never a hard dependency");
        assertTrue(integration.contains("implements ModMenuApi"));
        assertTrue(integration.contains("OptimizerConfigScreen"));

        for (String normalSetting : new String[] {
            "Enabled", "Strategy", "Target Range", "Min Damage", "Max Self Damage",
            "Face Place HP", "Crystals", "Anchors", "Auto Restock", "Rotation", "HUD"
        }) {
            assertTrue(screen.contains(normalSetting), "missing normal setting: " + normalSetting);
        }
        assertTrue(screen.contains("Save"));
        assertTrue(screen.contains("Cancel"));
        assertTrue(screen.contains("Advanced Diagnostics"));
        assertTrue(diagnostics.contains("readOnly"));
    }
}
