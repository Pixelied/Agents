package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import java.util.Objects;
import java.util.Optional;

public final class PlanExecutionController {
    private final CommitScheduler scheduler;
    private final CommitPolicy commitPolicy;
    private CombatAction opportunisticAction;
    private int waitingTicks;
    private boolean waitingCommitted;

    public PlanExecutionController(CommitScheduler scheduler, CommitPolicy commitPolicy) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.commitPolicy = Objects.requireNonNull(commitPolicy, "commitPolicy");
    }

    public void offer(CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (scheduler.phase() == CommitPhase.COMMITTED || scheduler.phase() == CommitPhase.RECONCILING) {
            return;
        }

        clearLocalWait();
        if (commitPolicy.shouldCommit(plan)) {
            opportunisticAction = null;
            if (scheduler.arm(plan)) {
                scheduler.commit();
            }
            return;
        }

        opportunisticAction = plan.actions().isEmpty() ? null : plan.actions().getFirst();
    }

    public Optional<CombatAction> nextAction() {
        if (waitingTicks > 0) {
            return Optional.empty();
        }
        if (scheduler.phase() == CommitPhase.COMMITTED) {
            return scheduler.nextAction();
        }
        if (scheduler.phase() == CommitPhase.NORMAL) {
            return Optional.ofNullable(opportunisticAction);
        }
        return Optional.empty();
    }

    public void report(ExecutionFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        if (nextAction().isEmpty()) {
            throw new IllegalStateException("no action is awaiting execution feedback");
        }

        switch (feedback.status()) {
            case SENT -> completeCurrentAction();
            case DEFERRED -> {
            }
            case WAITING -> {
                waitingTicks = feedback.waitTicks();
                waitingCommitted = scheduler.phase() == CommitPhase.COMMITTED;
            }
            case FAILED -> failCurrentAction();
        }
    }

    public void tick() {
        if (waitingTicks <= 0) {
            return;
        }
        waitingTicks--;
        if (waitingTicks == 0) {
            completeWaitingAction();
        }
    }

    public CommitPhase phase() {
        return scheduler.phase();
    }

    public int sentActionCount() {
        return scheduler.sentActionCount();
    }

    public Optional<CommitAbortReason> lastAbortReason() {
        return scheduler.lastAbortReason();
    }

    public void reconciliationComplete() {
        scheduler.reconciliationComplete();
    }

    private void completeCurrentAction() {
        clearLocalWait();
        if (scheduler.phase() == CommitPhase.COMMITTED) {
            scheduler.markActionSent();
        } else {
            opportunisticAction = null;
        }
    }

    private void completeWaitingAction() {
        boolean wasCommitted = waitingCommitted;
        waitingCommitted = false;
        if (wasCommitted && scheduler.phase() == CommitPhase.COMMITTED) {
            scheduler.markActionSent();
        } else if (!wasCommitted) {
            opportunisticAction = null;
        }
    }

    private void failCurrentAction() {
        clearLocalWait();
        opportunisticAction = null;
        if (scheduler.phase() == CommitPhase.COMMITTED) {
            scheduler.abort(CommitAbortReason.ACTION_DISPATCH_FAILED);
        }
    }

    private void clearLocalWait() {
        waitingTicks = 0;
        waitingCommitted = false;
    }
}
