package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.v2.diagnostics.TimeToDamageTrace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DamageCalibration {
    private static final int MAX_PENDING = 256;
    private static final float EPSILON = 1.0e-3f;

    private final LinkedHashMap<Long, TimeToDamageTrace> pending = new LinkedHashMap<>();

    public synchronized void observePrediction(long actionId, TimeToDamageTrace trace) {
        Objects.requireNonNull(trace, "trace");
        if (actionId < 0L || trace.actionId() != actionId) {
            throw new IllegalArgumentException("prediction action ID mismatch");
        }
        pending.put(actionId, trace);
        while (pending.size() > MAX_PENDING) {
            Long oldest = pending.keySet().iterator().next();
            pending.remove(oldest);
        }
    }

    public synchronized Optional<DamageMismatch> observeResult(
        long actionId,
        ObservedDamageResult result
    ) {
        Objects.requireNonNull(result, "result");
        TimeToDamageTrace trace = pending.remove(actionId);
        if (trace == null) {
            return Optional.empty();
        }

        DamageEstimate estimate = trace.targetDamage();
        if (result.combatRevision() != estimate.combatRevision()) {
            return Optional.of(new DamageMismatch(
                actionId,
                DamageMismatch.Kind.INTERFERENCE,
                intervalError(result.healthLoss(), estimate)
            ));
        }

        if (inside(result.healthLoss(), estimate)) {
            return Optional.empty();
        }

        return Optional.of(new DamageMismatch(
            actionId,
            classify(estimate),
            intervalError(result.healthLoss(), estimate)
        ));
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private static boolean inside(float observed, DamageEstimate estimate) {
        return observed + EPSILON >= estimate.lowerBound()
            && observed - EPSILON <= estimate.upperBound();
    }

    private static float intervalError(float observed, DamageEstimate estimate) {
        if (observed < estimate.lowerBound()) {
            return estimate.lowerBound() - observed;
        }
        if (observed > estimate.upperBound()) {
            return observed - estimate.upperBound();
        }
        return 0.0f;
    }

    private static DamageMismatch.Kind classify(DamageEstimate estimate) {
        Map<DamageUncertainty, DamageMismatch.Kind> taxonomy = Map.of(
            DamageUncertainty.HURT_THRESHOLD_UNKNOWN, DamageMismatch.Kind.HURT_THRESHOLD_UNKNOWN,
            DamageUncertainty.ABSORPTION_UNKNOWN, DamageMismatch.Kind.ABSORPTION_UNCERTAINTY,
            DamageUncertainty.PREDICTED_POSITION, DamageMismatch.Kind.TARGET_MOVED,
            DamageUncertainty.TERRAIN_UNOBSERVED, DamageMismatch.Kind.EXPOSURE_MISMATCH,
            DamageUncertainty.ARMOR_STATE_STALE, DamageMismatch.Kind.ARMOR_STATE_CHANGED,
            DamageUncertainty.EFFECT_STATE_STALE, DamageMismatch.Kind.EFFECT_STATE_CHANGED,
            DamageUncertainty.PENDING_SERVER_ACCEPTANCE, DamageMismatch.Kind.ACTION_NOT_SERVER_ACCEPTED
        );
        for (DamageUncertainty uncertainty : DamageUncertainty.values()) {
            if (estimate.uncertainties().contains(uncertainty)) {
                DamageMismatch.Kind kind = taxonomy.get(uncertainty);
                if (kind != null) {
                    return kind;
                }
            }
        }
        return DamageMismatch.Kind.UNKNOWN;
    }
}
