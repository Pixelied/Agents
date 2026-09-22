package gg.vape.fabric.render;

import com.mojang.blaze3d.platform.NativeImage;
import gg.vape.runtime.FontAtlasServices;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class FabricFontAtlasServices implements FontAtlasServices.Backend {
    private record Atlas(Identifier id, DynamicTexture texture, int width, int height) { }

    private static final AtomicLong NEXT_ID = new AtomicLong();
    private final FabricRenderServices renderServices;

    public FabricFontAtlasServices(FabricRenderServices renderServices) {
        this.renderServices = renderServices;
    }

    @Override
    public boolean available() {
        return renderServices.isFrameActive();
    }

    @Override
    public Object uploadAlphaAtlas(String label, int width, int height, byte[] alpha) {
        if (alpha == null || alpha.length != width * height) {
            throw new IllegalArgumentException("Invalid alpha atlas buffer");
        }

        NativeImage image = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int a = alpha[row + x] & 0xff;
                image.setPixel(x, y, (a << 24) | 0x00ffffff);
            }
        }

        String safeLabel = sanitize(label);
        Identifier id = Identifier.fromNamespaceAndPath(
                "vape421", "font/" + safeLabel + "_" + NEXT_ID.incrementAndGet());
        DynamicTexture texture = new DynamicTexture(() -> "Vape font atlas " + safeLabel, image);
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Atlas(id, texture, width, height);
    }

    @Override
    public void drawGlyph(
            Object atlasHandle,
            float x, float y, float width, float height,
            float u0, float v0, float u1, float v1,
            int argb) {
        if (!(atlasHandle instanceof Atlas atlas)) return;
        GuiGraphicsExtractor graphics = renderServices.currentGraphicsOrNull();
        if (graphics == null) return;

        int drawX = Math.round(x);
        int drawY = Math.round(y);
        int drawWidth = Math.max(1, Math.round(width));
        int drawHeight = Math.max(1, Math.round(height));

        float u = u0 * atlas.width();
        float v = v0 * atlas.height();
        int srcWidth = Math.max(1, Math.round((u1 - u0) * atlas.width()));
        int srcHeight = Math.max(1, Math.round((v1 - v0) * atlas.height()));

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                atlas.id(),
                drawX, drawY,
                u, v,
                drawWidth, drawHeight,
                srcWidth, srcHeight,
                atlas.width(), atlas.height(),
                argb);
    }

    @Override
    public void release(Object atlasHandle) {
        if (atlasHandle instanceof Atlas atlas) {
            Minecraft.getInstance().getTextureManager().release(atlas.id());
        }
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) return "atlas";
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._/-]+", "_");
        return normalized.isBlank() ? "atlas" : normalized;
    }
}
