package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import java.util.List;
import java.util.Objects;

public record ReactiveDecision(
    long actionId,
    ApprovalSlot slot,
    ActionApproval approval,
    List<CombatAction> actions,
    long eventObservedNanos,
    long decisionCompleteNanos
) {
    public ReactiveDecision {
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(approval, "approval");
        Objects.requireNonNull(actions, "actions");
        actions = List.copyOf(actions);
        if (actionId < 0L || actions.isEmpty()) {
            throw new IllegalArgumentException("invalid reactive decision");
        }
        if (decisionCompleteNanos < eventObservedNanos) {
            throw new IllegalArgumentException("decision cannot precede event");
        }
    }
}
