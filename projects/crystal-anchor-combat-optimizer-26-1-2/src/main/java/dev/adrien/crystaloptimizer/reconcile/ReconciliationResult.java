package dev.adrien.crystaloptimizer.reconcile;

import java.util.List;

public record ReconciliationResult(
    boolean valid,
    List<ReconciliationFailure> failures,
    boolean clearAllPredictions
) {
    public ReconciliationResult {
        failures = List.copyOf(failures);
        if (valid && !failures.isEmpty()) {
            throw new IllegalArgumentException("valid reconciliation cannot contain failures");
        }
    }

    public static ReconciliationResult accepted() {
        return new ReconciliationResult(true, List.of(), false);
    }

    public static ReconciliationResult invalid(List<ReconciliationFailure> failures, boolean clearAllPredictions) {
        if (failures.isEmpty()) {
            throw new IllegalArgumentException("invalid reconciliation needs at least one failure");
        }
        return new ReconciliationResult(false, failures, clearAllPredictions);
    }
}
