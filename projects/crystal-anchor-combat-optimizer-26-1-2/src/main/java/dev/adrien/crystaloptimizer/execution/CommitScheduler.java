package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import java.util.Objects;
import java.util.Optional;

public final class CommitScheduler {
    private final InventoryCoordinator inventory;
    private CommitPhase phase = CommitPhase.NORMAL;
    private CombatPlan activePlan;
    private int sentActionCount;
    private CommitAbortReason lastAbortReason;

    public CommitScheduler(InventoryCoordinator inventory) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        inventory.addReservationListener(this::onReservationGranted);
    }

    public CommitPhase phase() {
        return phase;
    }

    public CombatPlan activePlan() {
        return activePlan;
    }

    public Optional<CommitAbortReason> lastAbortReason() {
        return Optional.ofNullable(lastAbortReason);
    }

    public int sentActionCount() {
        return sentActionCount;
    }

    public boolean arm(CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (phase == CommitPhase.COMMITTED || phase == CommitPhase.RECONCILING) {
            return false;
        }
        activePlan = plan;
        sentActionCount = 0;
        lastAbortReason = null;
        phase = CommitPhase.ARMED;
        return true;
    }

    public boolean commit() {
        if (phase != CommitPhase.ARMED || activePlan == null) {
            return false;
        }
        phase = activePlan.actions().isEmpty() ? CommitPhase.RECONCILING : CommitPhase.COMMITTED;
        return true;
    }

    public void offer(CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (phase == CommitPhase.COMMITTED || phase == CommitPhase.RECONCILING) {
            return;
        }
        if (phase == CommitPhase.NORMAL || activePlan == null) {
            arm(plan);
            return;
        }
        if (plan.score().compareTo(activePlan.score()) > 0) {
            activePlan = plan;
            sentActionCount = 0;
        }
    }

    public Optional<CombatAction> nextAction() {
        if (phase != CommitPhase.COMMITTED || activePlan == null) {
            return Optional.empty();
        }
        if (sentActionCount >= activePlan.actions().size()) {
            return Optional.empty();
        }
        return Optional.of(activePlan.actions().get(sentActionCount));
    }

    public void markActionSent() {
        if (phase != CommitPhase.COMMITTED || activePlan == null) {
            throw new IllegalStateException("no committed action is awaiting dispatch");
        }
        if (sentActionCount >= activePlan.actions().size()) {
            throw new IllegalStateException("all committed actions are already sent");
        }
        sentActionCount++;
        if (sentActionCount >= activePlan.actions().size()) {
            phase = CommitPhase.RECONCILING;
        }
    }

    public void abort(CommitAbortReason reason) {
        Objects.requireNonNull(reason, "reason");
        lastAbortReason = reason;
        resetToNormal();
    }

    public void reconciliationComplete() {
        if (phase != CommitPhase.RECONCILING) {
            throw new IllegalStateException("scheduler is not reconciling");
        }
        resetToNormal();
    }

    private void onReservationGranted(ReservationRequest request) {
        if (!request.autoTotemEmergency()) {
            return;
        }
        if (phase == CommitPhase.ARMED) {
            abort(CommitAbortReason.AUTO_TOTEM_EMERGENCY);
            return;
        }
        if (phase == CommitPhase.COMMITTED && hasUnsentActions()) {
            abort(CommitAbortReason.AUTO_TOTEM_EMERGENCY);
        }
    }

    private boolean hasUnsentActions() {
        return activePlan != null && sentActionCount < activePlan.actions().size();
    }

    private void resetToNormal() {
        phase = CommitPhase.NORMAL;
        activePlan = null;
        sentActionCount = 0;
    }
}
