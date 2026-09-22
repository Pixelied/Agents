package gg.vape.runtime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Backend-neutral world-to-screen projection boundary.
 *
 * <p>The recovered client historically read fixed-function OpenGL model-view,
 * projection and viewport state. Minecraft 26.2 may render through Vulkan, so
 * Fabric supplies the current frame matrices instead.
 */
public final class ProjectionServices {
    public interface Backend {
        default boolean hasFrame() { return false; }
        default double[] projectCameraRelative(double x, double y, double z) {
            return new double[]{Double.NaN, Double.NaN, Double.NaN};
        }
    }

    private static final AtomicReference<Backend> BACKEND =
            new AtomicReference<>(new Backend() { });

    private ProjectionServices() { }

    public static void install(Backend backend) {
        BACKEND.set(Objects.requireNonNull(backend));
    }

    public static boolean hasFrame() {
        return BACKEND.get().hasFrame();
    }

    public static double[] projectCameraRelative(double x, double y, double z) {
        return BACKEND.get().projectCameraRelative(x, y, z);
    }
}
