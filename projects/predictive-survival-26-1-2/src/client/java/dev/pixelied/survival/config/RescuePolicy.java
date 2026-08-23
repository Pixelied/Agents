package dev.pixelied.survival.config;

/** Immutable allow-list for production-safe rescue action families. */
public record RescuePolicy(
    boolean deathProtection,
    boolean shields,
    boolean consumables,
    boolean equipment,
    boolean inventoryRouting,
    boolean mainHandTakeover,
    boolean proactiveDualProtection
) {
    public static RescuePolicy smartDefaults() {
        return new RescuePolicy(true, true, true, true, true, true, true);
    }

    public static RescuePolicy totemOnly() {
        return new RescuePolicy(true, false, false, false, true, true, true);
    }

    public static RescuePolicy totemAndShield() {
        return new RescuePolicy(true, true, false, false, true, true, true);
    }
}
