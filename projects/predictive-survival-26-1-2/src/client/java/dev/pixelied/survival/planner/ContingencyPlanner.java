package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timeline.TimelineResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded short-horizon rescue sequence planner. It deliberately searches only production-modeled
 * state actions and delegates all damage/timing safety decisions to ThreatTimelineSimulator.
 */
public final class ContingencyPlanner {
    private static final int DEFAULT_MAX_DEPTH = 3;
    private static final int DEFAULT_MAX_EVALUATIONS = 4096;

    private final ThreatTimelineSimulator timelineSimulator;
    private final int maxDepth;
    private final int maxEvaluations;

    public ContingencyPlanner() {
        this(new ThreatTimelineSimulator(), DEFAULT_MAX_DEPTH, DEFAULT_MAX_EVALUATIONS);
    }

    public ContingencyPlanner(int maxDepth, int maxEvaluations) {
        this(new ThreatTimelineSimulator(), maxDepth, maxEvaluations);
    }

    ContingencyPlanner(ThreatTimelineSimulator timelineSimulator, int maxDepth, int maxEvaluations) {
        this.timelineSimulator = Objects.requireNonNull(timelineSimulator, "timelineSimulator");
        if (maxDepth <= 0 || maxDepth > 3) throw new IllegalArgumentException("maxDepth must be in [1, 3]");
        if (maxEvaluations <= 0) throw new IllegalArgumentException("maxEvaluations must be positive");
        this.maxDepth = maxDepth;
        this.maxEvaluations = maxEvaluations;
    }

    public ContingencyPlan plan(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(safetyMode, "safetyMode");
        Objects.requireNonNull(profile, "profile");

        TimelineResult baseline = timelineSimulator.simulate(context.player(), timeline);
        if (baseline.survived()) return ContingencyPlan.baseline(baseline);

        int candidateCap = Math.min(context.limits().maxPlannerCandidates(), candidates.size());
        List<SurvivalAction> legal = new ArrayList<>(candidateCap);
        for (int i = 0; i < candidateCap; i++) {
            SurvivalAction action = Objects.requireNonNull(candidates.get(i), "candidate");
            if (isSequenceAction(action) && hardConstraintFailure(action, safetyMode) == null) legal.add(action);
        }
        if (legal.isEmpty()) return ContingencyPlan.baseline(baseline);

        SearchBudget budget = new SearchBudget(maxEvaluations);
        for (int depth = 1; depth <= maxDepth; depth++) {
            List<SequenceEvaluation> survivors = new ArrayList<>();
            enumerate(
                context,
                timeline,
                legal,
                profile,
                depth,
                new ArrayList<>(),
                0L,
                budget,
                survivors
            );
            if (!survivors.isEmpty()) {
                SequenceEvaluation best = survivors.stream()
                    .min(preference(profile))
                    .orElseThrow();
                return new ContingencyPlan(
                    best.steps(),
                    best.result(),
                    true,
                    budget.evaluations(),
                    budget.truncated(),
                    budget.truncated()
                        ? "guaranteed sequence found before bounded search truncation"
                        : "guaranteed bounded rescue sequence"
                );
            }
            if (budget.truncated()) break;
        }

        return new ContingencyPlan(
            List.of(),
            baseline,
            false,
            budget.evaluations(),
            budget.truncated(),
            budget.truncated()
                ? "sequence search truncated before any guarantee was found"
                : "no guaranteed rescue sequence"
        );
    }

    /**
     * Replans around an action that is already in flight. The first activation uses the executor's
     * conservative remaining server ticks from the current frame, so elapsed work and packet
     * transit are never charged a second time when the threat schedule changes.
     */
    public ContingencyPlan planInFlight(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile,
        SurvivalAction active,
        int remainingServerTicks
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(timeline, "timeline");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(safetyMode, "safetyMode");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(active, "active");
        if (remainingServerTicks < 0) throw new IllegalArgumentException("remainingServerTicks must be non-negative");

        TimelineResult baseline = timelineSimulator.simulate(context.player(), timeline);
        if (baseline.survived()) return ContingencyPlan.baseline(baseline);
        if (!isSequenceAction(active) || hardConstraintFailure(active, safetyMode) != null) {
            return new ContingencyPlan(List.of(), baseline, false, 0, false,
                "active action is not eligible for bounded in-flight planning");
        }

        int candidateCap = Math.min(context.limits().maxPlannerCandidates(), candidates.size());
        List<SurvivalAction> legal = new ArrayList<>(candidateCap);
        for (int i = 0; i < candidateCap; i++) {
            SurvivalAction action = Objects.requireNonNull(candidates.get(i), "candidate");
            if (action.equals(active)) continue;
            if (isSequenceAction(action) && hardConstraintFailure(action, safetyMode) == null) legal.add(action);
        }

        SearchBudget budget = new SearchBudget(maxEvaluations);
        List<SequenceEvaluation> survivors = new ArrayList<>();
        List<PlannedStep> prefix = new ArrayList<>();
        prefix.add(new PlannedStep(active, remainingServerTicks));

        for (int totalDepth = 1; totalDepth <= maxDepth; totalDepth++) {
            enumerateInFlight(
                context, timeline, legal, profile, totalDepth, prefix, remainingServerTicks, budget, survivors
            );
            if (!survivors.isEmpty()) {
                SequenceEvaluation best = survivors.stream().min(preference(profile)).orElseThrow();
                return new ContingencyPlan(
                    best.steps(), best.result(), true, budget.evaluations(), budget.truncated(),
                    budget.truncated()
                        ? "guaranteed in-flight sequence found before bounded search truncation"
                        : "guaranteed progress-aware in-flight rescue sequence"
                );
            }
            if (budget.truncated()) break;
        }

        return new ContingencyPlan(
            List.of(), baseline, false, budget.evaluations(), budget.truncated(),
            budget.truncated()
                ? "in-flight sequence search truncated before any guarantee was found"
                : "no guaranteed in-flight rescue sequence"
        );
    }

    private void enumerateInFlight(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        RescueProfile profile,
        int targetDepth,
        List<PlannedStep> prefix,
        long previousActivationTick,
        SearchBudget budget,
        List<SequenceEvaluation> survivors
    ) {
        if (budget.truncated()) return;
        if (prefix.size() == targetDepth) {
            if (!budget.tryEvaluate()) return;
            TimelineResult result = timelineSimulator.simulateWithActivations(
                context.player(), timeline, prefix.stream()
                    .map(step -> new ThreatTimelineSimulator.TimedActivation(step.activationTick(), step.action()::apply))
                    .toList()
            );
            if (result.survived()) survivors.add(new SequenceEvaluation(List.copyOf(prefix), result));
            return;
        }

        for (SurvivalAction action : candidates) {
            if (conflictsWithPrefix(prefix, action)) continue;
            long activationTick = saturatingAdd(previousActivationTick, activationDelay(context, action));
            prefix.add(new PlannedStep(action, activationTick));
            enumerateInFlight(
                context, timeline, candidates, profile, targetDepth, prefix, activationTick, budget, survivors
            );
            prefix.removeLast();
            if (budget.truncated()) return;
        }
    }

    private void enumerate(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        RescueProfile profile,
        int targetDepth,
        List<PlannedStep> prefix,
        long previousActivationTick,
        SearchBudget budget,
        List<SequenceEvaluation> survivors
    ) {
        if (budget.truncated()) return;
        if (prefix.size() == targetDepth) {
            if (!budget.tryEvaluate()) return;
            TimelineResult result = timelineSimulator.simulateWithActivations(
                context.player(),
                timeline,
                prefix.stream()
                    .map(step -> new ThreatTimelineSimulator.TimedActivation(step.activationTick(), step.action()::apply))
                    .toList()
            );
            if (result.survived()) {
                survivors.add(new SequenceEvaluation(List.copyOf(prefix), result));
            }
            return;
        }

        for (SurvivalAction action : candidates) {
            if (conflictsWithPrefix(prefix, action)) continue;
            long activationTick = saturatingAdd(previousActivationTick, activationDelay(context, action));
            prefix.add(new PlannedStep(action, activationTick));
            enumerate(context, timeline, candidates, profile, targetDepth, prefix, activationTick, budget, survivors);
            prefix.removeLast();
            if (budget.truncated()) return;
        }
    }

    private static boolean conflictsWithPrefix(List<PlannedStep> prefix, SurvivalAction action) {
        Optional<SourceResource> candidateSource = sourceResource(action);
        for (PlannedStep step : prefix) {
            if (step.action().equals(action)) return true;
            if (candidateSource.isPresent() && candidateSource.equals(sourceResource(step.action()))) return true;
        }
        return false;
    }

    private static Optional<SourceResource> sourceResource(SurvivalAction action) {
        Optional<SurvivalAction.HeldItemRef> source;
        if (action instanceof SurvivalAction.RaiseShield shield) {
            source = shield.sourceItem();
        } else if (action instanceof SurvivalAction.SwapEquipment equipment) {
            source = equipment.sourceItem();
        } else if (action instanceof SurvivalAction.ApplyEffects effects) {
            source = effects.sourceItem();
        } else {
            return Optional.empty();
        }
        return source.map(ContingencyPlanner::sourceResource);
    }

    private static SourceResource sourceResource(SurvivalAction.HeldItemRef source) {
        SurvivalItemRoute route = source.route().orElse(null);
        String location;
        if (route instanceof SurvivalItemRoute.HotbarSelect hotbar) {
            location = "inventory:" + hotbar.hotbarIndex();
        } else if (route instanceof SurvivalItemRoute.ContainerSwap swap) {
            location = "inventory:" + swap.sourceInventoryIndex();
        } else {
            location = "hand:" + source.hand().name();
        }
        return new SourceResource(location, source.itemKey(), source.componentFingerprint());
    }

    private static long activationDelay(PredictionContext context, SurvivalAction action) {
        if (!requiresPacketWindow(action)) return 0L;
        long completion = context.timing().deadline(action.requiredServerTicks()).completionWindow().latest();
        return Math.max(0L, completion - context.timing().clientTick());
    }

    private static boolean requiresPacketWindow(SurvivalAction action) {
        if (action instanceof SurvivalAction.NoAction) return false;
        if (action instanceof SurvivalAction.RaiseShield shield) return shield.requiredServerTicks() > 0;
        return true;
    }

    private static boolean isSequenceAction(SurvivalAction action) {
        return action instanceof SurvivalAction.EquipDeathProtection
            || action instanceof SurvivalAction.RaiseShield
            || action instanceof SurvivalAction.SwapEquipment
            || action instanceof SurvivalAction.ApplyEffects;
    }

    private static String hardConstraintFailure(SurvivalAction action, SafetyMode mode) {
        if (!action.legal()) return "illegal";
        if (!action.authoritativePrerequisitesSatisfied()) return "authoritative prerequisites missing";
        if (mode != SafetyMode.EXPERIMENTAL && action.deliberateDamage()) return "safety mode forbids deliberate damage";
        if (action instanceof SurvivalAction.RaiseShield shield && !shield.guaranteedBlock()) {
            return "shield block is not guaranteed";
        }
        return null;
    }

    private static Comparator<SequenceEvaluation> preference(RescueProfile profile) {
        return (left, right) -> {
            int comparison = Double.compare(right.reliability(), left.reliability());
            if (comparison != 0) return comparison;

            if (profile == RescueProfile.SMART) {
                comparison = Float.compare(right.effectiveHealth(), left.effectiveHealth());
                if (comparison != 0) return comparison;
            }

            comparison = Integer.compare(left.consumableCost(), right.consumableCost());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(left.disruptionCost(), right.disruptionCost());
            if (comparison != 0) return comparison;

            if (profile != RescueProfile.SMART) {
                comparison = Float.compare(right.effectiveHealth(), left.effectiveHealth());
                if (comparison != 0) return comparison;
            }

            comparison = Long.compare(left.lastActivationTick(), right.lastActivationTick());
            if (comparison != 0) return comparison;
            return left.signature().compareTo(right.signature());
        };
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }

    private record SourceResource(String location, String itemKey, int componentFingerprint) {
        private SourceResource {
            location = Objects.requireNonNull(location, "location");
            itemKey = Objects.requireNonNull(itemKey, "itemKey");
        }
    }

    private record SequenceEvaluation(List<PlannedStep> steps, TimelineResult result) {
        private double reliability() {
            double reliability = 1d;
            for (PlannedStep step : steps) reliability = Math.min(reliability, step.action().reliability());
            return reliability;
        }

        private int consumableCost() {
            int cost = 0;
            for (PlannedStep step : steps) cost = saturatingIntAdd(cost, step.action().consumableCost());
            return cost;
        }

        private int disruptionCost() {
            int cost = 0;
            for (PlannedStep step : steps) cost = saturatingIntAdd(cost, step.action().disruptionCost());
            return cost;
        }

        private float effectiveHealth() {
            return result.finalHealth() + result.finalAbsorption();
        }

        private long lastActivationTick() {
            return steps.isEmpty() ? 0L : steps.getLast().activationTick();
        }

        private String signature() {
            return steps.stream().map(step -> step.action().getClass().getSimpleName()).reduce("", (a, b) -> a + '|' + b);
        }

        private static int saturatingIntAdd(int left, int right) {
            long sum = (long) left + right;
            return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
        }
    }

    private static final class SearchBudget {
        private final int max;
        private int evaluations;
        private boolean truncated;

        private SearchBudget(int max) {
            this.max = max;
        }

        private boolean tryEvaluate() {
            if (evaluations >= max) {
                truncated = true;
                return false;
            }
            evaluations++;
            return true;
        }

        private int evaluations() {
            return evaluations;
        }

        private boolean truncated() {
            return truncated;
        }
    }
}
