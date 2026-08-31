package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PredictiveSurvivalConfigScreen extends Screen {
    private static final int ROW_WIDTH = 310;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 24;

    private final Screen parent;
    private final SurvivalConfigDraft draft;
    private final LiveConfigController controller;

    private CycleButton<RescueProfile> rescueProfileButton;
    private CycleButton<SafetyMode> safetyModeButton;
    private Button customizePolicyButton;
    private CycleButton<TotemHandPriority> totemHandPriorityButton;
    private CycleButton<Boolean> restoreHandButton;
    private CycleButton<Boolean> debugButton;
    private StringWidget errorWidget;

    public PredictiveSurvivalConfigScreen(
        Screen parent,
        SurvivalConfig initial,
        LiveConfigController controller
    ) {
        super(Component.translatable("predictive_survival.config.title"));
        this.parent = parent;
        this.draft = new SurvivalConfigDraft(Objects.requireNonNull(initial, "initial"));
        this.controller = Objects.requireNonNull(controller, "controller");
    }

    @Override
    protected void init() {
        int left = (this.width - ROW_WIDTH) / 2;
        int top = Math.max(32, (this.height - 268) / 2);

        StringWidget title = new StringWidget(this.title, this.font);
        title.setX((this.width - this.font.width(this.title)) / 2);
        title.setY(top - 24);
        this.addRenderableWidget(title);

        this.rescueProfileButton = this.addRenderableWidget(
            CycleButton.builder(PredictiveSurvivalConfigScreen::rescueProfileLabel, this.draft.rescueProfile())
                .withValues(List.of(RescueProfile.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable("predictive_survival.config.rescue_profile.description")))
                .create(left, top, ROW_WIDTH, ROW_HEIGHT,
                    Component.translatable("predictive_survival.config.rescue_profile"),
                    (button, value) -> {
                        this.draft.setRescueProfile(value);
                        syncProfileControls();
                    })
        );

        this.safetyModeButton = this.addRenderableWidget(
            CycleButton.builder(PredictiveSurvivalConfigScreen::safetyModeLabel, this.draft.safetyMode())
                .withValues(List.of(SafetyMode.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable("predictive_survival.config.safety_mode.description")))
                .create(left, top + ROW_GAP, ROW_WIDTH, ROW_HEIGHT,
                    Component.translatable("predictive_survival.config.safety_mode"),
                    (button, value) -> this.draft.setSafetyMode(value))
        );

        this.customizePolicyButton = this.addRenderableWidget(
            Button.builder(
                    Component.translatable("predictive_survival.config.customize_policy"),
                    button -> this.minecraft.setScreen(new PredictiveSurvivalPolicyScreen(this, this.draft)))
                .bounds(left, top + ROW_GAP * 2, ROW_WIDTH, ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable("predictive_survival.config.customize_policy.description")))
                .build()
        );

        this.totemHandPriorityButton = this.addRenderableWidget(
            CycleButton.builder(PredictiveSurvivalConfigScreen::totemHandPriorityLabel, this.draft.totemHandPriority())
                .withValues(List.of(TotemHandPriority.values()))
                .withTooltip(value -> Tooltip.create(Component.translatable("predictive_survival.config.totem_hand_priority.description")))
                .create(left, top + ROW_GAP * 3, ROW_WIDTH, ROW_HEIGHT,
                    Component.translatable("predictive_survival.config.totem_hand_priority"),
                    (button, value) -> this.draft.setTotemHandPriority(value))
        );

        this.restoreHandButton = addBooleanRow(
            left, top + ROW_GAP * 4,
            "predictive_survival.config.restore_hand",
            "predictive_survival.config.restore_hand.description",
            this.draft.restoreHandState(),
            this.draft::setRestoreHandState
        );
        this.debugButton = addBooleanRow(
            left, top + ROW_GAP * 5,
            "predictive_survival.config.debug",
            "predictive_survival.config.debug.description",
            this.draft.debugEnabled(),
            this.draft::setDebugEnabled
        );

        int controlsY = top + ROW_GAP * 6 + 8;
        this.addRenderableWidget(Button.builder(
                Component.translatable("predictive_survival.config.reset_defaults"),
                button -> resetDefaults())
            .bounds(left, controlsY, 100, ROW_HEIGHT)
            .tooltip(Tooltip.create(Component.translatable("predictive_survival.config.reset_defaults.description")))
            .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> discardAndClose())
            .bounds(left + 105, controlsY, 100, ROW_HEIGHT)
            .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
            .bounds(left + 210, controlsY, 100, ROW_HEIGHT)
            .build());

        this.errorWidget = new StringWidget(Component.empty(), this.font);
        this.errorWidget.setY(controlsY + 27);
        this.addRenderableWidget(this.errorWidget);
        syncProfileControls();
    }

    @Override
    public void onClose() {
        discardAndClose();
    }

    private CycleButton<Boolean> addBooleanRow(
        int x,
        int y,
        String labelKey,
        String descriptionKey,
        boolean initial,
        java.util.function.Consumer<Boolean> setter
    ) {
        return this.addRenderableWidget(
            CycleButton.onOffBuilder(initial)
                .withTooltip(value -> Tooltip.create(Component.translatable(descriptionKey)))
                .create(x, y, ROW_WIDTH, ROW_HEIGHT, Component.translatable(labelKey),
                    (button, value) -> setter.accept(value))
        );
    }

    private void syncProfileControls() {
        if (this.customizePolicyButton != null) {
            this.customizePolicyButton.active = this.draft.rescueProfile() == RescueProfile.CUSTOM;
        }
    }

    private void resetDefaults() {
        this.draft.resetDefaults();
        this.rescueProfileButton.setValue(this.draft.rescueProfile());
        this.safetyModeButton.setValue(this.draft.safetyMode());
        this.totemHandPriorityButton.setValue(this.draft.totemHandPriority());
        this.restoreHandButton.setValue(this.draft.restoreHandState());
        this.debugButton.setValue(this.draft.debugEnabled());
        syncProfileControls();
        clearError();
    }

    private void saveAndClose() {
        try {
            this.controller.apply(this.draft.snapshot());
            this.minecraft.setScreen(this.parent);
        } catch (IOException ignored) {
            Component message = Component.translatable("predictive_survival.config.save_failed");
            this.errorWidget.setMessage(message);
            this.errorWidget.setX((this.width - this.font.width(message)) / 2);
        }
    }

    private void discardAndClose() {
        this.minecraft.setScreen(this.parent);
    }

    private void clearError() {
        this.errorWidget.setMessage(Component.empty());
        this.errorWidget.setX(this.width / 2);
    }

    private static Component rescueProfileLabel(RescueProfile profile) {
        return Component.translatable("predictive_survival.config.rescue_profile." + profile.name().toLowerCase(Locale.ROOT));
    }

    private static Component safetyModeLabel(SafetyMode mode) {
        return Component.translatable("predictive_survival.config.safety_mode." + mode.name().toLowerCase(Locale.ROOT));
    }

    private static Component totemHandPriorityLabel(TotemHandPriority priority) {
        return Component.translatable(
            "predictive_survival.config.totem_hand_priority." + priority.name().toLowerCase(Locale.ROOT)
        );
    }
}
