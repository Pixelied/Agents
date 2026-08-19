package dev.adrien.crystaloptimizer.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

public final class OptimizerHud {
    private static final int PANEL_COLOR = 0xB20A1324;
    private static final int TITLE_COLOR = 0xFFDDE8FF;
    private static final int TEXT_COLOR = 0xFFF4F7FB;
    private static final int MUTED_COLOR = 0xFF9EACC1;
    private static final int ALERT_COLOR = 0xFFFFC857;
    private static final int X = 8;
    private static final int Y = 8;
    private static final int LINE_HEIGHT = 10;

    private final Minecraft minecraft;
    private final ClientCombatRuntime runtime;
    private final Supplier<dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics> diagnosticsSupplier;

    private OptimizerHud(Minecraft minecraft, ClientCombatRuntime runtime) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.diagnosticsSupplier = null;
    }

    private OptimizerHud(
        Minecraft minecraft,
        Supplier<dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics> diagnosticsSupplier
    ) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.runtime = null;
        this.diagnosticsSupplier = Objects.requireNonNull(diagnosticsSupplier, "diagnosticsSupplier");
    }

    public static void register(ClientCombatRuntime runtime) {
        OptimizerHud hud = new OptimizerHud(Minecraft.getInstance(), runtime);
        attach(hud);
    }

    public static void register(
        Supplier<dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics> diagnosticsSupplier
    ) {
        OptimizerHud hud = new OptimizerHud(Minecraft.getInstance(), diagnosticsSupplier);
        attach(hud);
    }

    private static void attach(OptimizerHud hud) {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath("crystaloptimizer", "diagnostics"),
            hud::extract
        );
    }

    private void extract(GuiGraphicsExtractor graphics, DeltaTracker ignored) {
        if (diagnosticsSupplier != null) {
            dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics diagnostics = diagnosticsSupplier.get();
            if (!diagnostics.enabled()) {
                return;
            }
            if (!diagnostics.hudEnabled()) {
                return;
            }
            List<Line> lines = new ArrayList<>();
            lines.add(new Line("CRYSTAL OPTIMIZER V2  " + diagnostics.strategy().name(), TITLE_COLOR));
            lines.add(new Line(
                diagnostics.targetName().isBlank() ? "Target  -" : "Target  " + diagnostics.targetName(),
                TEXT_COLOR
            ));
            String approval = diagnostics.selectedApproval().map(Enum::name).orElse("IDLE");
            lines.add(new Line("Reactive  " + approval, TEXT_COLOR));
            var damage = diagnostics.targetDamage();
            lines.add(new Line(String.format(
                Locale.ROOT,
                "Damage  %.1f / %.1f / %.1f   self<=%.1f",
                damage.lowerBound(), damage.expected(), damage.upperBound(), diagnostics.worstSelfDamage()
            ), TEXT_COLOR));
            if (diagnostics.placeSpawnP90Millis() > 0.0) {
                lines.add(new Line(String.format(
                    Locale.ROOT,
                    "Place->spawn  %.0f / %.0f ms",
                    diagnostics.placeSpawnP50Millis(), diagnostics.placeSpawnP90Millis()
                ), MUTED_COLOR));
            }
            lines.add(new Line(String.format(
                Locale.ROOT,
                "CPU  %.3f + %.3f ms",
                diagnostics.lastEventToDecisionNanos() / 1_000_000.0,
                diagnostics.lastDecisionToDispatchNanos() / 1_000_000.0
            ), MUTED_COLOR));
            if (diagnostics.lastMismatch() != dev.adrien.crystaloptimizer.v2.damage.DamageMismatch.Kind.NONE) {
                lines.add(new Line("Mismatch  " + diagnostics.lastMismatch().name(), ALERT_COLOR));
            }
            diagnostics.lastRejection().ifPresent(reason ->
                lines.add(new Line("Rejected  " + reason.name(), MUTED_COLOR))
            );
            draw(graphics, lines);
            return;
        }

        ClientCombatDiagnostics diagnostics = runtime.diagnostics();
        if (!diagnostics.enabled()) {
            return;
        }

        List<Line> lines = new ArrayList<>();
        lines.add(new Line("CRYSTAL OPTIMIZER  " + diagnostics.phase().name(), TITLE_COLOR));
        lines.add(new Line(
            diagnostics.targetName().isBlank() ? "Target  -" : "Target  " + diagnostics.targetName(),
            TEXT_COLOR
        ));

        String plan = diagnostics.actionCount() == 0
            ? "Plan  idle"
            : "Plan  " + diagnostics.actionCount()
                + (diagnostics.lethal() ? "  LETHAL" : "")
                + "  R " + Math.round(diagnostics.robustness() * 100.0) + "%";
        lines.add(new Line(plan, diagnostics.lethal() ? ALERT_COLOR : TEXT_COLOR));

        if (diagnostics.roundTripMillis() > 0.0 || diagnostics.jitterMillis() > 0.0) {
            lines.add(new Line(
                String.format(
                    Locale.ROOT,
                    "Timing  %.0f ms  +/-%.0f",
                    diagnostics.roundTripMillis(),
                    diagnostics.jitterMillis()
                ),
                MUTED_COLOR
            ));
        }
        if (!diagnostics.reconciliation().isBlank()) {
            lines.add(new Line("Recon  " + diagnostics.reconciliation(), MUTED_COLOR));
        } else if (!diagnostics.abortReason().isBlank()) {
            lines.add(new Line("Last abort  " + diagnostics.abortReason(), MUTED_COLOR));
        }
        draw(graphics, lines);
    }

    private void draw(GuiGraphicsExtractor graphics, List<Line> lines) {
        int width = 0;
        for (Line line : lines) {
            width = Math.max(width, minecraft.font.width(line.text()));
        }
        int height = lines.size() * LINE_HEIGHT;
        graphics.fill(X - 4, Y - 4, X + width + 4, Y + height + 3, PANEL_COLOR);
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            graphics.text(
                minecraft.font,
                line.text(),
                X,
                Y + index * LINE_HEIGHT,
                line.color(),
                true
            );
        }
    }

    private record Line(String text, int color) {
    }
}
