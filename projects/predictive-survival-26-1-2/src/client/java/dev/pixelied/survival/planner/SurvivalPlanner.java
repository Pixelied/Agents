package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.Confidence;
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
    private static final String IMMEDIATE_PROTECTION_BEST_EFFORT_REASON =
        "best effort: protection cannot be guaranteed from current observation before immediate potential threat";

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
            evaluated.add(simulate(
                context,
                timeline,
                Objects.requireNonNull(candidates.get(i), "candidate"),
                mode,
                baseline.result(),
                false,
                -1
            ));
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
        TimelineResult baseline = timelineSimulator.simulate(
            Objects.requireNonNull(context, "context").player(),
            Objects.requireNonNull(timeline, "timeline")
        );
        return simulate(context, timeline, action, mode, baseline, false, -1);
    }

    /**
     * Conservative compatibility overload for callers that cannot report executor progress yet.
     * The full action duration is treated as still outstanding rather than pretending the action
     * has already completed.
     */
    public ActionSimulation simulateInFlight(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        SafetyMode mode
    ) {
        return simulateInFlight(context, timeline, action, mode, action.requiredServerTicks());
    }

    /**
     * Re-evaluates a dispatched action using the executor's current conservative estimate of the
     * server work still outstanding. Packet transit is not charged a second time: remaining ticks
     * are relative to the current frame.
     */
    public ActionSimulation simulateInFlight(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        SafetyMode mode,
        int remainingServerTicks
    ) {
        if (remainingServerTicks < 0) {
            throw new IllegalArgumentException("remainingServerTicks must be non-negative");
        }
        TimelineResult baseline = timelineSimulator.simulate(
            Objects.requireNonNull(context, "context").player(),
            Objects.requireNonNull(timeline, "timeline")
        );
        return simulate(context, timeline, action, mode, baseline, true, remainingServerTicks);
    }

    private ActionSimulation simulate(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        SafetyMode mode,
        TimelineResult baselineResult,
        boolean inFlight,
        int remainingServerTicks
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(baselineResult, "baselineResult");

        String rejection = hardConstraintFailure(action, mode);
        if (rejection != null) {
            return rejected(action, baselineResult, rejection, DeadlineStatus.NOT_APPLICABLE);
        }

        if (isDelayedStateAction(action)) {
            int remaining = inFlight ? remainingServerTicks : action.requiredServerTicks();
            long activationTick = stateActivationTick(context, action, remaining, inFlight);
            TimelineResult delayedResult = timelineSimulator.simulateWithActivation(
                context.player(),
                timeline,
                activationTick,
                action::apply
            );

            DeadlineStatus completedStatus = activationTick == 0L
                ? DeadlineStatus.NOT_APPLICABLE
                : DeadlineStatus.GUARANTEED;
            if (delayedResult.survived()) {
                return accepted(action, delayedResult, "ok", completedStatus);
            }

            // Distinguish "the action does not save this timeline" from "it would save the timeline
            // if it were already authoritative, but cannot become authoritative soon enough".
            TimelineResult immediateResult = timelineSimulator.simulate(action.apply(context.player()), timeline);
            if (immediateResult.survived()) {
                if (!inFlight && eligibleForImmediateBestEffort(action, timeline, baselineResult)) {
                    return accepted(
                        action,
                        immediateResult,
                        IMMEDIATE_PROTECTION_BEST_EFFORT_REASON,
                        DeadlineStatus.BEST_EFFORT
                    );
                }
                return rejected(action, delayedResult, "server deadline missed", DeadlineStatus.MISSED);
            }

            return accepted(action, delayedResult, "ok", completedStatus);
        }

        // Timeline-transforming actions remain modeled as a single atomic transformation. They are
        // not dispatchable by the production engine, but the model remains available to tests and
        // future route generators without silently changing its established semantics.
        DeadlineStatus deadlineStatus = legacyDeadlineStatus(
            context,
            timeline,
            action,
            baselineResult,
            !inFlight
        );
        if (deadlineStatus == DeadlineStatus.MISSED) {
            return rejected(action, baselineResult, "server deadline missed", DeadlineStatus.MISSED);
        }

        ThreatTimeline transformedTimeline = action.applyTimeline(timeline);
        TimelineResult result = timelineSimulator.simulate(action.apply(context.player()), transformedTimeline);
        String reason = deadlineStatus == DeadlineStatus.BEST_EFFORT
            ? IMMEDIATE_PROTECTION_BEST_EFFORT_REASON
            : "ok";
        return accepted(action, result, reason, deadlineStatus);
    }

    private ActionSimulation simulateBaseline(PredictionContext context, ThreatTimeline timeline) {
        SurvivalAction noAction = new SurvivalAction.NoAction();
        TimelineResult result = timelineSimulator.simulate(context.player(), timeline);
        return new ActionSimulation(noAction, result, true, 1d, 0, 0, "baseline");
    }

    private static String hardConstraintFailure(SurvivalAction action, SafetyMode mode) {
        if (!action.legal()) return "illegal";
        if (!action.authoritativePrerequisitesSatisfied()) return "authoritative prerequisites missing";
        if (mode != SafetyMode.EXPERIMENTAL && action.deliberateDamage()) {
            return "safety mode forbids deliberate damage";
        }
        if (action instanceof SurvivalAction.RaiseShield shield && !shield.guaranteedBlock()) {
            return "shield block is not guaranteed";
        }
        return null;
    }

    private static boolean isDelayedStateAction(SurvivalAction action) {
        return action instanceof SurvivalAction.EquipDeathProtection
            || action instanceof SurvivalAction.RaiseShield
            || action instanceof SurvivalAction.SwapEquipment
            || action instanceof SurvivalAction.ApplyEffects;
    }

    private static long stateActivationTick(
        PredictionContext context,
        SurvivalAction action,
        int remainingServerTicks,
        boolean inFlight
    ) {
        if (inFlight) return remainingServerTicks;
        if (!requiresPacketWindow(action)) return 0L;

        long completion = context.timing().deadline(remainingServerTicks).completionWindow().latest();
        return Math.max(0L, completion - context.timing().clientTick());
    }

    private static boolean eligibleForImmediateBestEffort(
        SurvivalAction action,
        ThreatTimeline timeline,
        TimelineResult baselineResult
    ) {
        if (!(action instanceof SurvivalAction.EquipDeathProtection)) return false;
        if (baselineResult.firstLethalEventId().isEmpty()) return false;
        String lethalId = baselineResult.firstLethalEventId().get();
        ThreatEvent lethal = timeline.events().stream()
            .filter(event -> event.id().equals(lethalId))
            .findFirst()
            .orElse(null);
        return lethal != null
            && lethal.confidence() == Confidence.POTENTIAL
            && lethal.impact().earliest() == 0L;
    }

    private static DeadlineStatus legacyDeadlineStatus(
        PredictionContext context,
        ThreatTimeline timeline,
        SurvivalAction action,
        TimelineResult baselineResult,
        boolean enforceDeadline
    ) {
        if (!enforceDeadline || !requiresPacketWindow(action)) return DeadlineStatus.NOT_APPLICABLE;
        TickWindow requiredImpact = legacyRequiredImpactForAction(context, timeline, action, baselineResult);
        if (requiredImpact == null) return DeadlineStatus.NOT_APPLICABLE;
        if (context.timing().canCompleteBefore(action.requiredServerTicks(), requiredImpact)) {
            return DeadlineStatus.GUARANTEED;
        }
        if (eligibleForImmediateBestEffort(action, timeline, baselineResult)) return DeadlineStatus.BEST_EFFORT;
        return DeadlineStatus.MISSED;
    }

    private static boolean requiresPacketWindow(SurvivalAction action) {
        if (action instanceof SurvivalAction.NoAction) return false;
        if (action instanceof SurvivalAction.RaiseShield shield) {
            return shield.requiredServerTicks() > 0;
        }
        return true;
    }

    private static TickWindow legacyRequiredImpactForAction(
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

    private static ActionSimulation rejected(
        SurvivalAction action,
        TimelineResult result,
        String reason,
        DeadlineStatus deadlineStatus
    ) {
        return new ActionSimulation(
            action,
            result,
            false,
            action.reliability(),
            action.consumableCost(),
            action.disruptionCost(),
            reason,
            deadlineStatus
        );
    }

    private static ActionSimulation accepted(
        SurvivalAction action,
        TimelineResult result,
        String reason,
        DeadlineStatus deadlineStatus
    ) {
        return new ActionSimulation(
            action,
            result,
            true,
            action.reliability(),
            action.consumableCost(),
            action.disruptionCost(),
            reason,
            deadlineStatus
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
