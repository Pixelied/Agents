package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.PlayerSnapshot;

public record DamageResult(
    PlayerSnapshot after,
    DamageTrace trace,
    boolean rejected,
    boolean deathProtectionConsumed
) {
}
