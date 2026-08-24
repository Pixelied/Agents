package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.PlayerSnapshot;

public record DamageResult(
    PlayerSnapshot after,
    DamageTrace trace,
    boolean rejected,
    boolean deathProtectionConsumed,
    boolean postStateUncertain
) {
    public DamageResult(
        PlayerSnapshot after,
        DamageTrace trace,
        boolean rejected,
        boolean deathProtectionConsumed
    ) {
        this(after, trace, rejected, deathProtectionConsumed, false);
    }
}
