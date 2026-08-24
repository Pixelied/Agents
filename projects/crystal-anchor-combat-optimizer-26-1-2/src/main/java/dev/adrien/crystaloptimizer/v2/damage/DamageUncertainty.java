package dev.adrien.crystaloptimizer.v2.damage;

public enum DamageUncertainty {
    PREDICTED_POSITION,
    HURT_THRESHOLD_UNKNOWN,
    ABSORPTION_UNKNOWN,
    TERRAIN_UNOBSERVED,
    ARMOR_STATE_STALE,
    EFFECT_STATE_STALE,
    PENDING_SERVER_ACCEPTANCE
}
