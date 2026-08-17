package dev.pixelied.survival.damage;

public record DeathProtectionSnapshot(boolean mainHandAvailable, boolean offHandAvailable) {
    public static DeathProtectionSnapshot none() {
        return new DeathProtectionSnapshot(false, false);
    }

    public boolean anyHandAvailable() {
        return mainHandAvailable || offHandAvailable;
    }
}
