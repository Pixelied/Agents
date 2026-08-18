package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.reconcile.ReconciliationGate;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public final class CombatRuntimeEngine {
    private static final double MINIMUM_RECONCILIATION_HOLDOFF_MILLIS = 50.0;
    private static final double MINIMUM_RECONCILIATION_TIMEOUT_MILLIS = 250.0;

    private final PlanExecutionController controller;
    private final PlanExecutionDriver driver;
    private final RuntimePlanner planner;

    private ReconciliationGate reconciliationGate;
    private UUID pinnedTargetId;
    private long reconciliationNotBeforeNanos;
    private ReconciliationGate.Status lastReconciliationStatus;
    private CombatPlan lastPlan;

    public CombatRuntimeEngine(PlanExecutionController controller, RuntimePlanner planner) {
        this.controller = Objects.requireNonNull(controller, "controller");
        this.driver = new PlanExecutionDriver(controller);
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public int tick(
        RuntimeFrame frame,
        Function<CombatAction, ExecutionFeedback> dispatcher,
        long nowNanos,
        int maxDispatches
    ) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(dispatcher, "dispatcher");
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        if (maxDispatches <= 0) {
            throw new IllegalArgumentException("maxDispatches must be positive");
        }

        controller.tick();

        if (controller.phase() == CommitPhase.RECONCILING) {
            return reconcile(frame, nowNanos);
        }

        if (controller.phase() == CommitPhase.COMMITTED) {
            if (!acceptsTarget(frame.targetId())) {
                return 0;
            }
            int attempts = driver.drive(dispatcher, maxDispatches);
            clearContextIfCommitAborted();
            return attempts;
        }

        lastPlan = Objects.requireNonNull(planner.plan(frame), "runtime planner returned null");
        controller.offer(lastPlan);
        if (controller.phase() == CommitPhase.COMMITTED) {
            startReconciliationContext(frame, nowNanos);
        }

        int attempts = driver.drive(dispatcher, maxDispatches);
        clearContextIfCommitAborted();
        return attempts;
    }

    public CommitPhase phase() {
        return controller.phase();
    }

    public Optional<UUID> pinnedTargetId() {
        return Optional.ofNullable(pinnedTargetId);
    }

    public boolean acceptsTarget(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return pinnedTargetId == null || pinnedTargetId.equals(targetId);
    }

    public Optional<ReconciliationGate.Status> lastReconciliationStatus() {
        return Optional.ofNullable(lastReconciliationStatus);
    }

    public Optional<CombatPlan> lastPlan() {
        return Optional.ofNullable(lastPlan);
    }

    public Optional<CommitAbortReason> lastAbortReason() {
        return controller.lastAbortReason();
    }

    public void abort(CommitAbortReason reason) {
        controller.abort(Objects.requireNonNull(reason, "reason"));
        clearReconciliationContext();
    }

    private int reconcile(RuntimeFrame frame, long nowNanos) {
        if (reconciliationGate == null || pinnedTargetId == null) {
            throw new IllegalStateException("reconciling phase has no pinned baseline");
        }
        if (!pinnedTargetId.equals(frame.targetId())) {
            return 0;
        }
        if (nowNanos < reconciliationNotBeforeNanos) {
            return 0;
        }

        ReconciliationGate.Status status = reconciliationGate.evaluate(
            frame.snapshot(),
            pinnedTargetId,
            nowNanos
        );
        if (status == ReconciliationGate.Status.WAITING) {
            return 0;
        }

        lastReconciliationStatus = status;
        controller.reconciliationComplete();
        clearReconciliationContext();
        return 0;
    }

    private void startReconciliationContext(RuntimeFrame frame, long nowNanos) {
        TimingWindow window = TimingWindow.from(frame.snapshot().timing());
        pinnedTargetId = frame.targetId();
        reconciliationNotBeforeNanos = saturatingAdd(nowNanos, window.holdoffNanos());
        reconciliationGate = ReconciliationGate.start(
            frame.snapshot(),
            frame.targetId(),
            nowNanos,
            window.timeoutNanos()
        );
        lastReconciliationStatus = null;
    }

    private void clearContextIfCommitAborted() {
        if (pinnedTargetId != null && controller.phase() == CommitPhase.NORMAL) {
            clearReconciliationContext();
        }
    }

    private void clearReconciliationContext() {
        reconciliationGate = null;
        pinnedTargetId = null;
        reconciliationNotBeforeNanos = 0L;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record TimingWindow(long holdoffNanos, long timeoutNanos) {
        static TimingWindow from(TimingState timing) {
            Objects.requireNonNull(timing, "timing");
            double measuredSettleMillis = timing.roundTripMillis() + timing.jitterMillis() * 2.0;
            double holdoffMillis = Math.max(
                MINIMUM_RECONCILIATION_HOLDOFF_MILLIS,
                measuredSettleMillis
            );
            double timeoutMillis = Math.max(
                MINIMUM_RECONCILIATION_TIMEOUT_MILLIS,
                holdoffMillis * 2.0 + MINIMUM_RECONCILIATION_HOLDOFF_MILLIS
            );
            return new TimingWindow(
                millisToNanos(holdoffMillis),
                millisToNanos(timeoutMillis)
            );
        }

        private static long millisToNanos(double millis) {
            double nanos = millis * 1_000_000.0;
            if (nanos >= Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return Math.max(1L, (long) Math.ceil(nanos));
        }
    }
}
