package gg.vape.fabric.render;

import gg.vape.runtime.RenderServices;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FabricRenderServices implements RenderServices.Backend {
    private final ThreadLocal<GuiGraphicsExtractor> currentGraphics = new ThreadLocal<>();

    public Scope begin(GuiGraphicsExtractor graphics) {
        if (currentGraphics.get() != null) {
            throw new IllegalStateException("Nested Vape HUD extraction frame");
        }
        currentGraphics.set(graphics);
        return new Scope();
    }

    @Override
    public boolean isFrameActive() {
        return currentGraphics.get() != null;
    }

    @Override
    public void fillRect(float x, float y, float width, float height, int argb) {
        GuiGraphicsExtractor graphics = requireGraphics();
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        graphics.fill(left, top, right, bottom, argb);
    }

    @Override
    public void fillGradient(float x, float y, float width, float height, int topArgb, int bottomArgb) {
        GuiGraphicsExtractor graphics = requireGraphics();
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        graphics.fillGradient(left, top, right, bottom, topArgb, bottomArgb);
    }

    @Override
    public void enableScissor(int x, int y, int width, int height) {
        requireGraphics().enableScissor(x, y, x + width, y + height);
    }

    @Override
    public void disableScissor() {
        requireGraphics().disableScissor();
    }

    private GuiGraphicsExtractor requireGraphics() {
        GuiGraphicsExtractor graphics = currentGraphics.get();
        if (graphics == null) {
            throw new IllegalStateException("Vape render primitive used outside a HUD extraction frame");
        }
        return graphics;
    }

    public final class Scope implements AutoCloseable {
        private boolean closed;

        private Scope() { }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            currentGraphics.remove();
        }
    }
}
