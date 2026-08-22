package dev.adrien.crystaloptimizer.client.config;

import dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics;
import java.util.Locale;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OptimizerDiagnosticsScreen extends Screen {
    private final Screen parent;
    private final boolean readOnly = true;

    public OptimizerDiagnosticsScreen(Screen parent) {
        super(Component.literal("Crystal Optimizer Diagnostics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 150;
        int y = this.height / 2 - 92;
        int width = 300;
        ClientCombatDiagnostics diagnostics = ClientCombatDiagnostics.latest().orElse(null);

        addReadOnly(x, y, width, damageSummary(diagnostics));
        addReadOnly(x, y + 24, width, confidenceSummary(diagnostics));
        addReadOnly(x, y + 48, width, timingSummary(diagnostics));
        addReadOnly(x, y + 72, width, plannerSummary(diagnostics));
        addReadOnly(x, y + 96, width, traceSummary(diagnostics));

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> closeToParent())
            .bounds(x + 50, y + 132, 200, 20).build());
    }

    private void addReadOnly(int x, int y, int width, String text) {
        Button field = Button.builder(Component.literal(text), button -> {})
            .bounds(x, y, width, 20).build();
        field.active = !readOnly;
        addRenderableWidget(field);
    }

    private static String damageSummary(ClientCombatDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Damage: no live combat data";
        }
        var damage = diagnostics.targetDamage();
        return String.format(Locale.ROOT,
            "Damage L/E/Post %.1f / %.1f / %.1f | self %.1f",
            damage.lowerBound(),
            damage.expected(),
            damage.postMitigationExpected(),
            diagnostics.worstSelfDamage()
        );
    }

    private static String confidenceSummary(ClientCombatDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Confidence: no live combat data";
        }
        return String.format(Locale.ROOT,
            "Confidence prediction %.2f | hurt-window %.2f",
            diagnostics.predictionConfidence(),
            diagnostics.hurtWindowConfidence()
        );
    }

    private static String timingSummary(ClientCombatDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Timing: no live combat data";
        }
        return String.format(Locale.ROOT,
            "Timing place-spawn p50/p90 %.1f / %.1f ms | selected p90 %.1f",
            diagnostics.placeSpawnP50Millis(),
            diagnostics.placeSpawnP90Millis(),
            diagnostics.selectedP90Millis()
        );
    }

    private static String plannerSummary(ClientCombatDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Planner: no live combat data";
        }
        return String.format(Locale.ROOT,
            "Planner %.3f ms | stale %d | candidates %s",
            diagnostics.strategicDurationNanos() / 1_000_000.0,
            diagnostics.staleResultCount(),
            diagnostics.candidateCounts()
        );
    }

    private static String traceSummary(ClientCombatDiagnostics diagnostics) {
        if (diagnostics == null) {
            return "Trace: no live combat data";
        }
        return diagnostics.latestTrace()
            .map(trace -> "Trace " + trace.chosenDecisionKey()
                + " | " + trace.decisionClass()
                + " | reject " + diagnostics.lastRejection().map(Enum::name).orElse("none"))
            .orElse("Trace: no decisions recorded");
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void closeToParent() {
        this.minecraft.setScreen(parent);
    }
}
