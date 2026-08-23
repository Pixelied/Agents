package dev.pixelied.survival.config;

import java.util.Objects;

public enum RescueProfile {
    TOTEM_ONLY,
    TOTEM_AND_SHIELD,
    CONSERVATIVE_SMART,
    SMART,
    CUSTOM;

    public RescuePolicy resolve(RescuePolicy customPolicy) {
        Objects.requireNonNull(customPolicy, "customPolicy");
        return switch (this) {
            case TOTEM_ONLY -> RescuePolicy.totemOnly();
            case TOTEM_AND_SHIELD -> RescuePolicy.totemAndShield();
            case CONSERVATIVE_SMART, SMART -> RescuePolicy.smartDefaults();
            case CUSTOM -> customPolicy;
        };
    }
}
