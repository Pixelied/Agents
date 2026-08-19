package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import java.util.Optional;

public final class ClientCombatDiagnostics {
    private volatile ArbitrationResult.Reason lastRejection;
    private volatile long lastEventToDecisionNanos;
    private volatile long lastDecisionToDispatchNanos;
    private volatile long lastActionId = -1L;

    public void recordRejection(ArbitrationResult.Reason reason) {
        lastRejection = reason;
    }

    public void recordDispatch(ReactiveDecision decision, long dispatchCompleteNanos) {
        long decisionComplete = decision.decisionCompleteNanos();
        lastActionId = decision.actionId();
        lastEventToDecisionNanos = Math.max(0L, decisionComplete - decision.eventObservedNanos());
        lastDecisionToDispatchNanos = Math.max(0L, dispatchCompleteNanos - decisionComplete);
    }

    public Optional<ArbitrationResult.Reason> lastRejection() {
        return Optional.ofNullable(lastRejection);
    }

    public long lastEventToDecisionNanos() {
        return lastEventToDecisionNanos;
    }

    public long lastDecisionToDispatchNanos() {
        return lastDecisionToDispatchNanos;
    }

    public long lastActionId() {
        return lastActionId;
    }
}
