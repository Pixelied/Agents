package dev.pixelied.survival.core;

public record DamageRange(float min, float max) {
    public DamageRange {
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max");
        }
    }

    public static DamageRange exact(float value) {
        return new DamageRange(value, value);
    }

    public DamageRange scale(float factor) {
        if (factor < 0f || Float.isNaN(factor)) {
            throw new IllegalArgumentException("factor must be non-negative");
        }
        return new DamageRange(min * factor, max * factor);
    }

    public DamageRange subtractFloorZero(float value) {
        return new DamageRange(Math.max(0f, min - value), Math.max(0f, max - value));
    }
}
