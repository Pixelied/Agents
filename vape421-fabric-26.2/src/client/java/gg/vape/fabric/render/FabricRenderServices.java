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
        graphics.fill(Math.round(x), Math.round(y),
                Math.round(x + width), Math.round(y + height), argb);
    }

    @Override
    public void fillRoundedRect(float x, float y, float width, float height,
                                float radius, int cornerMask, int argb) {
        GuiGraphicsExtractor graphics = requireGraphics();
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        int w = right - left;
        int h = bottom - top;
        if (w <= 0 || h <= 0) return;

        int r = Math.max(0, Math.min(Math.round(radius), Math.min(w, h) / 2));
        if (r == 0 || cornerMask == 0) {
            graphics.fill(left, top, right, bottom, argb);
            return;
        }

        for (int row = 0; row < h; row++) {
            int insetLeft = cornerInset(row, h, r,
                    (cornerMask & 1) != 0, (cornerMask & 8) != 0);
            int insetRight = cornerInset(row, h, r,
                    (cornerMask & 2) != 0, (cornerMask & 4) != 0);
            int rowLeft = Math.min(right, left + insetLeft);
            int rowRight = Math.max(rowLeft, right - insetRight);
            if (rowRight > rowLeft) {
                graphics.fill(rowLeft, top + row, rowRight, top + row + 1, argb);
            }
        }
    }

    private static int cornerInset(int row, int height, int radius,
                                   boolean topRounded, boolean bottomRounded) {
        double dy;
        if (row < radius && topRounded) {
            dy = radius - row - 0.5;
        } else if (row >= height - radius && bottomRounded) {
            dy = row - (height - radius) + 0.5;
        } else {
            return 0;
        }

        double inside = Math.max(0.0, radius * (double) radius - dy * dy);
        return Math.max(0, (int) Math.ceil(radius - Math.sqrt(inside)));
    }

    @Override
    public void fillGradient(float x, float y, float width, float height,
                             int topArgb, int bottomArgb) {
        GuiGraphicsExtractor graphics = requireGraphics();
        graphics.fillGradient(Math.round(x), Math.round(y),
                Math.round(x + width), Math.round(y + height),
                topArgb, bottomArgb);
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
