package dev.adrien.spearclient.ui;

import dev.adrien.spearclient.SpearClient;
import dev.adrien.spearclient.config.ConfigStore;
import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.debug.DebugSnapshot;
import dev.adrien.spearclient.network.ServerStateTracker;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class SpearConfigScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int ROW_HEIGHT = 24;

    private final Screen parent;
    private final ConfigStore store;

    private boolean oneTapEnabled;
    private boolean lungeEnabled;
    private boolean reachEnabled;
    private boolean teamCheck;
    private boolean debugEnabled;
    private boolean closed;

    public SpearConfigScreen(Screen parent, ConfigStore store, SpearConfig initial) {
        super(Component.literal("Spear Client"));
        this.parent = parent;
        this.store = store;
        SpearConfig config = initial == null ? SpearConfig.defaults() : initial.sanitized();
        this.oneTapEnabled = config.oneTap().enabled();
        this.lungeEnabled = config.lungeBoost().enabled();
        this.reachEnabled = config.infiniteReach().enabled();
        this.teamCheck = config.infiniteReach().teamCheck();
        this.debugEnabled = config.debug();
    }

    @Override
    protected void init() {
        int x = this.width / 2 - BUTTON_WIDTH / 2;
        int y = 44;

        this.addRenderableWidget(Button.builder(
            toggleLabel("One-Tap", oneTapEnabled),
            button -> {
                oneTapEnabled = !oneTapEnabled;
                button.setMessage(toggleLabel("One-Tap", oneTapEnabled));
            }
        ).bounds(x, y, BUTTON_WIDTH, 20).build());

        y += ROW_HEIGHT;
        this.addRenderableWidget(Button.builder(
            toggleLabel("Lunge Boost", lungeEnabled),
            button -> {
                lungeEnabled = !lungeEnabled;
                button.setMessage(toggleLabel("Lunge Boost", lungeEnabled));
            }
        ).bounds(x, y, BUTTON_WIDTH, 20).build());

        y += ROW_HEIGHT;
        int half = (BUTTON_WIDTH - 4) / 2;
        this.addRenderableWidget(Button.builder(
            toggleLabel("Reach", reachEnabled),
            button -> {
                reachEnabled = !reachEnabled;
                button.setMessage(toggleLabel("Reach", reachEnabled));
            }
        ).bounds(x, y, half, 20).build());
        this.addRenderableWidget(Button.builder(
            toggleLabel("Team Check", teamCheck),
            button -> {
                teamCheck = !teamCheck;
                button.setMessage(toggleLabel("Team Check", teamCheck));
            }
        ).bounds(x + half + 4, y, half, 20).build());

        y += ROW_HEIGHT;
        this.addRenderableWidget(Button.builder(
            toggleLabel("Debug", debugEnabled),
            button -> {
                debugEnabled = !debugEnabled;
                button.setMessage(toggleLabel("Debug", debugEnabled));
            }
        ).bounds(x, y, BUTTON_WIDTH, 20).build());

        this.addRenderableWidget(Button.builder(
            Component.literal("Done"),
            button -> saveAndClose()
        ).bounds(x, this.height - 32, BUTTON_WIDTH, 20).build());
    }

    @Override
    public void extractRenderState(
        GuiGraphicsExtractor graphics,
        int mouseX,
        int mouseY,
        float partialTick
    ) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 20, -1);

        if (!debugEnabled) {
            return;
        }

        DebugSnapshot debug = captureDebugSnapshot();
        List<String> lines = debug.lines();
        int y = 148;
        for (String line : lines) {
            graphics.centeredText(this.font, line, this.width / 2, y, -1);
            y += 11;
        }
    }

    @Override
    public void onClose() {
        saveAndClose();
    }

    private DebugSnapshot captureDebugSnapshot() {
        ServerStateTracker.Snapshot tracker = ServerStateTracker.shared().snapshot();
        String targetName = "none";
        double targetDistance = Double.NaN;
        if (tracker.targetId() >= 0 && this.minecraft.level != null && this.minecraft.player != null) {
            Entity entity = this.minecraft.level.getEntity(tracker.targetId());
            if (entity != null) {
                targetName = entity.getName().getString();
                targetDistance = this.minecraft.player.distanceTo(entity);
            }
        }
        return DebugSnapshot.from(tracker, targetName, targetDistance);
    }

    private SpearConfig buildConfig() {
        return new SpearConfig(
            new SpearConfig.OneTapConfig(oneTapEnabled, SpearConfig.OneTapMode.SMART),
            new SpearConfig.LungeConfig(lungeEnabled, SpearConfig.LungeMode.SMART),
            new SpearConfig.ReachConfig(reachEnabled, SpearConfig.ReachMode.SMART, teamCheck),
            debugEnabled
        );
    }

    private void saveAndClose() {
        if (closed) {
            return;
        }
        closed = true;
        SpearConfig next = buildConfig();
        try {
            store.save(next);
            SpearClient.instance().setConfig(next);
        } catch (IOException failure) {
            SpearClient.LOGGER.error("Failed to save Spear Client settings", failure);
        }
        this.minecraft.setScreen(parent);
    }

    private static Component toggleLabel(String label, boolean enabled) {
        return Component.literal(label + ": " + (enabled ? "On" : "Off"));
    }
}
