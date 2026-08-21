package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDiagnosticsHudArchitectureTest {
    @Test
    void modernReadOnlyHudShowsCachedV2Diagnostics() throws IOException {
        String initializer = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java"
        ));
        String hud = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java"
        ));

        assertTrue(initializer.contains("OptimizerHud.register(coordinator::diagnostics)"));
        assertTrue(hud.contains("HudElementRegistry.attachElementBefore"));
        assertTrue(hud.contains("VanillaHudElements.CHAT"));
        assertTrue(hud.contains("GuiGraphicsExtractor"));
        assertTrue(hud.contains("graphics.fill("));
        assertTrue(hud.contains("graphics.text("));
        assertTrue(hud.contains("!diagnostics.enabled()"),
            "HUD must disappear when the combat optimizer is disabled");
        assertTrue(hud.contains("!diagnostics.hudEnabled()"),
            "HUD must respect the HUD setting");
        assertFalse(hud.contains("HudRenderCallback"),
            "26.1.2 HUD must use HudElementRegistry instead of the deprecated callback");
    }

    @Test
    void v2HudReadsOnlyCachedDiagnosticsDuringRender() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java"
        ));
        int start = source.indexOf("private void extract");
        int end = source.indexOf("private record Line", start);
        assertTrue(start >= 0 && end > start);
        String render = source.substring(start, end);

        assertFalse(render.contains("Minecraft.level"));
        assertFalse(render.contains("minecraft.level"));
        assertFalse(render.contains("ClientCombatSnapshotBuilder"));
        assertFalse(render.contains("BeamPlanner"));
        assertFalse(render.contains("CandidateGenerator"));
        assertFalse(render.contains("TargetPredictor"));
        assertTrue(source.contains("diagnosticsSupplier.get()"),
            "V2 HUD must render from a cached diagnostics supplier");
    }

    @Test
    void v2DiagnosticsCachesLatencyDamageMismatchAndEfficiencyFields() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatDiagnostics.java"
        ));
        assertTrue(source.contains("lastEventToDecisionNanos"));
        assertTrue(source.contains("lastDecisionToDispatchNanos"));
        assertTrue(source.contains("lastMismatch"));
        assertTrue(source.contains("targetDamage"));
        assertTrue(source.contains("worstSelfDamage"));
        assertTrue(source.contains("selectedIntent"));
        assertTrue(source.contains("resourceDemand"));
        assertTrue(source.contains("selectedP90Millis"));
    }
}
