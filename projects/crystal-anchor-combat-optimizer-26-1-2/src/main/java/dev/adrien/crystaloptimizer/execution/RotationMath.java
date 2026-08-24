package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;

public final class RotationMath {
    private RotationMath() {
    }

    public static RotationStep next(
        float currentYaw,
        float currentPitch,
        float targetYaw,
        float targetPitch,
        RotationMode mode,
        boolean committed,
        float maxDegreesPerStep
    ) {
        Objects.requireNonNull(mode, "mode");
        requireFinite(currentYaw, "currentYaw");
        requireFinite(currentPitch, "currentPitch");
        requireFinite(targetYaw, "targetYaw");
        requireFinite(targetPitch, "targetPitch");
        if (!Float.isFinite(maxDegreesPerStep) || maxDegreesPerStep <= 0.0f) {
            throw new IllegalArgumentException("maxDegreesPerStep must be positive and finite");
        }

        float clampedTargetPitch = clamp(targetPitch, -90.0f, 90.0f);
        boolean snap = mode == RotationMode.INSTANT_REAL
            || (mode == RotationMode.ADAPTIVE && committed);
        if (snap) {
            return new RotationStep(normalizeYaw(targetYaw), clampedTargetPitch);
        }

        float yawDelta = wrapDelta(targetYaw - currentYaw);
        float pitchDelta = clampedTargetPitch - currentPitch;
        float nextYaw = normalizeYaw(currentYaw + clamp(yawDelta, -maxDegreesPerStep, maxDegreesPerStep));
        float nextPitch = clamp(
            currentPitch + clamp(pitchDelta, -maxDegreesPerStep, maxDegreesPerStep),
            -90.0f,
            90.0f
        );
        return new RotationStep(nextYaw, nextPitch);
    }

    private static float wrapDelta(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped > 180.0f) {
            wrapped -= 360.0f;
        } else if (wrapped <= -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0f;
        if (normalized > 180.0f) {
            normalized -= 360.0f;
        } else if (normalized < -180.0f) {
            normalized += 360.0f;
        }
        if (normalized == -180.0f) {
            return 180.0f;
        }
        return normalized;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
