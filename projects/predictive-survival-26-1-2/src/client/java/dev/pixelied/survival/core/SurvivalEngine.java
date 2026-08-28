package dev.pixelied.survival.core;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.debug.DecisionRecord;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.ActionSimulation;
import dev.pixelied.survival.planner.ContingencyPlan;
import dev.pixelied.survival.planner.ContingencyPlanner;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalPlan;
import dev.pixelied.survival.planner.SurvivalPlanner;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.OpportunityRiskEvaluator;
import dev.pixelied.survival.threat.opportunity.ProtectionContinuity;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public final class SurvivalEngine {
    private final AtomicReference<SurvivalConfig> config;
    private final RuntimeAdapter runtime;
    private final DecisionHistory history;
    private final SurvivalPlanner planner;
    private final ContingencyPlanner contingencyPlanner;
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();
    private final OpportunityRiskEvaluator opportunityRiskEvaluator = new OpportunityRiskEvaluator();
    private final Set<SurvivalAction> failedActions = new LinkedHashSet<>();

    private Optional<SurvivalPlan> currentPlan = Optional.empty();
    private Optional<ContingencyPlan> currentContingency = Optional.empty();
    private Optional<ExecutionStatus> executionStatus = Optional.empty();
    private String dangerFingerprint = "";
    private String dangerSafetyFingerprint = "";

    public SurvivalEngine(SurvivalConfig config, RuntimeAdapter runtime, DecisionHistory history) {
        this(config, runtime, history, new SurvivalPlanner(), new ContingencyPlanner());
    }

    SurvivalEngine(
        SurvivalConfig config,
        RuntimeAdapter runtime,
        DecisionHistory history,
        SurvivalPlanner planner
    ) {
        this(config, runtime, history, planner, new ContingencyPlanner());
    }

    SurvivalEngine(
        SurvivalConfig config,
        RuntimeAdapter runtime,
        DecisionHistory history,
        SurvivalPlanner planner,
        ContingencyPlanner contingencyPlanner
    ) {
        this.config = new AtomicReference<>(Objects.requireNonNull(config, "config"));
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.history = Objects.requireNonNull(history, "history");
        this.planner = Objects.requireNonNull(planner, "planner");
        this.contingencyPlanner = Objects.requireNonNull(contingencyPlanner, "contingencyPlanner");
    }

    public void tick() {
        SurvivalConfig liveConfig = config();
        EngineFrame frame = Objects.requireNonNull(
            runtime.capture(liveConfig.rescuePolicy(), liveConfig.safetyMode()),
            "runtime frame"
        );
        RiskDecision risk = riskDecision(frame);
        // Fingerprint every observed/planning branch identity so a changed alternative invalidates
        // failed-action suppression even when a different branch remains the representative risk.
        updateDangerWindow(frame.planningTimeline());
        runtime.maintainRestoration(
            frame,
            liveConfig.restoreHandState(),
            risk.protectionLatchRequired(),
            currentPlan.isPresent()
        );

        if (currentPlan.isPresent()) {
            SurvivalAction active = currentPlan.get().action();
            if (shouldReplaceActivePlan(
                active,
                frame,
                risk.protectionLatchRequired(),
                risk.scenarios(),
                risk.timeline()
            )) {
                clearCurrentPlan();
            } else {
                ExecutionStatus observed = Objects.requireNonNull(runtime.observe(active, frame), "execution status");
                executionStatus = Optional.of(observed);
                record(frame, active, observed, statusReason(observed));

                if (observed instanceof ExecutionStatus.WaitingForServer) return;
                if (observed instanceof ExecutionStatus.Confirmed) {
                    // This frame already contains the authoritative state that made observe() confirm.
                    // Replan immediately from that state so a second lethal threat does not lose a tick.
                    clearCurrentPlan();
                } else if (observed instanceof ExecutionStatus.Failed failed) {
                    clearCurrentPlan();
                    if (!failed.replanRequired()) return;
                    failedActions.add(active);
                }
            }
        }

        planAndStart(frame, risk.protectionLatchRequired(), risk.scenarios(), risk.timeline());
    }

    private void planAndStart(
        EngineFrame frame,
        boolean protectionLatchRequired,
        List<ThreatTimeline> decisionScenarios,
        ThreatTimeline decisionTimeline
    ) {
        int maxAttempts = Math.max(1, frame.context().limits().maxPlannerCandidates());
        int remainingContingencyEvaluations = contingencyPlanner.maxEvaluations();
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            List<SurvivalAction> candidates = filteredCandidates(frame, protectionLatchRequired);
            ContingencyPlan contingency = contingencyPlanner.planAcrossScenarios(
                frame.context(),
                decisionScenarios,
                candidates,
                config().safetyMode(),
                config().rescueProfile(),
                remainingContingencyEvaluations
            );
            remainingContingencyEvaluations -= contingency.evaluations();

            SurvivalAction selected;
            SurvivalPlan selectedPlan;
            if (contingency.guaranteed()) {
                currentContingency = Optional.of(contingency);
                if (contingency.steps().isEmpty()) {
                    clearCurrentPlan();
                    record(frame, new SurvivalAction.NoAction(), null, contingency.reason());
                    return;
                }
                selected = contingency.steps().getFirst().action();
                selectedPlan = activeStepPlan(frame, decisionTimeline, selected);
            } else {
                currentContingency = Optional.empty();
                List<SurvivalAction> fallbackCandidates = decisionScenarios.size() > 1
                    ? universallySurvivingSingleActions(frame, decisionScenarios, candidates)
                    : candidates;
                selectedPlan = planner.plan(
                    frame.context(), decisionTimeline, fallbackCandidates, config().safetyMode()
                );
                selected = selectedPlan.action();
                if (selected instanceof SurvivalAction.NoAction) {
                    clearCurrentPlan();
                    String reason = decisionScenarios.size() > 1 && fallbackCandidates.isEmpty()
                        ? "no single best-effort action survives every alternative threat branch"
                        : selectedPlan.simulation().reason();
                    record(frame, selected, null, reason);
                    return;
                }
            }

            currentPlan = Optional.of(selectedPlan);
            ExecutionStatus started = Objects.requireNonNull(runtime.begin(selected, frame), "execution status");
            executionStatus = Optional.of(started);
            record(frame, selected, started, statusReason(started));

            if (started instanceof ExecutionStatus.Failed failed && failed.replanRequired()) {
                failedActions.add(selected);
                clearCurrentPlan();
                if (remainingContingencyEvaluations <= 0) {
                    ExecutionStatus exhausted = new ExecutionStatus.Failed(
                        "per-tick contingency evaluation budget exhausted after retryable execution failure",
                        true
                    );
                    executionStatus = Optional.of(exhausted);
                    record(frame, new SurvivalAction.NoAction(), exhausted, statusReason(exhausted));
                    return;
                }
                continue;
            }
            return;
        }

        clearCurrentPlan();
        executionStatus = Optional.of(new ExecutionStatus.Failed("all bounded candidates failed execution", false));
        record(frame, new SurvivalAction.NoAction(), executionStatus.get(), "all bounded candidates failed execution");
    }

    private List<SurvivalAction> universallySurvivingSingleActions(
        EngineFrame frame,
        List<ThreatTimeline> scenarios,
        List<SurvivalAction> candidates
    ) {
        int cap = Math.min(frame.context().limits().maxPlannerCandidates(), candidates.size());
        List<SurvivalAction> universal = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            SurvivalAction candidate = candidates.get(i);
            boolean survivesEveryBranch = true;
            for (ThreatTimeline scenario : scenarios) {
                ActionSimulation simulation = planner.simulate(
                    frame.context(), scenario, candidate, config().safetyMode()
                );
                if (!simulation.feasible() || !simulation.result().survived()) {
                    survivesEveryBranch = false;
                    break;
                }
            }
            if (survivesEveryBranch) universal.add(candidate);
        }
        return List.copyOf(universal);
    }

    private SurvivalPlan activeStepPlan(
        EngineFrame frame,
        ThreatTimeline decisionTimeline,
        SurvivalAction action
    ) {
        ActionSimulation simulation = planner.simulate(
            frame.context(), decisionTimeline, action, config().safetyMode()
        );
        return new SurvivalPlan(action, simulation, 1, List.of(simulation));
    }

    public Optional<SurvivalPlan> currentPlan() {
        return currentPlan;
    }

    public Optional<ContingencyPlan> currentContingency() {
        return currentContingency;
    }

    public Optional<ExecutionStatus> executionStatus() {
        return executionStatus;
    }

    public DecisionHistory history() {
        return history;
    }

    public SurvivalConfig config() {
        return config.get();
    }

    public void replaceConfig(SurvivalConfig replacement) {
        config.set(Objects.requireNonNull(replacement, "replacement"));
        failedActions.clear();
        clearCurrentPlan();
    }

    public void reset() {
        failedActions.clear();
        dangerFingerprint = "";
        dangerSafetyFingerprint = "";
        clearCurrentPlan();
    }

    private RiskDecision riskDecision(EngineFrame frame) {
        if (frame.opportunities().isEmpty()) {
            boolean lethal = damageOracle.lethalWithoutDeathProtection(
                frame.context().player(), frame.planningTimeline()
            );
            return new RiskDecision(lethal, frame.planningTimeline(), List.of(frame.planningTimeline()));
        }

        OpportunityRiskEvaluator.RiskAssessment assessment = opportunityRiskEvaluator.assess(
            frame.context(), frame.actualTimeline(), frame.opportunities()
        );
        if (assessment.lethalScenarios().isEmpty()) {
            // Hypothetical alternatives that are individually nonlethal must not become lethal merely
            // because the planning assembler contains several mutually exclusive options at once.
            return new RiskDecision(false, frame.actualTimeline(), List.of(frame.actualTimeline()));
        }
        return new RiskDecision(
            assessment.requiresDeathProtection(),
            assessment.criticalTimeline().orElseThrow(),
            assessment.lethalScenarios()
        );
    }

    private boolean shouldReplaceActivePlan(
        SurvivalAction active,
        EngineFrame frame,
        boolean protectionLatchRequired,
        List<ThreatTimeline> decisionScenarios,
        ThreatTimeline decisionTimeline
    ) {
        if (protectionLatchRequired
            && !ProtectionContinuity.preservesAuthoritativeProtection(frame.context().player(), active)) {
            return true;
        }
        List<SurvivalAction> candidates = filteredCandidates(frame, protectionLatchRequired);
        int remainingServerTicks = Math.max(0, runtime.remainingServerTicks(active, frame));

        ContingencyPlan inFlight = contingencyPlanner.planInFlightAcrossScenarios(
            frame.context(), decisionScenarios, candidates, config().safetyMode(), config().rescueProfile(),
            active, remainingServerTicks
        );
        ContingencyPlan replacement = contingencyPlanner.planAcrossScenarios(
            frame.context(), decisionScenarios, candidates, config().safetyMode(), config().rescueProfile()
        );

        boolean inFlightKeepsActive = startsWith(inFlight, active);
        boolean replacementStartsElsewhere = replacement.guaranteed()
            && !replacement.steps().isEmpty()
            && !replacement.steps().getFirst().action().equals(active);
        boolean shorterGuaranteedReplacement = replacementStartsElsewhere
            && (!inFlight.guaranteed()
                || !inFlightKeepsActive
                || replacement.steps().size() < inFlight.steps().size());

        if (shorterGuaranteedReplacement) {
            currentContingency = Optional.of(replacement);
            return true;
        }
        if (inFlight.guaranteed() && inFlightKeepsActive) {
            currentContingency = Optional.of(inFlight);
            return false;
        }
        if (replacement.guaranteed() && replacement.steps().isEmpty()) {
            // The danger moved away while an action was already dispatched. There is no generic,
            // server-safe cancellation route for every action, so reconcile the in-flight action.
            currentContingency = Optional.of(replacement);
            return false;
        }

        if (decisionScenarios.size() > 1) {
            // The action is already on the wire and there is no universal replacement. Do not switch
            // to another action justified by only one branch; keep reconciling the dispatched work.
            currentContingency = Optional.empty();
            return false;
        }

        var refreshedSingle = planner.simulateInFlight(
            frame.context(), decisionTimeline, active, config().safetyMode(), remainingServerTicks
        );
        if (refreshedSingle.feasible() && refreshedSingle.result().survived()) {
            currentContingency = Optional.empty();
            return false;
        }

        SurvivalPlan singleReplacement = planner.plan(
            frame.context(), decisionTimeline, candidates, config().safetyMode()
        );
        boolean replace = !(singleReplacement.action() instanceof SurvivalAction.NoAction)
            && !singleReplacement.action().equals(active);
        if (replace) currentContingency = Optional.empty();
        return replace;
    }

    private static boolean startsWith(ContingencyPlan plan, SurvivalAction action) {
        return plan.guaranteed()
            && !plan.steps().isEmpty()
            && plan.steps().getFirst().action().equals(action);
    }

    private List<SurvivalAction> filteredCandidates(EngineFrame frame, boolean protectionLatchRequired) {
        RescuePolicy policy = config().rescuePolicy();
        List<SurvivalAction> filtered = new ArrayList<>();
        for (SurvivalAction candidate : frame.candidates()) {
            if (candidate == null || failedActions.contains(candidate)) continue;
            if (candidate instanceof SurvivalAction.EquipDeathProtection && !policy.deathProtection()) continue;
            if (candidate instanceof SurvivalAction.RaiseShield && !policy.shields()) continue;
            if (candidate instanceof SurvivalAction.ApplyEffects && !policy.consumables()) continue;
            if (candidate instanceof SurvivalAction.SwapEquipment && !policy.equipment()) continue;
            if (protectionLatchRequired
                && !ProtectionContinuity.preservesAuthoritativeProtection(frame.context().player(), candidate)) {
                continue;
            }
            // These action types have models for future development but no production-safe route
            // generation/dispatcher yet. Never let stale flags make them dispatchable.
            if (candidate instanceof SurvivalAction.Relocate
                || candidate instanceof SurvivalAction.PlaceCover
                || candidate instanceof SurvivalAction.PearlRescue) {
                continue;
            }
            filtered.add(candidate);
        }
        return List.copyOf(filtered);
    }

    private void updateDangerWindow(ThreatTimeline timeline) {
        String nextIdentity = identityFingerprint(timeline);
        String nextSafety = failureSafetyFingerprint(timeline);
        if (!nextIdentity.equals(dangerFingerprint)) {
            dangerFingerprint = nextIdentity;
            dangerSafetyFingerprint = nextSafety;
            failedActions.clear();
            // Do not clear a dispatched action here. The fresh in-flight contingency planner uses
            // its actual remaining server work to decide whether it remains part of the safest plan.
            return;
        }
        if (!nextSafety.equals(dangerSafetyFingerprint)) {
            dangerSafetyFingerprint = nextSafety;
            failedActions.clear();
        }
    }

    private void clearCurrentPlan() {
        currentPlan = Optional.empty();
        currentContingency = Optional.empty();
        executionStatus = Optional.empty();
    }

    private void record(
        EngineFrame frame,
        SurvivalAction action,
        ExecutionStatus status,
        String reason
    ) {
        history.add(new DecisionRecord(
            frame.context().timing().clientTick(),
            threatSummary(frame.timeline()),
            action.getClass().getSimpleName(),
            status == null ? "PLANNED" : status.getClass().getSimpleName(),
            Objects.requireNonNull(reason, "reason")
        ));
    }

    private static String statusReason(ExecutionStatus status) {
        if (status instanceof ExecutionStatus.WaitingForServer waiting) return waiting.reason();
        if (status instanceof ExecutionStatus.Confirmed confirmed) return confirmed.detail();
        if (status instanceof ExecutionStatus.Failed failed) return failed.reason();
        return status.getClass().getSimpleName();
    }

    private static String identityFingerprint(ThreatTimeline timeline) {
        List<String> identities = timeline.events().stream()
            .map(event -> event.id() + '|' + event.kind() + '|' + event.damage().sourceKey())
            .sorted()
            .toList();
        return String.join(";", identities);
    }

    private static String failureSafetyFingerprint(ThreatTimeline timeline) {
        List<String> states = timeline.events().stream()
            .map(SurvivalEngine::failureSafetyFingerprint)
            .sorted()
            .toList();
        return String.join(";", states);
    }

    private static String failureSafetyFingerprint(ThreatEvent event) {
        var damage = event.damage();
        List<String> flags = damage.flags().stream()
            .map(Enum::name)
            .sorted()
            .toList();
        return event.id()
            + '|' + event.kind()
            + '|' + damage.sourceKey()
            + '|' + damage.rawDamage().min() + ':' + damage.rawDamage().max()
            + '|' + flags
            + '|' + damage.scalesWithDifficulty()
            + '|' + damage.freezingMultiplier()
            + '|' + damage.piercingProjectile()
            + '|' + damage.applicationHealthThresholdExclusive()
            + '|' + damage.armorEffectivenessAdjustment()
            + '|' + damage.blockingDisableSeconds()
            + '|' + event.confidence()
            + '|' + event.avoidable()
            + '|' + event.blockable()
            + '|' + event.relocatable()
            + '|' + event.canDisableBlocking()
            + '|' + event.requiresAcceptedEventId();
    }

    private static String threatSummary(ThreatTimeline timeline) {
        if (timeline.events().isEmpty()) return "none";
        StringBuilder builder = new StringBuilder();
        int count = Math.min(3, timeline.events().size());
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(',');
            builder.append(timeline.events().get(i).id());
        }
        if (timeline.events().size() > count) builder.append("+").append(timeline.events().size() - count);
        return builder.toString();
    }

    private record RiskDecision(
        boolean protectionLatchRequired,
        ThreatTimeline timeline,
        List<ThreatTimeline> scenarios
    ) {
        private RiskDecision {
            timeline = Objects.requireNonNull(timeline, "timeline");
            scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
            if (scenarios.isEmpty()) throw new IllegalArgumentException("risk scenarios must not be empty");
        }
    }

    public record EngineFrame(
        PredictionContext context,
        ThreatTimeline actualTimeline,
        List<LethalOpportunity> opportunities,
        ThreatTimeline planningTimeline,
        List<SurvivalAction> candidates
    ) {
        public EngineFrame {
            context = Objects.requireNonNull(context, "context");
            actualTimeline = Objects.requireNonNull(actualTimeline, "actualTimeline");
            opportunities = List.copyOf(Objects.requireNonNull(opportunities, "opportunities"));
            planningTimeline = Objects.requireNonNull(planningTimeline, "planningTimeline");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }

        public EngineFrame(
            PredictionContext context,
            ThreatTimeline timeline,
            List<SurvivalAction> candidates
        ) {
            this(context, timeline, List.of(), timeline, candidates);
        }

        /** Compatibility alias for callers that historically consumed the engine's planning risk. */
        public ThreatTimeline timeline() {
            return planningTimeline;
        }
    }

    public interface RuntimeAdapter {
        EngineFrame capture();

        default EngineFrame capture(RescuePolicy policy) {
            Objects.requireNonNull(policy, "policy");
            return capture();
        }

        default EngineFrame capture(RescuePolicy policy, SafetyMode safetyMode) {
            Objects.requireNonNull(policy, "policy");
            Objects.requireNonNull(safetyMode, "safetyMode");
            return capture(policy);
        }

        ExecutionStatus begin(SurvivalAction action, EngineFrame frame);
        ExecutionStatus observe(SurvivalAction action, EngineFrame frame);

        default int remainingServerTicks(SurvivalAction action, EngineFrame frame) {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(frame, "frame");
            return action.requiredServerTicks();
        }

        default void maintainRestoration(
            EngineFrame frame,
            boolean restorationEnabled,
            boolean lethalWithoutProtection,
            boolean survivalActionActive
        ) {
        }
    }
}
