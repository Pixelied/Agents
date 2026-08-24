package dev.adrien.crystaloptimizer.client.config;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import java.util.Arrays;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OptimizerConfigScreen extends Screen {
    private static final String TARGET_RANGE = "Target Range";
    private static final String MIN_DAMAGE = "Min Damage";
    private static final String MAX_SELF_DAMAGE = "Max Self Damage";
    private static final String FACE_PLACE_HP = "Face Place HP";
    private static final String ADVANCED_DIAGNOSTICS = "Advanced Diagnostics";

    private final Screen parent;
    private final OptimizerConfigService service;
    private OptimizerConfig draft;
    private EditBox targetRange;
    private EditBox minDamage;
    private EditBox maxSelfDamage;
    private EditBox facePlaceHealth;

    public OptimizerConfigScreen(Screen parent, OptimizerConfigService service) {
        super(Component.literal("Crystal Optimizer"));
        this.parent = parent;
        this.service = service;
        this.draft = service.current();
    }

    @Override
    protected void init() {
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        int y = 36;
        int width = 150;

        addRenderableWidget(CycleButton.onOffBuilder(draft.enabled()).create(
            left, y, width, 20, Component.literal("Enabled"),
            (button, value) -> draft = withEnabled(draft, value)
        ));
        addRenderableWidget(CycleButton.builder(
            value -> Component.literal(pretty(value.name())), draft.strategy()
        ).withValues(Arrays.asList(OptimizerStrategy.values())).create(
            right, y, width, 20, Component.literal("Strategy"),
            (button, value) -> draft = withStrategy(draft, value)
        ));

        y += 26;
        targetRange = numberBox(left, y, TARGET_RANGE, draft.targetRange());
        minDamage = numberBox(right, y, MIN_DAMAGE, draft.minDamage());
        y += 26;
        maxSelfDamage = numberBox(left, y, MAX_SELF_DAMAGE, draft.maxSelfDamage());
        facePlaceHealth = numberBox(right, y, FACE_PLACE_HP, draft.facePlaceHealth());

        y += 32;
        addRenderableWidget(CycleButton.onOffBuilder(draft.crystals()).create(
            left, y, width, 20, Component.literal("Crystals"),
            (button, value) -> draft = withCrystals(draft, value)
        ));
        addRenderableWidget(CycleButton.onOffBuilder(draft.anchors()).create(
            right, y, width, 20, Component.literal("Anchors"),
            (button, value) -> draft = withAnchors(draft, value)
        ));
        y += 26;
        addRenderableWidget(CycleButton.onOffBuilder(draft.autoRestock()).create(
            left, y, width, 20, Component.literal("Auto Restock"),
            (button, value) -> draft = withAutoRestock(draft, value)
        ));
        addRenderableWidget(CycleButton.builder(
            value -> Component.literal(pretty(value.name())), draft.rotationMode()
        ).withValues(Arrays.asList(RotationMode.values())).create(
            right, y, width, 20, Component.literal("Rotation"),
            (button, value) -> draft = withRotation(draft, value)
        ));
        y += 26;
        addRenderableWidget(CycleButton.onOffBuilder(draft.hud()).create(
            left, y, width, 20, Component.literal("HUD"),
            (button, value) -> draft = withHud(draft, value)
        ));
        addRenderableWidget(Button.builder(Component.literal(ADVANCED_DIAGNOSTICS), button ->
            this.minecraft.setScreen(new OptimizerDiagnosticsScreen(this))
        ).bounds(right, y, width, 20).build());

        y += 34;
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> closeToParent())
            .bounds(left, y, width, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> saveAndClose())
            .bounds(right, y, width, 20).build());
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private EditBox numberBox(int x, int y, String label, double value) {
        EditBox box = new EditBox(this.font, x, y, 150, 20, Component.literal(label));
        box.setHint(Component.literal(label));
        box.setValue(Double.toString(value));
        return addRenderableWidget(box);
    }

    private void saveAndClose() {
        try {
            OptimizerConfig next = new OptimizerConfig(
                draft.enabled(),
                draft.strategy(),
                Double.parseDouble(targetRange.getValue()),
                Float.parseFloat(minDamage.getValue()),
                Float.parseFloat(maxSelfDamage.getValue()),
                Float.parseFloat(facePlaceHealth.getValue()),
                draft.crystals(),
                draft.anchors(),
                draft.autoRestock(),
                draft.rotationMode(),
                draft.hud()
            ).validated();
            service.apply(next);
            closeToParent();
        } catch (RuntimeException invalid) {
            targetRange.setHint(Component.literal("Invalid numeric settings"));
        }
    }

    private void closeToParent() {
        this.minecraft.setScreen(parent);
    }

    private static OptimizerConfig withEnabled(OptimizerConfig c, boolean v) {
        return new OptimizerConfig(v, c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), c.anchors(), c.autoRestock(), c.rotationMode(), c.hud());
    }

    private static OptimizerConfig withStrategy(OptimizerConfig c, OptimizerStrategy v) {
        return new OptimizerConfig(c.enabled(), v, c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), c.anchors(), c.autoRestock(), c.rotationMode(), c.hud());
    }

    private static OptimizerConfig withCrystals(OptimizerConfig c, boolean v) {
        return new OptimizerConfig(c.enabled(), c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), v, c.anchors(), c.autoRestock(), c.rotationMode(), c.hud());
    }

    private static OptimizerConfig withAnchors(OptimizerConfig c, boolean v) {
        return new OptimizerConfig(c.enabled(), c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), v, c.autoRestock(), c.rotationMode(), c.hud());
    }

    private static OptimizerConfig withAutoRestock(OptimizerConfig c, boolean v) {
        return new OptimizerConfig(c.enabled(), c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), c.anchors(), v, c.rotationMode(), c.hud());
    }

    private static OptimizerConfig withRotation(OptimizerConfig c, RotationMode v) {
        return new OptimizerConfig(c.enabled(), c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), c.anchors(), c.autoRestock(), v, c.hud());
    }

    private static OptimizerConfig withHud(OptimizerConfig c, boolean v) {
        return new OptimizerConfig(c.enabled(), c.strategy(), c.targetRange(), c.minDamage(), c.maxSelfDamage(), c.facePlaceHealth(), c.crystals(), c.anchors(), c.autoRestock(), c.rotationMode(), v);
    }

    private static String pretty(String value) {
        return value.replace('_', ' ');
    }
}
