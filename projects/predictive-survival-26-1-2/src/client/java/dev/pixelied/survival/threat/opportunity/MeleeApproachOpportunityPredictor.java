package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.PredictionContext;

import java.util.List;

/** Predicts lethal melee opportunities that become server-reachable on a future observed-motion tick. */
public final class MeleeApproachOpportunityPredictor implements LethalOpportunityPredictor {
    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        return List.of();
    }
}
