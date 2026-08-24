package dev.adrien.crystaloptimizer.execution;

public record RotationStep(float yaw, float pitch) {
    public RotationStep {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("rotation must be finite");
        }
        if (pitch < -90.0f || pitch > 90.0f) {
            throw new IllegalArgumentException("pitch must be in [-90, 90]");
        }
    }
}
