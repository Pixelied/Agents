package dev.pixelied.survival.core;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.debug.DecisionRecord;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalPlan;
import dev.pixelied.survival.planner.SurvivalPlanner;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class SurvivalEngine {
    private final SurvivalConfig config;
    private final RuntimeAdapter runtime;
    private final DecisionHistory history;
    private final SurvivalPlanner planner;
    private final Set<SurvivalAction> failedActions = new LinkedHashSet<>();

    private Optional<SurvivalPlan> currentPlan = Optional.empty();
    private Optional<ExecutionStatus> executionStatus = Optional.empty();
    private String dangerFingerprint = "";

    public SurvivalEngine(SurvivalConfig config, RuntimeAdapter runtime, DecisionHistory history) {
        this(config, runtime, history, new SurvivalPlanner());
    }

    SurvivalEngine(
        SurvivalConfig config,
        RuntimeAdapter runtime,
        DecisionHistory history,
        SurvivalPlanner planner
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.history = Objects.requireNonNull(history, "history");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public void tick() {
        EngineFrame frame = Objects.requireNonNull(runtime.capture(), "runtime frame");
        updateDangerWindow(frame.timeline());

        if (currentPlan.isPresent()) {
            SurvivalAction active = currentPlan.get().action();
            ExecutionStatus observed = Objects.requireNonNull(runtime.observe(active, frame), "execution status");
            executionStatus = Optional.of(observed);
            record(frame, active, observed, statusReason(observed));

            if (observed instanceof ExecutionStatus.WaitingForServer) return;
            if (observed instanceof ExecutionStatus.Confirmed) {
                currentPlan = Optional.empty();
                return;
            }
            if (observed instanceof ExecutionStatus.Failed failed) {
                currentPlan = Optional.empty();
                if (!failed.replanRequired()) return;
                failedActions.add(active);
            }
        }

        int maxAttempts = Math.max(1, frame.context().limits().maxPlannerCandidates());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            List<SurvivalAction> candidates = filteredCandidates(frame.candidates());
            SurvivalPlan plan = planner.plan(frame.context(), frame.timeline(), candidates, config.safetyMode());

            if (plan.action() instanceof SurvivalAction.NoAction) {
                currentPlan = Optional.empty();
                executionStatus = Optional.empty();
                record(frame, plan.action(), null, plan.simulation().reason());
                return;
            }

            currentPlan = Optional.of(plan);
            ExecutionStatus started = Objects.requireNonNull(runtime.begin(plan.action(), frame), "execution status");
            executionStatus = Optional.of(started);
            record(frame, plan.action(), started, statusReason(started));

            if (started instanceof ExecutionStatus.Failed failed && failed.replanRequired()) {
                failedActions.add(plan.action());
                currentPlan = Optional.empty();
                continue;
            }
            return;
        }

        currentPlan = Optional.empty();
        executionStatus = Optional.of(new ExecutionStatus.Failed("all bounded candidates failed execution", false));
        record(frame, new SurvivalAction.NoAction(), executionStatus.get(), "all bounded candidates failed execution");
    }

    public Optional<SurvivalPlan> currentPlan() {
        return currentPlan;
    }

    public Optional<ExecutionStatus> executionStatus() {
        return executionStatus;
    }

    public DecisionHistory history() {
        return history;
    }

    public SurvivalConfig config() {
        return config;
    }

    private List<SurvivalAction> filteredCandidates(List<SurvivalAction> candidates) {
        List<SurvivalAction> filtered = new ArrayList<>();
        for (SurvivalAction candidate : candidates) {
            if (candidate == null || failedActions.contains(candidate)) continue;
            if (!config.automaticMovement() && candidate instanceof SurvivalAction.Relocate) continue;
            if (!config.blockPlacementAndClutches()
                && (candidate instanceof SurvivalAction.PlaceCover || candidate instanceof SurvivalAction.PearlRescue)) {
                continue;
            }
            filtered.add(candidate);
        }
        return List.copyOf(filtered);
    }

    private void updateDangerWindow(ThreatTimeline timeline) {
        String next = fingerprint(timeline);
        if (!next.equals(dangerFingerprint)) {
            dangerFingerprint = next;
            failedActions.clear();
            currentPlan = Optional.empty();
            executionStatus = Optional.empty();
        }
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

    private static String fingerprint(ThreatTimeline timeline) {
        StringBuilder builder = new StringBuilder();
        for (ThreatEvent event : timeline.events()) {
            builder.append(event.id())
                .append('@').append(event.impact().earliest())
                .append('-').append(event.impact().latest())
                .append(':').append(event.damage().sourceKey())
                .append(':').append(event.damage().rawDamage().max())
                .append(';');
        }
        return builder.toString();
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

    public record EngineFrame(
        PredictionContext context,
        ThreatTimeline timeline,
        List<SurvivalAction> candidates
    ) {
        public EngineFrame {
            context = Objects.requireNonNull(context, "context");
            timeline = Objects.requireNonNull(timeline, "timeline");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
        }
    }

    public interface RuntimeAdapter {
        EngineFrame capture();
        ExecutionStatus begin(SurvivalAction action, EngineFrame frame);
        ExecutionStatus observe(SurvivalAction action, EngineFrame frame);
    }
}
