package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
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

    public int maxEvaluations() {
        return maxEvaluations;
    }

    public ContingencyPlan plan(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile
    ) {
        Objects.requireNonNull(timeline, "timeline");
        return planAcrossScenarios(context, List.of(timeline), candidates, safetyMode, profile);
    }

    /**
     * Finds one bounded rescue sequence that survives every supplied alternative threat scenario.
     * The scenarios are alternatives, not cumulative events: each candidate sequence is replayed
     * independently against every branch and is guaranteed only when every branch survives.
     */
    public ContingencyPlan planAcrossScenarios(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile
    ) {
        return planAcrossScenarios(
            context,
            scenarios,
            candidates,
            safetyMode,
            profile,
            maxEvaluations
        );
    }

    /**
     * Same bounded search with a caller-supplied per-call ceiling. The requested ceiling can only
     * reduce this planner's configured maximum; callers cannot use it to raise the planner budget.
     */
    public ContingencyPlan planAcrossScenarios(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile,
        int evaluationLimit
    ) {
        Objects.requireNonNull(context, "context");
        scenarios = validatedScenarios(scenarios);
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(safetyMode, "safetyMode");
        Objects.requireNonNull(profile, "profile");
        if (evaluationLimit <= 0) throw new IllegalArgumentException("evaluationLimit must be positive");

        ScenarioBaseline baseline = baseline(context, scenarios);
        if (baseline.allSurvived()) return ContingencyPlan.baseline(baseline.representative());

        int candidateCap = Math.min(context.limits().maxPlannerCandidates(), candidates.size());
        List<SurvivalAction> legal = new ArrayList<>(candidateCap);
        for (int i = 0; i < candidateCap; i++) {
            SurvivalAction action = Objects.requireNonNull(candidates.get(i), "candidate");
            if (isSequenceAction(action) && hardConstraintFailure(action, safetyMode) == null) legal.add(action);
        }
        if (legal.isEmpty()) return ContingencyPlan.baseline(baseline.representative());

        SearchBudget budget = new SearchBudget(Math.min(maxEvaluations, evaluationLimit));
        for (int depth = 1; depth <= maxDepth; depth++) {
            List<SequenceEvaluation> survivors = new ArrayList<>();
            enumerate(
                context,
                scenarios,
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
                        : scenarios.size() == 1
                            ? "guaranteed bounded rescue sequence"
                            : "guaranteed bounded rescue sequence across all alternative threat branches"
                );
            }
            if (budget.truncated()) break;
        }

        return new ContingencyPlan(
            List.of(),
            baseline.representative(),
            false,
            budget.evaluations(),
            budget.truncated(),
            budget.truncated()
                ? "sequence search truncated before any guarantee was found"
                : scenarios.size() == 1
                    ? "no guaranteed rescue sequence"
                    : "no rescue sequence survives every alternative threat branch"
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
        Objects.requireNonNull(timeline, "timeline");
        return planInFlightAcrossScenarios(
            context, List.of(timeline), candidates, safetyMode, profile, active, remainingServerTicks
        );
    }

    /**
     * Progress-aware variant of {@link #planAcrossScenarios}. The same already-dispatched prefix
     * and any continuation must survive every alternative branch before it is treated as a guarantee.
     */
    public ContingencyPlan planInFlightAcrossScenarios(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
        List<SurvivalAction> candidates,
        SafetyMode safetyMode,
        RescueProfile profile,
        SurvivalAction active,
        int remainingServerTicks
    ) {
        Objects.requireNonNull(context, "context");
        scenarios = validatedScenarios(scenarios);
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(safetyMode, "safetyMode");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(active, "active");
        if (remainingServerTicks < 0) throw new IllegalArgumentException("remainingServerTicks must be non-negative");

        ScenarioBaseline baseline = baseline(context, scenarios);
        if (baseline.allSurvived()) return ContingencyPlan.baseline(baseline.representative());
        if (!isSequenceAction(active) || hardConstraintFailure(active, safetyMode) != null) {
            return new ContingencyPlan(List.of(), baseline.representative(), false, 0, false,
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
                context, scenarios, legal, profile, totalDepth, prefix, remainingServerTicks, budget, survivors
            );
            if (!survivors.isEmpty()) {
                SequenceEvaluation best = survivors.stream().min(preference(profile)).orElseThrow();
                return new ContingencyPlan(
                    best.steps(), best.result(), true, budget.evaluations(), budget.truncated(),
                    budget.truncated()
                        ? "guaranteed in-flight sequence found before bounded search truncation"
                        : scenarios.size() == 1
                            ? "guaranteed progress-aware in-flight rescue sequence"
                            : "guaranteed progress-aware rescue sequence across all alternative threat branches"
                );
            }
            if (budget.truncated()) break;
        }

        return new ContingencyPlan(
            List.of(), baseline.representative(), false, budget.evaluations(), budget.truncated(),
            budget.truncated()
                ? "in-flight sequence search truncated before any guarantee was found"
                : scenarios.size() == 1
                    ? "no guaranteed in-flight rescue sequence"
                    : "no in-flight rescue sequence survives every alternative threat branch"
        );
    }

    private void enumerateInFlight(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
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
            simulateAcrossScenarios(context, scenarios, prefix, budget)
                .ifPresent(result -> survivors.add(new SequenceEvaluation(List.copyOf(prefix), result)));
            return;
        }

        for (SurvivalAction action : candidates) {
            if (conflictsWithPrefix(prefix, action)) continue;
            long activationTick = saturatingAdd(previousActivationTick, activationDelay(context, action));
            prefix.add(new PlannedStep(action, activationTick));
            enumerateInFlight(
                context, scenarios, candidates, profile, targetDepth, prefix, activationTick, budget, survivors
            );
            prefix.removeLast();
            if (budget.truncated()) return;
        }
    }

    private void enumerate(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
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
            simulateAcrossScenarios(context, scenarios, prefix, budget)
                .ifPresent(result -> survivors.add(new SequenceEvaluation(List.copyOf(prefix), result)));
            return;
        }

        for (SurvivalAction action : candidates) {
            if (conflictsWithPrefix(prefix, action)) continue;
            long activationTick = saturatingAdd(previousActivationTick, activationDelay(context, action));
            prefix.add(new PlannedStep(action, activationTick));
            enumerate(context, scenarios, candidates, profile, targetDepth, prefix, activationTick, budget, survivors);
            prefix.removeLast();
            if (budget.truncated()) return;
        }
    }

    private Optional<TimelineResult> simulateAcrossScenarios(
        PredictionContext context,
        List<ThreatTimeline> scenarios,
        List<PlannedStep> prefix,
        SearchBudget budget
    ) {
        List<ThreatTimelineSimulator.TimedActivation> activations = prefix.stream()
            .map(step -> new ThreatTimelineSimulator.TimedActivation(step.activationTick(), step.action()::apply))
            .toList();
        TimelineResult worst = null;
        for (ThreatTimeline scenario : scenarios) {
            if (!budget.tryEvaluate()) return Optional.empty();
            TimelineResult result = timelineSimulator.simulateWithActivations(
                context.player(), scenario, activations
            );
            if (!result.survived()) return Optional.empty();
            if (worst == null || effectiveHealth(result) < effectiveHealth(worst)) worst = result;
        }
        return Optional.of(Objects.requireNonNull(worst, "scenario result"));
    }

    private ScenarioBaseline baseline(PredictionContext context, List<ThreatTimeline> scenarios) {
        TimelineResult firstLethal = null;
        TimelineResult worstSurvivor = null;
        for (ThreatTimeline scenario : scenarios) {
            TimelineResult result = timelineSimulator.simulate(context.player(), scenario);
            if (!result.survived()) {
                if (firstLethal == null) firstLethal = result;
            } else if (worstSurvivor == null || effectiveHealth(result) < effectiveHealth(worstSurvivor)) {
                worstSurvivor = result;
            }
        }
        if (firstLethal != null) return new ScenarioBaseline(false, firstLethal);
        return new ScenarioBaseline(true, Objects.requireNonNull(worstSurvivor, "baseline result"));
    }

    private static List<ThreatTimeline> validatedScenarios(List<ThreatTimeline> scenarios) {
        Objects.requireNonNull(scenarios, "scenarios");
        if (scenarios.isEmpty()) throw new IllegalArgumentException("at least one threat scenario is required");
        List<ThreatTimeline> copy = new ArrayList<>(scenarios.size());
        for (ThreatTimeline scenario : scenarios) copy.add(Objects.requireNonNull(scenario, "scenario"));
        return List.copyOf(copy);
    }

    private static float effectiveHealth(TimelineResult result) {
        return result.finalHealth() + result.finalAbsorption();
    }

    private static boolean conflictsWithPrefix(List<PlannedStep> prefix, SurvivalAction action) {
        Optional<SourceResource> candidateSource = sourceResource(action);
        Optional<SurvivalAction.Hand> candidateHeldDependency = requiredHeldHand(action);
        boolean candidateNeedsStableMainSelection = requiresStableMainSelection(action);

        for (PlannedStep step : prefix) {
            SurvivalAction previous = step.action();
            if (previous.equals(action)) return true;

            Optional<SourceResource> previousSource = sourceResource(previous);
            if (candidateSource.isPresent()
                && previousSource.isPresent()
                && candidateSource.get().samePhysicalLocation(previousSource.get())) {
                return true;
            }

            if (candidateHeldDependency.isPresent()
                && invalidatesHeldHand(previous, candidateHeldDependency.get())) {
                return true;
            }

            if (candidateNeedsStableMainSelection && changesSelectedMainHand(previous)) {
                return true;
            }
        }
        return false;
    }

    private static Optional<SurvivalAction.Hand> requiredHeldHand(SurvivalAction action) {
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            SurvivalAction.DeathProtectionSourceRef source = protection.sourceItem().orElse(null);
            if (source == null) return Optional.empty();
            if (source.route() instanceof DeathProtectionRoute.AlreadyInHand already) {
                return Optional.of(already.destination() == DeathProtectionRoute.Destination.MAIN_HAND
                    ? SurvivalAction.Hand.MAIN_HAND
                    : SurvivalAction.Hand.OFF_HAND);
            }
            return Optional.empty();
        }

        SurvivalAction.HeldItemRef source = heldItemSource(action).orElse(null);
        if (source == null) return Optional.empty();
        SurvivalItemRoute route = source.route().orElse(null);
        return route == null || route instanceof SurvivalItemRoute.AlreadyHeld
            ? Optional.of(source.hand())
            : Optional.empty();
    }

    private static boolean requiresStableMainSelection(SurvivalAction action) {
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            SurvivalAction.DeathProtectionSourceRef source = protection.sourceItem().orElse(null);
            return source != null
                && source.route() instanceof DeathProtectionRoute.ContainerSwap swap
                && swap.destination() == DeathProtectionRoute.Destination.MAIN_HAND;
        }

        SurvivalAction.HeldItemRef source = heldItemSource(action).orElse(null);
        return source != null
            && source.route().orElse(null) instanceof SurvivalItemRoute.ContainerSwap swap
            && swap.destinationHand() == SurvivalAction.Hand.MAIN_HAND;
    }

    private static boolean invalidatesHeldHand(SurvivalAction action, SurvivalAction.Hand hand) {
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            return protection.hand() == hand;
        }

        SurvivalAction.HeldItemRef source = heldItemSource(action).orElse(null);
        if (source == null || source.hand() != hand) return false;

        if (action instanceof SurvivalAction.RaiseShield) {
            SurvivalItemRoute route = source.route().orElse(null);
            return route != null && !(route instanceof SurvivalItemRoute.AlreadyHeld);
        }

        return action instanceof SurvivalAction.ApplyEffects
            || action instanceof SurvivalAction.SwapEquipment;
    }

    private static boolean changesSelectedMainHand(SurvivalAction action) {
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            return protection.sourceItem()
                .map(SurvivalAction.DeathProtectionSourceRef::route)
                .filter(DeathProtectionRoute.HotbarSelect.class::isInstance)
                .isPresent();
        }

        return heldItemSource(action)
            .flatMap(SurvivalAction.HeldItemRef::route)
            .filter(SurvivalItemRoute.HotbarSelect.class::isInstance)
            .isPresent();
    }

    private static Optional<SurvivalAction.HeldItemRef> heldItemSource(SurvivalAction action) {
        if (action instanceof SurvivalAction.RaiseShield shield) return shield.sourceItem();
        if (action instanceof SurvivalAction.SwapEquipment equipment) return equipment.sourceItem();
        if (action instanceof SurvivalAction.ApplyEffects effects) return effects.sourceItem();
        return Optional.empty();
    }

    private static Optional<SourceResource> sourceResource(SurvivalAction action) {
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            return protection.sourceItem().map(source -> new SourceResource(
                "inventory:" + source.sourceInventoryIndex(),
                source.itemKey(),
                source.componentFingerprint()
            ));
        }

        return heldItemSource(action).map(ContingencyPlanner::sourceResource);
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

        private boolean samePhysicalLocation(SourceResource other) {
            return location.equals(other.location);
        }
    }

    private record ScenarioBaseline(boolean allSurvived, TimelineResult representative) {
        private ScenarioBaseline {
            representative = Objects.requireNonNull(representative, "representative");
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
