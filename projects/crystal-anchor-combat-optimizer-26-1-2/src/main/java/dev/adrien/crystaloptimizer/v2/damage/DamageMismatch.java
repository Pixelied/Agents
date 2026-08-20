package dev.adrien.crystaloptimizer.v2.damage;

public record DamageMismatch(long actionId, Kind kind, float error) {
    public DamageMismatch {
        if (actionId < 0L) {
            throw new IllegalArgumentException("actionId must be non-negative");
        }
        if (kind == null) {
            throw new NullPointerException("kind");
        }
        if (!Float.isFinite(error) || error < 0.0f) {
            throw new IllegalArgumentException("error must be finite and non-negative");
        }
    }

    public enum Kind {
        NONE,
        EXPOSURE_MISMATCH,
        STALE_GEOMETRY,
        HURT_THRESHOLD_UNKNOWN,
        ABSORPTION_UNCERTAINTY,
        EFFECT_STATE_CHANGED,
        TARGET_MOVED,
        ARMOR_STATE_CHANGED,
        ACTION_NOT_SERVER_ACCEPTED,
        INTERFERENCE,
        UNKNOWN
    }
}
