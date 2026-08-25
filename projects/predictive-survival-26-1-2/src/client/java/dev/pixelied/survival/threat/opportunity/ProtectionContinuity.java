package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;

/**
 * Guards an already-authoritative death-protection hand from being transiently replaced while a
 * lethal planning latch is active.
 */
public final class ProtectionContinuity {
    private ProtectionContinuity() {
    }

    public static boolean preservesAuthoritativeProtection(
        PlayerSnapshot player,
        SurvivalAction action
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(action, "action");

        if (!player.deathProtection().anyHandAvailable()) return true;
        PlayerSnapshot after = Objects.requireNonNull(action.apply(player), "action result");
        return after.deathProtection().anyHandAvailable();
    }
}
