package dev.pixelied.survival;

import dev.pixelied.survival.core.SurvivalStateInvalidationReason;

import java.util.Set;

/** GameTest-only bridge to inspect the production dirty coalescer without exposing a public runtime API. */
public final class SurvivalStateInvalidationProbe {
    private SurvivalStateInvalidationProbe() {
    }

    public static Set<SurvivalStateInvalidationReason> consumeReasons() {
        return PredictiveSurvivalClient.consumeThreatDirtyReasons();
    }
}
