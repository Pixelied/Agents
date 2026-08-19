package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.damage.DamageMismatch;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import java.util.Optional;

public final class ClientCombatDiagnostics {
    private volatile ArbitrationResult.Reason lastRejection;
    private volatile long lastEventToDecisionNanos;
    private volatile long lastDecisionToDispatchNanos;
    private volatile long lastActionId = -1L;
    private volatile DamageMismatch.Kind lastMismatch = DamageMismatch.Kind.NONE;
    private volatile DamageEstimate targetDamage = DamageEstimate.exact(0.0f, 0L, 0L);
    private volatile float worstSelfDamage;
    private volatile boolean enabled;
    private volatile OptimizerStrategy strategy = OptimizerStrategy.LETHAL_SPEED;
    private volatile ApprovalSlot selectedApproval;
    private volatile String targetName = "";
    private volatile double placeSpawnP50Millis;
    private volatile double placeSpawnP90Millis;

    public void recordConfig(OptimizerConfig config) {
        enabled = config.enabled();
        strategy = config.strategy();
    }

    public void recordRejection(ArbitrationResult.Reason reason) {
        lastRejection = reason;
    }

    public void recordDispatch(ReactiveDecision decision, long dispatchCompleteNanos) {
        long decisionComplete = decision.decisionCompleteNanos();
        lastActionId = decision.actionId();
        lastEventToDecisionNanos = Math.max(0L, decisionComplete - decision.eventObservedNanos());
        lastDecisionToDispatchNanos = Math.max(0L, dispatchCompleteNanos - decisionComplete);
        selectedApproval = decision.slot();
        targetDamage = decision.approval().targetDamage();
        worstSelfDamage = decision.approval().worstCaseSelfDamage();
    }

    public void recordMismatch(DamageMismatch mismatch) {
        lastMismatch = mismatch == null ? DamageMismatch.Kind.NONE : mismatch.kind();
    }

    public void recordTarget(String name) {
        targetName = name == null ? "" : name;
    }

    public void recordPlaceSpawnTiming(double p50Millis, double p90Millis) {
        if (Double.isFinite(p50Millis) && Double.isFinite(p90Millis)
            && p50Millis >= 0.0 && p90Millis >= p50Millis) {
            placeSpawnP50Millis = p50Millis;
            placeSpawnP90Millis = p90Millis;
        }
    }

    public Optional<ArbitrationResult.Reason> lastRejection() {
        return Optional.ofNullable(lastRejection);
    }

    public long lastEventToDecisionNanos() { return lastEventToDecisionNanos; }
    public long lastDecisionToDispatchNanos() { return lastDecisionToDispatchNanos; }
    public long lastActionId() { return lastActionId; }
    public DamageMismatch.Kind lastMismatch() { return lastMismatch; }
    public DamageEstimate targetDamage() { return targetDamage; }
    public float worstSelfDamage() { return worstSelfDamage; }
    public boolean enabled() { return enabled; }
    public OptimizerStrategy strategy() { return strategy; }
    public Optional<ApprovalSlot> selectedApproval() { return Optional.ofNullable(selectedApproval); }
    public String targetName() { return targetName; }
    public double placeSpawnP50Millis() { return placeSpawnP50Millis; }
    public double placeSpawnP90Millis() { return placeSpawnP90Millis; }
}
