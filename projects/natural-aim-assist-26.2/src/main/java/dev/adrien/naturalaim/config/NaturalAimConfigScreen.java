package dev.adrien.naturalaim.config;

import dev.adrien.naturalaim.NaturalAimClient;
import dev.adrien.naturalaim.NaturalAimConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;
import java.util.function.BooleanSupplier;

public final class NaturalAimConfigScreen extends Screen {
    private static final int BUTTON_HEIGHT = 20;
    private static final int GAP = 6;
    private static final int ROW_GAP = 4;

    private final Screen parent;
    private final NaturalAimConfig config;

    public NaturalAimConfigScreen(Screen parent) {
        super(Component.literal("Natural Aim Assist"));
        this.parent = parent;
        this.config = NaturalAimClient.config();
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(190, Math.max(130, (this.width - 3 * GAP) / 2));
        int left = this.width / 2 - buttonWidth - GAP / 2;
        int right = this.width / 2 + GAP / 2;
        int y = 54;
        int row = BUTTON_HEIGHT + ROW_GAP;

        addToggle(left, y, buttonWidth, "Enabled", config::enabled, config::toggleEnabled);
        addCycle(right, y, buttonWidth, () -> "Preset: " + config.preset().displayName(), config::cyclePreset);

        y += row;
        addCycle(left, y, buttonWidth, () -> "Strength: " + Math.round(config.strength() * 100.0) + "%", config::cycleStrength);
        addCycle(right, y, buttonWidth, () -> "Assist FOV: " + compact(config.assistFov()) + " deg", config::cycleAssistFov);

        y += row;
        addCycle(left, y, buttonWidth, () -> "Range: " + compact(config.range()) + " blocks", config::cycleRange);
        addToggle(right, y, buttonWidth, "Vertical Assist", config::verticalAssist, config::toggleVerticalAssist);

        y += row;
        addToggle(left, y, buttonWidth, "Require Attack", config::requireAttack, config::toggleRequireAttack);
        addToggle(right, y, buttonWidth, "Weapons Only", config::weaponsOnly, config::toggleWeaponsOnly);

        y += row;
        addToggle(left, y, buttonWidth, "Target Players", config::targetPlayers, config::toggleTargetPlayers);
        addToggle(right, y, buttonWidth, "Target Mobs", config::targetMobs, config::toggleTargetMobs);

        y += row;
        addToggle(left, y, buttonWidth, "Visible Only", config::visibleOnly, config::toggleVisibleOnly);
        addToggle(right, y, buttonWidth, "Ignore Invisible", config::ignoreInvisible, config::toggleIgnoreInvisible);

        y += row;
        addToggle(left, y, buttonWidth, "Pause Mining / Use", config::pauseActions, config::togglePauseActions);
        addCycle(right, y, buttonWidth, () -> "Reset Defaults", () -> {
            config.resetDefaults();
            this.rebuildWidgets();
        });

        int doneY = Math.min(this.height - 28, y + row + 8);
        this.addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
                .bounds(this.width / 2 - 80, doneY, 160, BUTTON_HEIGHT)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int titleX = (this.width - this.font.width(this.title)) / 2;
        graphics.text(this.font, this.title, titleX, 18, 0xFFFFFFFF, true);

        String subtitle = "Player-led correction - no target lock or random jitter";
        int subtitleX = (this.width - this.font.width(subtitle)) / 2;
        graphics.text(this.font, subtitle, subtitleX, 34, 0xFFAAAAAA, false);
    }

    @Override
    public void onClose() {
        config.save();
        NaturalAimClient.engine().reset();
        this.minecraft.gui.setScreen(parent);
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

    private static String compact(double value) {
        if (Math.rint(value) == value) return String.format(Locale.ROOT, "%.0f", value);
        return String.format(Locale.ROOT, "%.1f", value);
    }

    @FunctionalInterface
    private interface LabelSupplier {
        String get();
    }
}
