package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.PredictionContext;

import java.util.List;

@FunctionalInterface
public interface LethalOpportunityPredictor {
    List<LethalOpportunity> predict(PredictionContext context);
}
