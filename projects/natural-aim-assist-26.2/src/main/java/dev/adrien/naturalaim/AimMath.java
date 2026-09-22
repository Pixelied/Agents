package dev.adrien.naturalaim;

public final class AimMath {
    private AimMath() {}

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double wrapDegrees(double degrees) {
        double wrapped = degrees % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }

    public static double smoothstep(double edge0, double edge1, double value) {
        if (edge0 == edge1) return value < edge0 ? 0.0 : 1.0;
        double t = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    public static double approach(double current, double target, double maxDelta) {
        if (maxDelta <= 0.0 || current == target) return current;
        double delta = target - current;
        if (Math.abs(delta) <= maxDelta) return target;
        return current + Math.copySign(maxDelta, delta);
    }

    public static double boundedStep(double velocityDegreesPerSecond, double dtSeconds, double remainingErrorDegrees) {
        if (dtSeconds <= 0.0 || remainingErrorDegrees == 0.0) return 0.0;
        double step = velocityDegreesPerSecond * dtSeconds;
        if (Math.signum(step) != Math.signum(remainingErrorDegrees)) return 0.0;
        return Math.copySign(Math.min(Math.abs(step), Math.abs(remainingErrorDegrees)), remainingErrorDegrees);
    }

    public static double intentAlignment(double inputYaw, double inputPitch, double errorYaw, double errorPitch) {
        double inputLength = Math.hypot(inputYaw, inputPitch);
        double errorLength = Math.hypot(errorYaw, errorPitch);
        if (inputLength < 1.0e-6 || errorLength < 1.0e-6) return 0.0;
        return clamp((inputYaw * errorYaw + inputPitch * errorPitch) / (inputLength * errorLength), -1.0, 1.0);
    }

    public static boolean isDeliberatePullAway(double inputYaw, double inputPitch, double errorYaw, double errorPitch,
                                               double minimumInputDegrees, double alignmentThreshold) {
        if (Math.hypot(inputYaw, inputPitch) < minimumInputDegrees) return false;
        return intentAlignment(inputYaw, inputPitch, errorYaw, errorPitch) <= alignmentThreshold;
    }

    public static double clampToRegion(double value, double min, double max) {
        if (min > max) {
            double midpoint = (min + max) * 0.5;
            min = midpoint;
            max = midpoint;
        }
        return clamp(value, min, max);
    }
}
