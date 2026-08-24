package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.damage.DamageMismatch;
import dev.adrien.crystaloptimizer.v2.debug.DecisionTrace;
import dev.adrien.crystaloptimizer.v2.debug.DecisionTraceBuffer;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientCombatDiagnostics {
    private static volatile ClientCombatDiagnostics latestInstance;

    private final DecisionTraceBuffer traces = new DecisionTraceBuffer();
    private final AtomicLong staleResultCount = new AtomicLong();
    private volatile ArbitrationResult.Reason lastRejection;
    private volatile long lastEventToDecisionNanos;
    private volatile long lastDecisionToDispatchNanos;
    private volatile long lastActionId = -1L;
    private volatile long strategicDurationNanos;
    private volatile DamageMismatch.Kind lastMismatch = DamageMismatch.Kind.NONE;
    private volatile DamageEstimate targetDamage = DamageEstimate.exact(0.0f, 0L, 0L);
    private volatile float worstSelfDamage;
    private volatile boolean enabled;
    private volatile boolean hudEnabled = true;
    private volatile OptimizerStrategy strategy = OptimizerStrategy.LETHAL_SPEED;
    private volatile ApprovalSlot selectedApproval;
    private volatile OpportunityIntent selectedIntent;
    private volatile String resourceDemand = "{}";
    private volatile double selectedP90Millis;
    private volatile String targetName = "";
    private volatile double placeSpawnP50Millis;
    private volatile double placeSpawnP90Millis;
    private volatile double predictionConfidence;
    private volatile double hurtWindowConfidence;
    private volatile Map<String, Integer> candidateCounts = Map.of();

    public ClientCombatDiagnostics() {
        latestInstance = this;
    }

    public static Optional<ClientCombatDiagnostics> latest() {
        return Optional.ofNullable(latestInstance);
    }

    public void recordConfig(OptimizerConfig config) {
        enabled = config.enabled();
        hudEnabled = config.hud();
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
        selectedIntent = decision.approval().intent();
        targetDamage = decision.approval().targetDamage();
        worstSelfDamage = decision.approval().worstCaseSelfDamage();
        resourceDemand = decision.approval().resources().demand().toString();
        selectedP90Millis = decision.approval().timing().p90Millis();
        predictionConfidence = targetDamage.confidence();

        String snapshotHash = "w" + decision.approval().worldRevision()
            + ":t" + decision.approval().targetRevision()
            + ":i" + decision.approval().inventoryRevision();
        String configHash = "c" + decision.approval().configRevision() + ":" + strategy.name();
        traces.add(new DecisionTrace(
            snapshotHash,
            configHash,
            Map.of(),
            candidateCounts,
            List.of(decision.approval().intent().name()),
            decision.approval().targetId(),
            Long.toString(decision.actionId()),
            decision.slot().name(),
            lastRejection == null ? List.of() : List.of(lastRejection.name()),
            Map.of(
                "placeSpawnP50", placeSpawnP50Millis,
                "placeSpawnP90", placeSpawnP90Millis,
                "selectedP90", selectedP90Millis,
                "hurtWindowConfidence", hurtWindowConfidence,
                "predictionConfidence", predictionConfidence
            ),
            decision.approval().worldRevision(),
            decision.approval().targetRevision(),
            decision.approval().inventoryRevision(),
            decision.approval().configRevision(),
            strategicDurationNanos,
            lastEventToDecisionNanos
        ));
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

    public void recordCandidateCounts(Map<String, Integer> counts) {
        candidateCounts = counts == null ? Map.of() : Map.copyOf(counts);
    }

    public void recordStrategicDuration(long durationNanos) {
        strategicDurationNanos = Math.max(0L, durationNanos);
    }

    public void recordHurtWindowConfidence(double confidence) {
        if (Double.isFinite(confidence) && confidence >= 0.0 && confidence <= 1.0) {
            hurtWindowConfidence = confidence;
        }
    }

    public void recordPredictionConfidence(double confidence) {
        if (Double.isFinite(confidence) && confidence >= 0.0 && confidence <= 1.0) {
            predictionConfidence = confidence;
        }
    }

    public void recordStaleResult() {
        staleResultCount.incrementAndGet();
    }

    public void recordTrace(DecisionTrace trace) {
        traces.add(trace);
    }

    public List<DecisionTrace> traces() { return traces.snapshot(); }
    public Optional<DecisionTrace> latestTrace() {
        List<DecisionTrace> snapshot = traces.snapshot();
        return snapshot.isEmpty() ? Optional.empty() : Optional.of(snapshot.getLast());
    }
    public Optional<ArbitrationResult.Reason> lastRejection() { return Optional.ofNullable(lastRejection); }
    public long lastEventToDecisionNanos() { return lastEventToDecisionNanos; }
    public long lastDecisionToDispatchNanos() { return lastDecisionToDispatchNanos; }
    public long lastActionId() { return lastActionId; }
    public long strategicDurationNanos() { return strategicDurationNanos; }
    public long staleResultCount() { return staleResultCount.get(); }
    public DamageMismatch.Kind lastMismatch() { return lastMismatch; }
    public DamageEstimate targetDamage() { return targetDamage; }
    public float worstSelfDamage() { return worstSelfDamage; }
    public boolean enabled() { return enabled; }
    public boolean hudEnabled() { return hudEnabled; }
    public OptimizerStrategy strategy() { return strategy; }
    public Optional<ApprovalSlot> selectedApproval() { return Optional.ofNullable(selectedApproval); }
    public Optional<OpportunityIntent> selectedIntent() { return Optional.ofNullable(selectedIntent); }
    public String resourceDemand() { return resourceDemand; }
    public double selectedP90Millis() { return selectedP90Millis; }
    public String targetName() { return targetName; }
    public double placeSpawnP50Millis() { return placeSpawnP50Millis; }
    public double placeSpawnP90Millis() { return placeSpawnP90Millis; }
    public double predictionConfidence() { return predictionConfidence; }
    public double hurtWindowConfidence() { return hurtWindowConfidence; }
    public Map<String, Integer> candidateCounts() { return candidateCounts; }
}
