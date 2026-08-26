package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Evaluates hypothetical hostile opportunities as alternative risk branches rather than pretending
 * mutually exclusive setup choices all occur on one ordinary damage timeline.
 */
public final class OpportunityRiskEvaluator {
    private final VanillaDamageOracle damageOracle;

    public OpportunityRiskEvaluator() {
        this(new VanillaDamageOracle());
    }

    OpportunityRiskEvaluator(VanillaDamageOracle damageOracle) {
        this.damageOracle = Objects.requireNonNull(damageOracle, "damageOracle");
    }

    public RiskAssessment assess(
        PredictionContext context,
        ThreatTimeline actualTimeline,
        List<LethalOpportunity> opportunities
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(actualTimeline, "actualTimeline");
        Objects.requireNonNull(opportunities, "opportunities");

        List<ThreatTimeline> lethalScenarios = new ArrayList<>();
        Optional<ThreatTimeline> critical = Optional.empty();

        if (damageOracle.lethalWithoutDeathProtection(context.player(), actualTimeline)) {
            lethalScenarios.add(actualTimeline);
            critical = Optional.of(actualTimeline);
        }

        for (LethalOpportunity opportunity : opportunities) {
            LethalOpportunity value = Objects.requireNonNull(opportunity, "opportunity");
            ThreatTimeline scenario = branch(actualTimeline, value.projectedThreat());
            if (!damageOracle.lethalWithoutDeathProtection(context.player(), scenario)) continue;
            lethalScenarios.add(scenario);
            if (critical.isEmpty()) critical = Optional.of(scenario);
        }

        return new RiskAssessment(
            !lethalScenarios.isEmpty(),
            critical,
            List.copyOf(lethalScenarios)
        );
    }

    private static ThreatTimeline branch(ThreatTimeline actualTimeline, ThreatEvent projectedThreat) {
        List<ThreatEvent> events = new ArrayList<>(actualTimeline.events().size() + 1);
        events.addAll(actualTimeline.events());
        events.add(Objects.requireNonNull(projectedThreat, "projectedThreat"));
        return new ThreatTimeline(List.copyOf(events));
    }

    public record RiskAssessment(
        boolean requiresDeathProtection,
        Optional<ThreatTimeline> criticalTimeline,
        List<ThreatTimeline> lethalScenarios
    ) {
        public RiskAssessment {
            criticalTimeline = Objects.requireNonNull(criticalTimeline, "criticalTimeline");
            lethalScenarios = List.copyOf(Objects.requireNonNull(lethalScenarios, "lethalScenarios"));
            if (requiresDeathProtection != !lethalScenarios.isEmpty()) {
                throw new IllegalArgumentException("requiresDeathProtection must match lethal scenario presence");
            }
            if (requiresDeathProtection && criticalTimeline.isEmpty()) {
                throw new IllegalArgumentException("lethal risk requires a critical timeline");
            }
            if (!requiresDeathProtection && criticalTimeline.isPresent()) {
                throw new IllegalArgumentException("safe risk assessment cannot expose a critical timeline");
            }
        }
    }
}
