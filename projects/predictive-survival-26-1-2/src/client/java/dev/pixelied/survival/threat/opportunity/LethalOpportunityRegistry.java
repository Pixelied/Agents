package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.PredictionContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class LethalOpportunityRegistry {
    private static final Comparator<LethalOpportunity> RISK_ORDER = Comparator
        .comparingLong((LethalOpportunity opportunity) -> opportunity.projectedThreat().impact().earliest())
        .thenComparing(Comparator.comparingDouble(
            (LethalOpportunity opportunity) -> opportunity.projectedThreat().damage().rawDamage().max()
        ).reversed())
        .thenComparing(LethalOpportunity::id);

    private final List<LethalOpportunityPredictor> predictors;

    public LethalOpportunityRegistry(List<LethalOpportunityPredictor> predictors) {
        this.predictors = List.copyOf(Objects.requireNonNull(predictors, "predictors"));
    }

    public List<LethalOpportunity> predictAll(PredictionContext context) {
        Objects.requireNonNull(context, "context");
        Map<String, LethalOpportunity> byId = new LinkedHashMap<>();
        for (LethalOpportunityPredictor predictor : predictors) {
            List<LethalOpportunity> predicted = Objects.requireNonNull(
                Objects.requireNonNull(predictor, "predictor").predict(context),
                "predictor result"
            );
            for (LethalOpportunity opportunity : predicted) {
                LethalOpportunity value = Objects.requireNonNull(opportunity, "opportunity");
                byId.merge(value.id(), value, LethalOpportunityRegistry::moreConservative);
            }
        }

        List<LethalOpportunity> ordered = new ArrayList<>(byId.values());
        ordered.sort(RISK_ORDER);
        int cap = Math.min(context.limits().maxOpportunities(), ordered.size());
        return List.copyOf(ordered.subList(0, cap));
    }

    private static LethalOpportunity moreConservative(LethalOpportunity first, LethalOpportunity second) {
        return RISK_ORDER.compare(first, second) <= 0 ? first : second;
    }
}
