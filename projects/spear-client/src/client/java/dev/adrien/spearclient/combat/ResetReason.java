package dev.adrien.spearclient.combat;

public enum ResetReason {
    DISCONNECT,
    LEVEL_CHANGE,
    DEATH,
    RESPAWN,
    CORRECTION,
    LOST_SPEAR,
    TARGET_LOST,
    CONFIG_DISABLED;

    public boolean shouldReleaseOwnedUse() {
        return this == LOST_SPEAR
            || this == TARGET_LOST
            || this == CONFIG_DISABLED;
    }

    public boolean shouldAbortWithoutPackets() {
        return this == CORRECTION
            || this == DISCONNECT
            || this == LEVEL_CHANGE
            || this == RESPAWN
            || this == DEATH;
    }
}
