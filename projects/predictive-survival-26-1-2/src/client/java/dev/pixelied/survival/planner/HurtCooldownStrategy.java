package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class HurtCooldownStrategy {
    private static final float ADVANTAGE_EPSILON = 0.0001f;

    private final SurvivalPlanner planner;

    public HurtCooldownStrategy() {
        this(new SurvivalPlanner());
    }

    HurtCooldownStrategy(SurvivalPlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public Optional<ActionSimulation> evaluate(
        HurtCooldownCandidate candidate,
        PredictionContext context,
        ThreatTimeline timeline
    ) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");

        if (!candidate.runtimeValidated()) return Optional.empty();
        if (!candidate.action().deliberateDamage()) return Optional.empty();
        if (!trusted(context.player().hurtState().confidence())) return Optional.empty();
        if (!trusted(candidate.precursor().confidence())) return Optional.empty();
        if (candidate.precursor().impact().earliest() != candidate.precursor().impact().latest()) return Optional.empty();
        if (timeline.events().stream().anyMatch(event -> event.id().equals(candidate.precursor().id()))) {
            return Optional.empty();
        }

        ActionSimulation baseline = planner.simulate(
            context,
            timeline,
            new SurvivalAction.NoAction(),
            SafetyMode.EXPERIMENTAL
        );

        List<ThreatEvent> combinedEvents = new ArrayList<>(timeline.events().size() + 1);
        combinedEvents.add(candidate.precursor());
        combinedEvents.addAll(timeline.events());
        ThreatTimeline combined = new ThreatTimeline(combinedEvents);

        ActionSimulation simulation = planner.simulate(
            context,
            combined,
            candidate.action(),
            SafetyMode.EXPERIMENTAL
        );
        if (!simulation.feasible() || !simulation.result().survived()) return Optional.empty();
        if (!materiallyBetter(simulation, baseline)) return Optional.empty();
        return Optional.of(simulation);
    }

    private static boolean trusted(Confidence confidence) {
        return confidence == Confidence.EXACT || confidence == Confidence.MATCHED;
    }

    private static boolean materiallyBetter(ActionSimulation candidate, ActionSimulation baseline) {
        if (candidate.result().survived() && !baseline.result().survived()) return true;
        if (candidate.result().survived() != baseline.result().survived()) return false;

        if (candidate.result().consumedDeathProtectionCount() < baseline.result().consumedDeathProtectionCount()) {
            return true;
        }

        float candidateEffective = candidate.result().finalHealth() + candidate.result().finalAbsorption();
        float baselineEffective = baseline.result().finalHealth() + baseline.result().finalAbsorption();
        return candidateEffective > baselineEffective + ADVANTAGE_EPSILON;
    }
}
