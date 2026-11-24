package gg.vape.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backend-neutral 2D primitives used while the recovered Vape renderer is
 * migrated away from raw OpenGL. Coordinates are GUI-space pixels.
 */
public final class RenderServices {
    public interface Backend {
        default boolean isFrameActive() { return false; }
        default void fillRect(float x, float y, float width, float height, int argb) { }
        default void fillRoundedRect(float x, float y, float width, float height,
                                     float radius, int cornerMask, int argb) { }
        default void fillGradient(float x, float y, float width, float height,
                                  int topArgb, int bottomArgb) { }
        default void enableScissor(int x, int y, int width, int height) { }
        default void disableScissor() { }
    }

    private static final AtomicReference<Backend> BACKEND =
            new AtomicReference<>(new Backend() { });

    private RenderServices() { }

    public static void install(Backend backend) {
        BACKEND.set(Objects.requireNonNull(backend));
    }

    public static boolean isFrameActive() {
        return BACKEND.get().isFrameActive();
    }

    public static void fillRect(float x, float y, float width, float height, int argb) {
        BACKEND.get().fillRect(x, y, width, height, argb);
    }

    public static void fillRoundedRect(float x, float y, float width, float height,
                                       float radius, int cornerMask, int argb) {
        BACKEND.get().fillRoundedRect(x, y, width, height, radius, cornerMask, argb);
    }

    public static void fillGradient(float x, float y, float width, float height,
                                    int topArgb, int bottomArgb) {
        BACKEND.get().fillGradient(x, y, width, height, topArgb, bottomArgb);
    }

    public static void enableScissor(int x, int y, int width, int height) {
        BACKEND.get().enableScissor(x, y, width, height);
    }

    public static void disableScissor() {
        BACKEND.get().disableScissor();
    }
}
