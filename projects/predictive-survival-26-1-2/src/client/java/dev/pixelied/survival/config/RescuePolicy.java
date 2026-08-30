package dev.pixelied.survival.config;

import java.util.Objects;

/** Immutable allow-list plus deadline-safe preference inputs for production rescue actions. */
public record RescuePolicy(
    boolean deathProtection,
    boolean shields,
    boolean consumables,
    boolean equipment,
    boolean inventoryRouting,
    boolean mainHandTakeover,
    boolean proactiveDualProtection,
    TotemHandPriority totemHandPriority
) {
    public RescuePolicy {
        totemHandPriority = Objects.requireNonNull(totemHandPriority, "totemHandPriority");
    }

    /** Compatibility constructor for schema-v2 policy call sites. */
    public RescuePolicy(
        boolean deathProtection,
        boolean shields,
        boolean consumables,
        boolean equipment,
        boolean inventoryRouting,
        boolean mainHandTakeover,
        boolean proactiveDualProtection
    ) {
        this(
            deathProtection,
            shields,
            consumables,
            equipment,
            inventoryRouting,
            mainHandTakeover,
            proactiveDualProtection,
            TotemHandPriority.SMART
        );
    }

    public RescuePolicy withTotemHandPriority(TotemHandPriority priority) {
        return new RescuePolicy(
            deathProtection,
            shields,
            consumables,
            equipment,
            inventoryRouting,
            mainHandTakeover,
            proactiveDualProtection,
            priority
        );
    }

    public static RescuePolicy smartDefaults() {
        return new RescuePolicy(true, true, true, true, true, true, true, TotemHandPriority.SMART);
    }

    public static RescuePolicy totemOnly() {
        return new RescuePolicy(true, false, false, false, true, true, true, TotemHandPriority.SMART);
    }

    public static RescuePolicy totemAndShield() {
        return new RescuePolicy(true, true, false, false, true, true, true, TotemHandPriority.SMART);
    }
}
