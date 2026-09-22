package dev.adrien.naturalaim.config;

import dev.adrien.naturalaim.AimMath;
import dev.adrien.naturalaim.NaturalAimClient;
import dev.adrien.naturalaim.NaturalAimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;

public final class NaturalAimConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int ROW_GAP = 3;
    private static final int FIELD_WIDTH = 62;

    private final Screen parent;
    private final NaturalAimConfig config;
    private boolean syncingNumeric;

    public NaturalAimConfigScreen(Screen parent) {
        super(Component.literal("Natural Aim Assist"));
        this.parent = parent;
        this.config = NaturalAimClient.config();
    }

    @Override
    protected void init() {
        int fullWidth = Math.min(390, Math.max(260, this.width - 36));
        int fullLeft = this.width / 2 - fullWidth / 2;
        int sliderWidth = fullWidth - FIELD_WIDTH - GAP;
        int y = 50;
        int row = BUTTON_HEIGHT + ROW_GAP;

        addNumericRow(
                fullLeft, y, sliderWidth, FIELD_WIDTH,
                "Strength",
                () -> config.strength() * 100.0,
                value -> config.setStrength(value / 100.0),
                0.0, 100.0, 1.0,
                value -> String.format(Locale.ROOT, "%.0f%%", value),
                value -> String.format(Locale.ROOT, "%.0f", value)
        );

        y += row;
        addNumericRow(
                fullLeft, y, sliderWidth, FIELD_WIDTH,
                "Assist FOV",
                config::assistFov,
                config::setAssistFov,
                NaturalAimConfig.MIN_FOV, NaturalAimConfig.MAX_FOV, 1.0,
                value -> String.format(Locale.ROOT, "%.0f deg", value),
                value -> String.format(Locale.ROOT, "%.0f", value)
        );

        y += row;
        addNumericRow(
                fullLeft, y, sliderWidth, FIELD_WIDTH,
                "Range",
                config::range,
                config::setRange,
                NaturalAimConfig.MIN_RANGE, NaturalAimConfig.MAX_RANGE, 0.1,
                value -> String.format(Locale.ROOT, "%.1f blocks", value),
                value -> String.format(Locale.ROOT, "%.1f", value)
        );

        int buttonWidth = (fullWidth - GAP) / 2;
        int left = fullLeft;
        int right = fullLeft + buttonWidth + GAP;

        y += row;
        addToggle(left, y, buttonWidth, "Enabled", config::enabled, config::toggleEnabled);
        addToggle(right, y, buttonWidth, "Vertical Assist", config::verticalAssist, config::toggleVerticalAssist);

        y += row;
        addToggle(left, y, buttonWidth, "Require Attack", config::requireAttack, config::toggleRequireAttack);
        addToggle(right, y, buttonWidth, "Weapons Only", config::weaponsOnly, config::toggleWeaponsOnly);

        y += row;
        addToggle(left, y, buttonWidth, "Pause Mining / Use", config::pauseActions, config::togglePauseActions);
        addToggle(right, y, buttonWidth, "Ignore Invisible", config::ignoreInvisible, config::toggleIgnoreInvisible);

        y += row;
        addToggle(left, y, buttonWidth, "Target Players", config::targetPlayers, config::toggleTargetPlayers);
        addToggle(right, y, buttonWidth, "Hostile Mobs", config::targetHostileMobs, config::toggleTargetHostileMobs);

        y += row;
        addToggle(left, y, buttonWidth, "Passive Mobs", config::targetPassiveMobs, config::toggleTargetPassiveMobs);
        addToggle(right, y, buttonWidth, "Visible Only", config::visibleOnly, config::toggleVisibleOnly);

        y += row;
        addToggle(left, y, buttonWidth, "Ignore Invisible", config::ignoreInvisible, config::toggleIgnoreInvisible);
        addCycle(right, y, buttonWidth, () -> "Preset: " + config.preset().displayName(), config::cyclePreset);

        y += row;
        this.addRenderableWidget(Button.builder(Component.literal("Reset Defaults"), button -> {
            config.resetDefaults();
            NaturalAimClient.engine().reset();
            this.rebuildWidgets();
        }).bounds(left, y, fullWidth, BUTTON_HEIGHT).build());

        int doneY = Math.min(this.height - 28, y + row + 7);
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 80, doneY, 160, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int titleX = (this.width - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, titleX, 14, 0xFFFFFFFF, true);

        String subtitle = "Drag a slider or type an exact value";
        int subtitleX = (this.width - this.font.width(subtitle)) / 2;
        graphics.text(this.font, subtitle, subtitleX, 30, 0xFFAAAAAA, false);
    }

    @Override
    public void onClose() {
        config.save();
        NaturalAimClient.engine().reset();
        this.minecraft.gui.setScreen(parent);
    }

    private void addNumericRow(
            int x,
            int y,
            int sliderWidth,
            int fieldWidth,
            String label,
            DoubleSupplier getter,
            DoubleConsumer setter,
            double min,
            double max,
            double step,
            DoubleFunction<String> sliderFormatter,
            DoubleFunction<String> fieldFormatter
    ) {
        EditBox field = new EditBox(
                this.font,
                x + sliderWidth + GAP,
                y,
                fieldWidth,
                BUTTON_HEIGHT,
                Component.literal(label + " value")
        );
        field.setMaxLength(8);
        field.setFilter(NaturalAimConfigScreen::isPotentialNumber);
        field.setValue(fieldFormatter.apply(getter.getAsDouble()));

        NumericSlider slider = new NumericSlider(
                x,
                y,
                sliderWidth,
                BUTTON_HEIGHT,
                label,
                getter.getAsDouble(),
                min,
                max,
                step,
                sliderFormatter,
                value -> {
                    setter.accept(value);
                    if (!syncingNumeric) {
                        syncingNumeric = true;
                        field.setTextColor(0xFFE0E0E0);
                        field.setValue(fieldFormatter.apply(value));
                        syncingNumeric = false;
                    }
                }
        );

        field.setResponder(text -> {
            if (syncingNumeric || text.isBlank() || text.equals(".")) {
                return;
            }

            try {
                double parsed = Double.parseDouble(text);
                if (parsed < min || parsed > max) {
                    field.setTextColor(0xFFFF7777);
                    return;
                }

                double snapped = snap(parsed, min, max, step);
                field.setTextColor(0xFFE0E0E0);
                setter.accept(snapped);
                slider.setActualValue(snapped);
            } catch (NumberFormatException ignored) {
                field.setTextColor(0xFFFF7777);
            }
        });

        this.addRenderableWidget(slider);
        this.addRenderableWidget(field);
    }

    private void addToggle(int x, int y, int width, String name, BooleanSupplier getter, Runnable toggle) {
        Button button = Button.builder(toggleLabel(name, getter.getAsBoolean()), clicked -> {
            toggle.run();
            clicked.setMessage(toggleLabel(name, getter.getAsBoolean()));
        }).bounds(x, y, width, BUTTON_HEIGHT).build();
        this.addRenderableWidget(button);
    }

    private void addCycle(int x, int y, int width, LabelSupplier label, Runnable action) {
        Button button = Button.builder(Component.literal(label.get()), clicked -> {
            action.run();
            clicked.setMessage(Component.literal(label.get()));
        }).bounds(x, y, width, BUTTON_HEIGHT).build();
        this.addRenderableWidget(button);
    }

    private static Component toggleLabel(String name, boolean enabled) {
        return Component.literal(name + ": " + (enabled ? "ON" : "OFF"));
    }

    private static boolean isPotentialNumber(String value) {
        return value.matches("\\d{0,4}(?:\\.\\d{0,2})?");
    }

    private static double snap(double value, double min, double max, double step) {
        double clamped = AimMath.clamp(value, min, max);
        if (step <= 0.0) return clamped;
        double snapped = min + Math.round((clamped - min) / step) * step;
        return AimMath.clamp(snapped, min, max);
    }

    @FunctionalInterface
    private interface LabelSupplier {
        String get();
    }
}
