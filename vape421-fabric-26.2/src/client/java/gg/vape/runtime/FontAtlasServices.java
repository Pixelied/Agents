package gg.vape.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backend-neutral custom-font atlas bridge. The recovered core keeps ownership
 * of glyph rasterization/metrics; the platform backend owns texture upload and
 * drawing.
 */
public final class FontAtlasServices {
    public interface Backend {
        default boolean available() { return false; }
        default Object uploadAlphaAtlas(String label, int width, int height, byte[] alpha) { return null; }
        default void drawGlyph(
                Object atlas,
                float x, float y, float width, float height,
                float u0, float v0, float u1, float v1,
                int argb) { }
        default void release(Object atlas) { }
    }

    private static final AtomicReference<Backend> BACKEND =
            new AtomicReference<>(new Backend() { });

    private FontAtlasServices() { }

    public static void install(Backend backend) {
        BACKEND.set(Objects.requireNonNull(backend));
    }

    public static boolean available() {
        return BACKEND.get().available();
    }

    public static Object uploadAlphaAtlas(String label, int width, int height, byte[] alpha) {
        return BACKEND.get().uploadAlphaAtlas(label, width, height, alpha);
    }

    public static void drawGlyph(
            Object atlas,
            float x, float y, float width, float height,
            float u0, float v0, float u1, float v1,
            int argb) {
        BACKEND.get().drawGlyph(atlas, x, y, width, height, u0, v0, u1, v1, argb);
    }

    public static void release(Object atlas) {
        BACKEND.get().release(atlas);
    }
}
