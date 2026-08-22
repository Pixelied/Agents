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
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;

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
    private final ThreatTimelineSimulator restorationSafetySimulator = new ThreatTimelineSimulator();
    private final Set<SurvivalAction> failedActions = new LinkedHashSet<>();

    private Optional<SurvivalPlan> currentPlan = Optional.empty();
    private Optional<ExecutionStatus> executionStatus = Optional.empty();
    private String dangerFingerprint = "";
    private String dangerSafetyFingerprint = "";
    private String activeThreatScheduleFingerprint = "";

    public SurvivalEngine(SurvivalConfig config, RuntimeAdapter runtime, DecisionHistory history) {
        this(config, runtime, history, new SurvivalPlanner());
    }

    SurvivalEngine(
        SurvivalConfig config,
        RuntimeAdapter runtime,
        DecisionHistory history,
        SurvivalPlanner planner
    ) {
        this.config = new AtomicReference<>(Objects.requireNonNull(config, "config"));
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.history = Objects.requireNonNull(history, "history");
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public void tick() {
        EngineFrame frame = Objects.requireNonNull(runtime.capture(), "runtime frame");
        updateDangerWindow(frame.timeline());
        runtime.maintainRestoration(
            frame,
            config().restoreHandState(),
            lethalWithoutDeathProtection(frame),
            currentPlan.isPresent()
        );

        if (currentPlan.isPresent()) {
            SurvivalAction active = currentPlan.get().action();
            if (shouldReplaceActivePlan(active, frame)) {
                clearCurrentPlan();
            } else {
                ExecutionStatus observed = Objects.requireNonNull(runtime.observe(active, frame), "execution status");
                executionStatus = Optional.of(observed);
                record(frame, active, observed, statusReason(observed));

                if (observed instanceof ExecutionStatus.WaitingForServer) return;
                if (observed instanceof ExecutionStatus.Confirmed) {
                    clearCurrentPlan();
                    return;
                }
                if (observed instanceof ExecutionStatus.Failed failed) {
                    clearCurrentPlan();
                    if (!failed.replanRequired()) return;
                    failedActions.add(active);
                }
            }
        }

        int maxAttempts = Math.max(1, frame.context().limits().maxPlannerCandidates());
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            List<SurvivalAction> candidates = filteredCandidates(frame.candidates());
            SurvivalPlan plan = planner.plan(frame.context(), frame.timeline(), candidates, config().safetyMode());

            if (plan.action() instanceof SurvivalAction.NoAction) {
                clearCurrentPlan();
                record(frame, plan.action(), null, plan.simulation().reason());
                return;
            }

            currentPlan = Optional.of(plan);
            activeThreatScheduleFingerprint = scheduleFingerprint(frame);
            ExecutionStatus started = Objects.requireNonNull(runtime.begin(plan.action(), frame), "execution status");
            executionStatus = Optional.of(started);
            record(frame, plan.action(), started, statusReason(started));

            if (started instanceof ExecutionStatus.Failed failed && failed.replanRequired()) {
                failedActions.add(plan.action());
                clearCurrentPlan();
                continue;
            }
            return;
        }

        clearCurrentPlan();
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

    private boolean lethalWithoutDeathProtection(EngineFrame frame) {
        PlayerSnapshot player = frame.context().player();
        PlayerSnapshot withoutProtection = new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(),
            player.hurtState(), DeathProtectionSnapshot.none(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), player.stateProperties()
        );
        return !restorationSafetySimulator.simulate(withoutProtection, frame.timeline()).survived();
    }

    private boolean shouldReplaceActivePlan(SurvivalAction active, EngineFrame frame) {
        String refreshedScheduleFingerprint = scheduleFingerprint(frame);
        boolean sameAbsoluteSchedule = refreshedScheduleFingerprint.equals(activeThreatScheduleFingerprint);

        int remainingServerTicks = Math.max(0, runtime.remainingServerTicks(active, frame));
        var refreshed = sameAbsoluteSchedule
            ? planner.simulateInFlight(
                frame.context(), frame.timeline(), active, config().safetyMode(), remainingServerTicks
            )
            : planner.simulate(frame.context(), frame.timeline(), active, config().safetyMode());
        if (refreshed.feasible() && refreshed.result().survived()) {
            activeThreatScheduleFingerprint = refreshedScheduleFingerprint;
            return false;
        }

        SurvivalPlan replacement = planner.plan(
            frame.context(),
            frame.timeline(),
            filteredCandidates(frame.candidates()),
            config().safetyMode()
        );
        boolean replace = !(replacement.action() instanceof SurvivalAction.NoAction)
            && !replacement.action().equals(active);
        if (!replace) activeThreatScheduleFingerprint = refreshedScheduleFingerprint;
        return replace;
    }

    private List<SurvivalAction> filteredCandidates(List<SurvivalAction> candidates) {
        List<SurvivalAction> filtered = new ArrayList<>();
        for (SurvivalAction candidate : candidates) {
            if (candidate == null || failedActions.contains(candidate)) continue;
            // These action types have models for future development but no production-safe route
            // generation/dispatcher yet. Legacy config files may still contain the old booleans;
            // never let those stale flags make an unsupported action dispatchable.
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
            clearCurrentPlan();
            return;
        }
        if (!nextSafety.equals(dangerSafetyFingerprint)) {
            dangerSafetyFingerprint = nextSafety;
            failedActions.clear();
        }
    }

    private void clearCurrentPlan() {
        currentPlan = Optional.empty();
        executionStatus = Optional.empty();
        activeThreatScheduleFingerprint = "";
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

    private static String scheduleFingerprint(EngineFrame frame) {
        long clientTick = frame.context().timing().clientTick();
        List<String> schedule = frame.timeline().events().stream()
            .map(event -> safetyFingerprint(event)
                + '|' + saturatingAdd(clientTick, event.impact().earliest())
                + ':' + saturatingAdd(clientTick, event.impact().latest()))
            .sorted()
            .toList();
        return String.join(";", schedule);
    }

    private static String safetyFingerprint(ThreatEvent event) {
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
            + '|' + damage.sourcePosition()
            + '|' + event.confidence()
            + '|' + event.sourcePosition()
            + '|' + event.impactPosition()
            + '|' + event.avoidable()
            + '|' + event.blockable()
            + '|' + event.relocatable()
            + '|' + event.canDisableBlocking();
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        if (increment < 0 && value < Long.MIN_VALUE - increment) return Long.MIN_VALUE;
        return value + increment;
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
