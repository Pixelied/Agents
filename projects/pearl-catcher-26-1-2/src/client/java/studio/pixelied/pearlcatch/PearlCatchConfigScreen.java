package studio.pixelied.pearlcatch;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public final class PearlCatchConfigScreen extends Screen {
    private final Screen parent;
    private final PearlCatchConfig config = PearlCatchClient.CONFIG;

    public PearlCatchConfigScreen(Screen parent) {
        super(Component.literal("Pearl Catcher Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = width / 2;
        int left = center - 155;
        int right = center + 5;
        int y = 46;
        int w = 150;
        int h = 20;
        int gap = 24;

        addRenderableWidget(Button.builder(itemSwitching(), b -> {
            config.itemSwitchMode = config.itemSwitchMode.next(); config.save(); b.setMessage(itemSwitching());
        }).bounds(left, y, w, h).build());
        addRenderableWidget(Button.builder(rotation(), b -> {
            config.rotationMode = config.rotationMode.next(); config.save(); b.setMessage(rotation());
        }).bounds(right, y, w, h).build());

        y += gap;
        addRenderableWidget(Button.builder(bool("Enabled", config.enabled), b -> {
            config.enabled = !config.enabled; config.save(); b.setMessage(bool("Enabled", config.enabled));
        }).bounds(left, y, w, h).build());
        addRenderableWidget(Button.builder(bool("Restore slot", config.autoRestoreSlot), b -> {
            config.autoRestoreSlot = !config.autoRestoreSlot; config.save(); b.setMessage(bool("Restore slot", config.autoRestoreSlot));
        }).bounds(right, y, w, h).build());

        y += gap;
        addRenderableWidget(Button.builder(bool("Debug overlay", config.debugOverlay), b -> {
            config.debugOverlay = !config.debugOverlay; config.save(); b.setMessage(bool("Debug overlay", config.debugOverlay));
        }).bounds(left, y, w, h).build());
        addRenderableWidget(Button.builder(bool("Debug visualization", config.debugVisualization), b -> {
            config.debugVisualization = !config.debugVisualization; config.save(); b.setMessage(bool("Debug visualization", config.debugVisualization));
        }).bounds(right, y, w, h).build());

        y += gap;
        addRenderableWidget(Button.builder(bool("Debug export", config.debugExport), b -> {
            config.debugExport = !config.debugExport; config.save(); b.setMessage(bool("Debug export", config.debugExport));
        }).bounds(left, y, w, h).build());
        addRenderableWidget(Button.builder(bool("Debug chat", config.debugChat), b -> {
            config.debugChat = !config.debugChat; config.save(); b.setMessage(bool("Debug chat", config.debugChat));
        }).bounds(right, y, w, h).build());

        y += gap + 4;
        addRenderableWidget(new ValueSlider(left, y, w, h, "Target catch distance", 1, 64,
                () -> config.targetCatchDistance, v -> { config.targetCatchDistance = round(v, 1); config.save(); }));
        addRenderableWidget(new ValueSlider(right, y, w, h, "Crosshair radius", 0.1, 8.0,
                () -> config.maxCrosshairDistance, v -> { config.maxCrosshairDistance = round(v, 2); config.save(); }));

        y += gap + 4;
        addRenderableWidget(new ValueSlider(left, y, w, h, "Sweep start", -90, 90,
                () -> config.pitchSweepStart, v -> { config.pitchSweepStart = round(v, 1); config.save(); }));
        addRenderableWidget(new ValueSlider(right, y, w, h, "Sweep end", -90, 90,
                () -> config.pitchSweepEnd, v -> { config.pitchSweepEnd = round(v, 1); config.save(); }));

        y += gap;
        addRenderableWidget(new ValueSlider(left, y, w, h, "Sweep step", -45, 45,
                () -> config.pitchSweepStep, v -> { config.pitchSweepStep = round(v, 1); config.save(); }));
        addRenderableWidget(new ValueSlider(right, y, w, h, "Ticks / pitch", 20, 400,
                () -> config.maxTicksPerPitch, v -> { config.maxTicksPerPitch = (int)Math.round(v); config.save(); }));

        y += gap;
        addRenderableWidget(new ValueSlider(left, y, w, h, "Between shots", 0, 80,
                () -> config.debugBetweenShotsTicks, v -> { config.debugBetweenShotsTicks = (int)Math.round(v); config.save(); }));
        addRenderableWidget(new ValueSlider(right, y, w, h, "Trail limit", 20, 600,
                () -> config.debugTrailLimit, v -> { config.debugTrailLimit = (int)Math.round(v); config.save(); }));

        y += gap + 10;
        int bottomY = Math.min(y, height - 28);
        addRenderableWidget(Button.builder(Component.literal("Reset to defaults"), b -> {
            config.resetToDefaults();
            minecraft.setScreen(new PearlCatchConfigScreen(parent));
        }).bounds(left, bottomY, w, h).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> {
            config.save(); minecraft.setScreen(parent);
        }).bounds(right, bottomY, w, h).build());
    }

    @Override
    public void onClose() {
        config.save();
        minecraft.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.centeredText(font, title, width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("G = pearl catch • H = vertical catch • B = debug sweep"), width / 2, 31, 0xFFAAAAAA);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private Component itemSwitching() { return Component.literal("Item switching: " + config.itemSwitchMode.label()); }
    private Component rotation() { return Component.literal("Rotation: " + config.rotationMode.label()); }
    private static Component bool(String name, boolean value) { return Component.literal(name + ": " + (value ? "ON" : "OFF")); }
    private static double round(double v, int places) { double s = Math.pow(10, places); return Math.round(v * s) / s; }

    private static final class ValueSlider extends AbstractSliderButton {
        private final String label;
        private final double min;
        private final double max;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;

        private ValueSlider(int x, int y, int width, int height, String label, double min, double max,
                            DoubleSupplier getter, DoubleConsumer setter) {
            super(x, y, width, height, Component.empty(), normalize(getter.getAsDouble(), min, max));
            this.label = label; this.min = min; this.max = max; this.getter = getter; this.setter = setter;
            updateMessage();
        }

        @Override protected void updateMessage() {
            double actual = denormalize(value, min, max);
            String f = Math.abs(actual - Math.rint(actual)) < 0.0001
                    ? Integer.toString((int)Math.round(actual))
                    : String.format(java.util.Locale.ROOT, "%.2f", actual).replaceAll("0+$", "").replaceAll("\\.$", "");
            setMessage(Component.literal(label + ": " + f));
        }

        @Override protected void applyValue() {
            setter.accept(denormalize(value, min, max));
            value = normalize(getter.getAsDouble(), min, max);
        }

        private static double normalize(double v, double min, double max) { return max <= min ? 0.0 : (v - min) / (max - min); }
        private static double denormalize(double v, double min, double max) { return min + v * (max - min); }
    }
}
