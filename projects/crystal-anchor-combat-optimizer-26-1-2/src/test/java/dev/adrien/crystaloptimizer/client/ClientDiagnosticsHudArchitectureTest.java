package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientDiagnosticsHudArchitectureTest {
    @Test
    void modernReadOnlyHudShowsCachedCombatRuntimeDiagnostics() throws IOException {
        Path initializerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/CrystalOptimizerClient.java"
        );
        Path runtimePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java"
        );
        Path diagnosticsPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatDiagnostics.java"
        );
        Path hudPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java"
        );

        assertTrue(Files.exists(diagnosticsPath), "immutable client diagnostics snapshot must exist");
        assertTrue(Files.exists(hudPath), "optimizer HUD renderer must exist");

        String initializer = Files.readString(initializerPath);
        String runtime = Files.readString(runtimePath);
        String diagnostics = Files.readString(diagnosticsPath);
        String hud = Files.readString(hudPath);

        assertTrue(initializer.contains("OptimizerHud.register(runtime)"));
        assertTrue(hud.contains("HudElementRegistry.attachElementBefore"));
        assertTrue(hud.contains("VanillaHudElements.CHAT"));
        assertTrue(hud.contains("GuiGraphicsExtractor"));
        assertTrue(hud.contains("graphics.fill("));
        assertTrue(hud.contains("graphics.text("));
        assertTrue(hud.contains("if (!diagnostics.enabled())"),
            "HUD must disappear when the combat optimizer is disabled");

        for (String required : List.of(
            "engine.phase()",
            "engine.lastPlan()",
            "engine.lastReconciliationStatus()",
            "engine.lastAbortReason()",
            "lastTargetName",
            "lastTiming"
        )) {
            assertTrue(runtime.contains(required), "runtime diagnostics are missing: " + required);
        }
        assertTrue(runtime.contains("ClientCombatDiagnostics diagnostics()"));

        for (String required : List.of(
            "boolean enabled",
            "String targetName",
            "CommitPhase phase",
            "int actionCount",
            "boolean lethal",
            "double robustness",
            "String reconciliation",
            "String abortReason",
            "double roundTripMillis",
            "double jitterMillis"
        )) {
            assertTrue(diagnostics.contains(required), "diagnostics snapshot is missing: " + required);
        }

        assertFalse(hud.contains("HudRenderCallback"),
            "26.1.2 HUD must use HudElementRegistry instead of the deprecated callback");
        assertFalse(hud.contains("ClientLevel"));
        assertFalse(hud.contains("LocalPlayer"));
        assertFalse(hud.contains("BeamPlanner"));
        assertFalse(hud.contains("ClientCombatSnapshotBuilder"),
            "render extraction must not query/rebuild combat state");
    }
}
