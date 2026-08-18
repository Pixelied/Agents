package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
import dev.adrien.crystaloptimizer.client.execution.HotbarRestocker;
import dev.adrien.crystaloptimizer.client.execution.RotationController;
import dev.adrien.crystaloptimizer.client.execution.VanillaInteractionDispatcher;
import dev.adrien.crystaloptimizer.client.world.ClientCombatSnapshotBuilder;
import dev.adrien.crystaloptimizer.execution.CombatRuntimeEngine;
import dev.adrien.crystaloptimizer.execution.CommitAbortReason;
import dev.adrien.crystaloptimizer.execution.CommitPhase;
import dev.adrien.crystaloptimizer.execution.CommitPolicy;
import dev.adrien.crystaloptimizer.execution.CommitScheduler;
import dev.adrien.crystaloptimizer.execution.ExecutionFeedback;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.execution.PlanExecutionController;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.execution.RuntimeFrame;
import dev.adrien.crystaloptimizer.planner.BeamPlanner;
import dev.adrien.crystaloptimizer.planner.PlannerBudget;
import dev.adrien.crystaloptimizer.planner.RiskBudget;
import dev.adrien.crystaloptimizer.planner.TargetOpportunityScorer;
import dev.adrien.crystaloptimizer.planner.TargetPriority;
import dev.adrien.crystaloptimizer.planner.TargetSelector;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.prediction.TargetPredictor;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionKind;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public final class ClientCombatRuntime {
    private static final int MAX_MOVEMENT_SAMPLES = 8;
    private static final int MAX_DISPATCHES_PER_TICK = 8;
    private static final long PLANNER_BUDGET_NANOS = 2_000_000L;
    private static final int PLANNER_BEAM_WIDTH = 10;
    private static final int PLANNER_MAX_DEPTH = 4;
    private static final int TARGET_SHORTLIST_LIMIT = 2;
    private static final int TARGET_REEVALUATION_INTERVAL_TICKS = 3;
    private static final long TARGET_SELECTION_BUDGET_NANOS = 450_000L;
    private static final int TARGET_SELECTION_BEAM_WIDTH = 4;
    private static final int TARGET_SELECTION_MAX_DEPTH = 2;
    private static final double MIN_PREDICTION_MILLIS = 50.0;
    private static final double MAX_PREDICTION_MILLIS = 250.0;

    private final Minecraft minecraft;
    private final ClientCombatSnapshotBuilder snapshotBuilder;
    private final TargetSelector targetSelector;
    private final TargetPredictor targetPredictor;
    private final RiskBudget riskBudget;
    private final PlannerBudget plannerBudget;
    private final PlannerBudget targetSelectionBudget;
    private final BeamPlanner beamPlanner;
    private final CombatRuntimeEngine engine;
    private final VanillaInteractionDispatcher dispatcher;
    private final HotbarRestocker restocker;
    private final Map<UUID, ArrayDeque<MovementSample>> movementHistory = new HashMap<>();

    private UUID previousTarget;
    private String lastTargetName = "";
    private TimingState lastTiming = TimingState.unknown();
    private int targetReevaluationTicks;
    private boolean enabled;

    public ClientCombatRuntime(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.snapshotBuilder = new ClientCombatSnapshotBuilder(minecraft);
        this.targetSelector = new TargetSelector(0.05, 0.75);
        this.targetPredictor = new TargetPredictor();
        this.riskBudget = RiskBudget.adaptive();
        this.plannerBudget = new PlannerBudget(
            PLANNER_BEAM_WIDTH,
            PLANNER_MAX_DEPTH,
            PLANNER_BUDGET_NANOS
        );
        this.targetSelectionBudget = new PlannerBudget(
            TARGET_SELECTION_BEAM_WIDTH,
            TARGET_SELECTION_MAX_DEPTH,
            TARGET_SELECTION_BUDGET_NANOS
        );
        this.beamPlanner = new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            riskBudget
        );

        InventoryCoordinator inventory = new InventoryCoordinator();
        CommitScheduler scheduler = new CommitScheduler(inventory);
        PlanExecutionController execution = new PlanExecutionController(
            scheduler,
            new CommitPolicy(0.90, 0.80, 0.85)
        );
        this.engine = new CombatRuntimeEngine(execution, frame -> beamPlanner.plan(
            CombatState.fromSnapshot(frame.snapshot(), frame.targetId()),
            plannerBudget,
            frame.predictions()
        ));
        this.dispatcher = new VanillaInteractionDispatcher(
            minecraft,
            new RotationController(minecraft, 35.0f),
            scheduler,
            RotationMode.ADAPTIVE
        );
        this.restocker = new HotbarRestocker(minecraft, inventory);
    }

    public boolean enabled() {
        return enabled;
    }

    public ClientCombatDiagnostics diagnostics() {
        var plan = engine.lastPlan();
        return new ClientCombatDiagnostics(
            enabled,
            lastTargetName,
            engine.phase(),
            plan.map(value -> value.actions().size()).orElse(0),
            plan.map(value -> value.lethal()).orElse(false),
            plan.map(value -> value.robustness()).orElse(0.0),
            engine.lastReconciliationStatus().map(Enum::name).orElse(""),
            engine.lastAbortReason().map(Enum::name).orElse(""),
            lastTiming.roundTripMillis(),
            lastTiming.jitterMillis()
        );
    }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            engine.abort(CommitAbortReason.RECONCILIATION_INVALIDATED);
            resetTargetingState();
        }
    }

    public void tick() {
        if (!enabled) {
            return;
        }

        LocalPlayer self = minecraft.player;
        ClientLevel level = minecraft.level;
        if (self == null || level == null || minecraft.gameMode == null) {
            engine.abort(CommitAbortReason.RECONCILIATION_INVALIDATED);
            resetTargetingState();
            return;
        }

        if (engine.phase() == CommitPhase.NORMAL && restocker.restockOne(self)) {
            return;
        }

        long nowNanos = System.nanoTime();
        TargetSelection selection = resolveTarget(self, level, nowNanos);
        if (selection == null) {
            if (engine.pinnedTargetId().isPresent()) {
                engine.abort(CommitAbortReason.TARGET_OUTSIDE_VIABLE_GEOMETRY);
            }
            previousTarget = null;
            lastTargetName = "";
            targetReevaluationTicks = 0;
            return;
        }

        AbstractClientPlayer target = selection.target();
        CombatSnapshot currentSnapshot = selection.snapshot();
        lastTargetName = target.getName().getString();
        lastTiming = currentSnapshot.timing();
        RuntimeFrame frame = new RuntimeFrame(
            currentSnapshot,
            target.getUUID(),
            selection.predictions()
        );
        engine.tick(frame, action -> feedback(dispatcher.dispatch(action)), nowNanos, MAX_DISPATCHES_PER_TICK);
        previousTarget = target.getUUID();
    }

    private TargetSelection resolveTarget(LocalPlayer self, ClientLevel level, long nowNanos) {
        UUID pinned = engine.pinnedTargetId().orElse(null);
        if (pinned != null) {
            AbstractClientPlayer target = level.players().stream()
                .filter(player -> player.getUUID().equals(pinned))
                .filter(player -> validTarget(self, player, searchRange(self)))
                .findFirst()
                .orElse(null);
            return target == null ? null : snapshotSelection(target, nowNanos);
        }

        if (previousTarget != null && targetReevaluationTicks > 0) {
            AbstractClientPlayer previous = level.players().stream()
                .filter(player -> player.getUUID().equals(previousTarget))
                .filter(player -> validTarget(self, player, searchRange(self)))
                .findFirst()
                .orElse(null);
            if (previous != null) {
                TargetSelection selection = snapshotSelection(previous, nowNanos);
                if (selection != null) {
                    targetReevaluationTicks--;
                    return selection;
                }
            }
            targetReevaluationTicks = 0;
        }

        double range = searchRange(self);
        List<AbstractClientPlayer> candidates = level.players().stream()
            .filter(player -> validTarget(self, player, range))
            .sorted(Comparator.comparingDouble(
                (AbstractClientPlayer candidate) -> shortlistScore(self, candidate)
            ).reversed())
            .limit(TARGET_SHORTLIST_LIMIT)
            .toList();
        if (candidates.isEmpty()) {
            movementHistory.clear();
            return null;
        }

        List<TargetPriority> priorities = new ArrayList<>(candidates.size());
        List<TargetSelection> selections = new ArrayList<>(candidates.size());
        for (AbstractClientPlayer candidate : candidates) {
            var snapshot = snapshotBuilder.build(candidate);
            if (snapshot.isEmpty()) {
                continue;
            }

            CombatSnapshot candidateSnapshot = snapshot.orElseThrow();
            recordMovement(candidate, nowNanos);
            PredictionSet predictions = predict(candidate.getUUID(), candidateSnapshot.timing());
            var plan = beamPlanner.plan(
                CombatState.fromSnapshot(candidateSnapshot, candidate.getUUID()),
                targetSelectionBudget,
                predictions
            );
            priorities.add(TargetOpportunityScorer.priority(
                candidate.getUUID(),
                plan,
                threatScore(self, candidate),
                distance(self, candidate)
            ));
            selections.add(new TargetSelection(candidate, candidateSnapshot, predictions));
        }

        if (priorities.isEmpty()) {
            movementHistory.clear();
            return null;
        }

        TargetPriority selected = targetSelector.select(previousTarget, priorities, riskBudget);
        TargetSelection selection = selections.stream()
            .filter(candidate -> candidate.target().getUUID().equals(selected.targetId()))
            .findFirst()
            .orElseThrow();
        targetReevaluationTicks = TARGET_REEVALUATION_INTERVAL_TICKS;
        movementHistory.keySet().removeIf(id -> selections.stream()
            .noneMatch(candidate -> candidate.target().getUUID().equals(id)));
        return selection;
    }

    private TargetSelection snapshotSelection(AbstractClientPlayer target, long nowNanos) {
        var snapshot = snapshotBuilder.build(target);
        if (snapshot.isEmpty()) {
            return null;
        }
        CombatSnapshot currentSnapshot = snapshot.orElseThrow();
        recordMovement(target, nowNanos);
        PredictionSet predictions = predict(target.getUUID(), currentSnapshot.timing());
        return new TargetSelection(target, currentSnapshot, predictions);
    }

    private double shortlistScore(LocalPlayer self, AbstractClientPlayer target) {
        double sticky = target.getUUID().equals(previousTarget) ? 2.0 : 0.0;
        return sticky + threatScore(self, target) * 1.5 - distance(self, target) * 0.001;
    }

    private static boolean validTarget(LocalPlayer self, AbstractClientPlayer target, double range) {
        if (target == self || target.isRemoved() || target.isDeadOrDying() || target.isSpectator()) {
            return false;
        }
        if (self.isAlliedTo(target)) {
            return false;
        }
        return self.distanceToSqr(target) <= range * range;
    }

    private static double searchRange(LocalPlayer self) {
        double crystalReach = self.entityInteractionRange() + ExplosionKind.CRYSTAL.radius() * 2.0;
        double anchorReach = self.blockInteractionRange() + ExplosionKind.ANCHOR.radius() * 2.0;
        return Math.max(crystalReach, anchorReach);
    }

    private static double threatScore(LocalPlayer self, AbstractClientPlayer target) {
        boolean recentAttacker = self.getLastHurtByMob() == target
            && self.tickCount - self.getLastHurtByMobTimestamp() <= 40;
        return recentAttacker ? 1.0 : 0.0;
    }

    private static double distance(LocalPlayer self, AbstractClientPlayer target) {
        return Math.sqrt(self.distanceToSqr(target));
    }

    private void recordMovement(AbstractClientPlayer target, long nowNanos) {
        ArrayDeque<MovementSample> history = movementHistory.computeIfAbsent(
            target.getUUID(),
            ignored -> new ArrayDeque<>()
        );
        history.addLast(new MovementSample(nowNanos, target.position(), target.getDeltaMovement()));
        while (history.size() > MAX_MOVEMENT_SAMPLES) {
            history.removeFirst();
        }
    }

    private PredictionSet predict(UUID targetId, TimingState timing) {
        ArrayDeque<MovementSample> history = movementHistory.get(targetId);
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("movement history must contain the selected target");
        }
        double measuredMillis = timing.roundTripMillis() + timing.jitterMillis();
        double horizonMillis = Math.max(MIN_PREDICTION_MILLIS, Math.min(MAX_PREDICTION_MILLIS, measuredMillis));
        return targetPredictor.predict(
            List.copyOf(history),
            Duration.ofNanos((long)Math.ceil(horizonMillis * 1_000_000.0))
        );
    }

    private void resetTargetingState() {
        previousTarget = null;
        lastTargetName = "";
        lastTiming = TimingState.unknown();
        targetReevaluationTicks = 0;
        movementHistory.clear();
    }

    private static ExecutionFeedback feedback(DispatchReceipt receipt) {
        return switch (receipt.status()) {
            case SENT -> ExecutionFeedback.sent();
            case DEFERRED -> ExecutionFeedback.deferred();
            case WAITING -> ExecutionFeedback.waiting(receipt.waitTicks());
            case FAILED -> ExecutionFeedback.failed();
        };
    }

    private record TargetSelection(
        AbstractClientPlayer target,
        CombatSnapshot snapshot,
        PredictionSet predictions
    ) {
    }
}
