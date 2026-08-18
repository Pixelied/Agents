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
import dev.adrien.crystaloptimizer.planner.TargetPriority;
import dev.adrien.crystaloptimizer.planner.TargetSelector;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.prediction.TargetPredictor;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionKind;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;

public final class ClientCombatRuntime {
    private static final int MAX_MOVEMENT_SAMPLES = 8;
    private static final int MAX_DISPATCHES_PER_TICK = 8;
    private static final long PLANNER_BUDGET_NANOS = 2_000_000L;
    private static final int PLANNER_BEAM_WIDTH = 10;
    private static final int PLANNER_MAX_DEPTH = 4;
    private static final double MIN_PREDICTION_MILLIS = 50.0;
    private static final double MAX_PREDICTION_MILLIS = 250.0;

    private final Minecraft minecraft;
    private final ClientCombatSnapshotBuilder snapshotBuilder;
    private final TargetSelector targetSelector;
    private final TargetPredictor targetPredictor;
    private final RiskBudget riskBudget;
    private final PlannerBudget plannerBudget;
    private final BeamPlanner beamPlanner;
    private final CombatRuntimeEngine engine;
    private final VanillaInteractionDispatcher dispatcher;
    private final HotbarRestocker restocker;
    private final Map<UUID, ArrayDeque<MovementSample>> movementHistory = new HashMap<>();

    private UUID previousTarget;
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

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }
        this.enabled = enabled;
        if (!enabled) {
            engine.abort(CommitAbortReason.RECONCILIATION_INVALIDATED);
            previousTarget = null;
            movementHistory.clear();
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
            previousTarget = null;
            movementHistory.clear();
            return;
        }

        if (engine.phase() == CommitPhase.NORMAL && restocker.restockOne(self)) {
            return;
        }

        AbstractClientPlayer target = resolveTarget(self, level);
        if (target == null) {
            if (engine.pinnedTargetId().isPresent()) {
                engine.abort(CommitAbortReason.TARGET_OUTSIDE_VIABLE_GEOMETRY);
            }
            previousTarget = null;
            return;
        }

        var snapshot = snapshotBuilder.build(target);
        if (snapshot.isEmpty()) {
            engine.abort(CommitAbortReason.TARGET_OUTSIDE_VIABLE_GEOMETRY);
            previousTarget = null;
            return;
        }

        long nowNanos = System.nanoTime();
        recordMovement(target, nowNanos);
        PredictionSet predictions = predict(target.getUUID(), snapshot.orElseThrow().timing());
        RuntimeFrame frame = new RuntimeFrame(
            snapshot.orElseThrow(),
            target.getUUID(),
            predictions
        );
        engine.tick(frame, action -> feedback(dispatcher.dispatch(action)), nowNanos, MAX_DISPATCHES_PER_TICK);
        previousTarget = target.getUUID();
    }

    private AbstractClientPlayer resolveTarget(LocalPlayer self, ClientLevel level) {
        UUID pinned = engine.pinnedTargetId().orElse(null);
        if (pinned != null) {
            return level.players().stream()
                .filter(player -> player.getUUID().equals(pinned))
                .filter(player -> validTarget(self, player, searchRange(self)))
                .findFirst()
                .orElse(null);
        }

        double range = searchRange(self);
        List<AbstractClientPlayer> candidates = level.players().stream()
            .filter(player -> validTarget(self, player, range))
            .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<TargetPriority> priorities = new ArrayList<>(candidates.size());
        for (AbstractClientPlayer candidate : candidates) {
            priorities.add(priority(self, candidate));
        }
        TargetPriority selected = targetSelector.select(previousTarget, priorities, riskBudget);
        return candidates.stream()
            .filter(candidate -> candidate.getUUID().equals(selected.targetId()))
            .findFirst()
            .orElse(null);
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

    private static TargetPriority priority(LocalPlayer self, AbstractClientPlayer target) {
        double maxHealth = Math.max(1.0, target.getMaxHealth());
        double healthWeakness = clamp01(1.0 - target.getHealth() / maxHealth);
        int armorPieces = 0;
        for (EquipmentSlot slot : List.of(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
        )) {
            if (!target.getItemBySlot(slot).isEmpty()) {
                armorPieces++;
            }
        }
        double armorWeakness = 1.0 - armorPieces / 4.0;
        boolean visibleTotem = target.getMainHandItem().is(Items.TOTEM_OF_UNDYING)
            || target.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
        double killOpportunity = clamp01(
            0.40 + healthWeakness * 0.40 + armorWeakness * 0.20 - (visibleTotem ? 0.10 : 0.0)
        );

        boolean recentAttacker = self.getLastHurtByMob() == target
            && self.tickCount - self.getLastHurtByMobTimestamp() <= 40;
        double threat = recentAttacker ? 1.0 : 0.0;
        double distance = Math.sqrt(self.distanceToSqr(target));
        return new TargetPriority(target.getUUID(), killOpportunity, threat, distance);
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
        movementHistory.keySet().removeIf(id -> !id.equals(target.getUUID()));
    }

    private PredictionSet predict(UUID targetId, dev.adrien.crystaloptimizer.sim.model.TimingState timing) {
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

    private static ExecutionFeedback feedback(DispatchReceipt receipt) {
        return switch (receipt.status()) {
            case SENT -> ExecutionFeedback.sent();
            case DEFERRED -> ExecutionFeedback.deferred();
            case WAITING -> ExecutionFeedback.waiting(receipt.waitTicks());
            case FAILED -> ExecutionFeedback.failed();
        };
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
