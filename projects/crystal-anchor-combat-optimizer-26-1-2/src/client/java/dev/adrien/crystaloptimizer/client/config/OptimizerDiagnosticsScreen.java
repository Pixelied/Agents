package dev.adrien.crystaloptimizer.client.config;

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
        int x = this.width / 2 - 100;
        int y = this.height / 2 - 36;

        Button timing = Button.builder(
            Component.literal("TIME_TO_DAMAGE: read only"), button -> {}
        ).bounds(x, y, 200, 20).build();
        timing.active = !readOnly;
        addRenderableWidget(timing);

        Button damage = Button.builder(
            Component.literal("Damage interval + mismatch: HUD"), button -> {}
        ).bounds(x, y + 26, 200, 20).build();
        damage.active = !readOnly;
        addRenderableWidget(damage);

        addRenderableWidget(Button.builder(Component.literal("Done"), button -> closeToParent())
            .bounds(x, y + 60, 200, 20).build());
    }

    @Override
    public void onClose() {
        closeToParent();
    }

    private void closeToParent() {
        this.minecraft.setScreen(parent);
    }
}
