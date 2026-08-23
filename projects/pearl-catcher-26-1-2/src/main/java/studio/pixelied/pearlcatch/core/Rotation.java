package studio.pixelied.pearlcatch.core;

public record Rotation(double yaw, double pitch) {
    public Rotation {
        yaw = wrapYaw(yaw);
        pitch = Math.max(-90.0, Math.min(90.0, pitch));
    }

    public static double wrapYaw(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
