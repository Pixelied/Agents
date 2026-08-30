package dev.pixelied.survival;

import dev.pixelied.survival.core.SurvivalStateInvalidationReason;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Coalesces relevant client packet updates into one optional extra analysis pass without losing why. */
public final class ThreatDirtyTracker {
    private final EnumSet<SurvivalStateInvalidationReason> reasons =
        EnumSet.noneOf(SurvivalStateInvalidationReason.class);

    public void markDirty(SurvivalStateInvalidationReason reason) {
        reasons.add(Objects.requireNonNull(reason, "reason"));
    }

    public Set<SurvivalStateInvalidationReason> consumeReasons() {
        if (reasons.isEmpty()) return Set.of();
        EnumSet<SurvivalStateInvalidationReason> result = EnumSet.copyOf(reasons);
        reasons.clear();
        return Set.copyOf(result);
    }

    public boolean consumeDirty() {
        return !consumeReasons().isEmpty();
    }

    public void reset() {
        reasons.clear();
    }
}
