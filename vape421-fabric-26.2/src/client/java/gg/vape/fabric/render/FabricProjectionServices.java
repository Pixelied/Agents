package gg.vape.fabric.render;

import gg.vape.runtime.ProjectionServices;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4fc;

/**
 * Captures Minecraft 26.2's backend-neutral camera matrices during level
 * extraction and exposes Vape's historical camera-relative world projection.
 */
public final class FabricProjectionServices implements ProjectionServices.Backend {
    private volatile Frame frame;

    private record Frame(float[] view, float[] projection, int width, int height) { }

    public void capture(LevelExtractionContext context) {
        var cameraState = context.levelState().cameraRenderState;
        Matrix4fc view = cameraState.viewRotationMatrix;
        Matrix4fc projection = cameraState.projectionMatrix;
        if (view == null || projection == null) {
            frame = null;
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            frame = null;
            return;
        }

        float[] viewElements = new float[16];
        float[] projectionElements = new float[16];
        view.get(viewElements);
        projection.get(projectionElements);
        frame = new Frame(viewElements, projectionElements, width, height);
    }

    @Override
    public boolean hasFrame() {
        return frame != null;
    }

    @Override
    public double[] projectCameraRelative(double x, double y, double z) {
        Frame current = frame;
        if (current == null) {
            return new double[]{Double.NaN, Double.NaN, Double.NaN};
        }

        double[] eye = multiplyColumnMajor(current.view(), x, y, z, 1.0);
        double[] clip = multiplyColumnMajor(
                current.projection(), eye[0], eye[1], eye[2], eye[3]);

        if (!(clip[3] > 0.0)) {
            return new double[]{Double.NaN, Double.NaN, 2.0};
        }

        double ndcX = clip[0] / clip[3];
        double ndcY = clip[1] / clip[3];
        double ndcZ = clip[2] / clip[3];

        return new double[]{
                (1.0 + ndcX) * current.width() * 0.5,
                (1.0 + ndcY) * current.height() * 0.5,
                ndcZ
        };
    }

    private static double[] multiplyColumnMajor(
            float[] matrix, double x, double y, double z, double w) {
        return new double[]{
                matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12] * w,
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13] * w,
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14] * w,
                matrix[3] * x + matrix[7] * y + matrix[11] * z + matrix[15] * w
        };
    }
}
