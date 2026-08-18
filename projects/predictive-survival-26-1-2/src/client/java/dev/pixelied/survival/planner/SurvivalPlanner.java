package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timeline.TimelineResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class SurvivalPlanner {
    private final ThreatTimelineSimulator timelineSimulator;

    public SurvivalPlanner() {
        this(new ThreatTimelineSimulator());
    }

    SurvivalPlanner(ThreatTimelineSimulator timelineSimulator) {
        this.timelineSimulator = Objects.requireNonNull(timelineSimulator, "timelineSimulator");
    }

    public SurvivalPlan plan(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        SafetyMode mode
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(mode, "mode");

        ActionSimulation baseline = simulateBaseline(context, timeline);
        if (baseline.result().survived()) {
            return new SurvivalPlan(baseline.action(), baseline, 0, List.of());
        }

        int cap = Math.min(context.limits().maxPlannerCandidates(), candidates.size());
        List<ActionSimulation> evaluated = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            evaluated.add(simulate(context, timeline, Objects.requireNonNull(candidates.get(i), "candidate"), mode));
        }

        ActionSimulation best = evaluated.stream()
            .filter(ActionSimulation::feasible)
            .filter(simulation -> simulation.result().survived())
            .min(SurvivalPlanner::comparePreferred)
            .orElse(baseline);

        return new SurvivalPlan(best.action(), best, cap, evaluated);
    }

    public ActionSimulation simulate(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        SafetyMode mode
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mode, "mode");

        TimelineResult baselineResult = timelineSimulator.simulate(context.player(), timeline);
        String rejection = hardConstraintFailure(context, timeline, action, mode, baselineResult);
        if (rejection != null) {
            return new ActionSimulation(
                action, baselineResult, false, action.reliability(),
                action.consumableCost(), action.disruptionCost(), rejection
            );
        }

        ThreatTimeline transformedTimeline = action.applyTimeline(timeline);
        TimelineResult result = timelineSimulator.simulate(action.apply(context.player()), transformedTimeline);
        return new ActionSimulation(
            action, result, true, action.reliability(),
            action.consumableCost(), action.disruptionCost(), "ok"
        );
    }

    private ActionSimulation simulateBaseline(PredictionContext context, ThreatTimeline timeline) {
        SurvivalAction noAction = new SurvivalAction.NoAction();
        TimelineResult result = timelineSimulator.simulate(context.player(), timeline);
        return new ActionSimulation(noAction, result, true, 1d, 0, 0, "baseline");
    }

    private static String hardConstraintFailure(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        SafetyMode mode,
        TimelineResult baselineResult
    ) {
        if (!action.legal()) return "illegal";
        if (!action.authoritativePrerequisitesSatisfied()) return "authoritative prerequisites missing";
        if (mode != SafetyMode.EXPERIMENTAL && action.deliberateDamage()) {
            return "safety mode forbids deliberate damage";
        }
        if (action instanceof SurvivalAction.RaiseShield shield && !shield.guaranteedBlock()) {
            return "shield block is not guaranteed";
        }
        if (action.requiredServerTicks() > 0) {
            TickWindow requiredImpact = requiredImpactForAction(context, timeline, action, baselineResult);
            if (requiredImpact != null && !context.timing().canCompleteBefore(action.requiredServerTicks(), requiredImpact)) {
                return "server deadline missed";
            }
        }
        return null;
    }

    private static TickWindow requiredImpactForAction(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        TimelineResult baselineResult
    ) {
        if (action instanceof SurvivalAction.EquipDeathProtection && baselineResult.firstLethalEventId().isPresent()) {
            String lethalId = baselineResult.firstLethalEventId().get();
            ThreatEvent lethal = timeline.events().stream()
                .filter(event -> event.id().equals(lethalId))
                .findFirst()
                .orElse(null);
            if (lethal != null) return absoluteImpact(context, lethal);
        }
        return earliestAbsoluteImpact(context, timeline);
    }

    private static TickWindow earliestAbsoluteImpact(PredictionContext context, ThreatTimeline timeline) {
        ThreatEvent earliest = timeline.events().stream()
            .min(Comparator.comparingLong(event -> event.impact().earliest()))
            .orElse(null);
        return earliest == null ? null : absoluteImpact(context, earliest);
    }

    private static TickWindow absoluteImpact(PredictionContext context, ThreatEvent event) {
        long base = context.timing().clientTick();
        return new TickWindow(
            saturatingAdd(base, event.impact().earliest()),
            saturatingAdd(base, event.impact().latest())
        );
    }

    private static int comparePreferred(ActionSimulation left, ActionSimulation right) {
        int comparison = Double.compare(right.reliability(), left.reliability());
        if (comparison != 0) return comparison;

        comparison = Float.compare(right.result().finalHealth(), left.result().finalHealth());
        if (comparison != 0) return comparison;

        comparison = Float.compare(right.result().finalAbsorption(), left.result().finalAbsorption());
        if (comparison != 0) return comparison;

        comparison = Integer.compare(left.consumableCost(), right.consumableCost());
        if (comparison != 0) return comparison;

        return Integer.compare(left.disruptionCost(), right.disruptionCost());
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }
}
