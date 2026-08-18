package dev.adrien.crystaloptimizer.reconcile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Reconciler {
    private final List<PlanAssumption> assumptions;

    public Reconciler(List<PlanAssumption> assumptions) {
        Objects.requireNonNull(assumptions, "assumptions");
        this.assumptions = List.copyOf(assumptions);
    }

    public ReconciliationResult accept(ReconciliationEvent event) {
        Objects.requireNonNull(event, "event");
        List<ReconciliationFailure> failures = new ArrayList<>();
        boolean clearAll = false;
        for (PlanAssumption assumption : assumptions) {
            if (!assumption.relevant(event)) {
                continue;
            }
            var failure = assumption.failure(event);
            if (failure.isPresent()) {
                failures.add(failure.orElseThrow());
                clearAll |= assumption.clearAllPredictionsOnFailure();
            }
        }
        return failures.isEmpty()
            ? ReconciliationResult.valid()
            : ReconciliationResult.invalid(failures, clearAll);
    }

    public List<PlanAssumption> assumptions() {
        return assumptions;
    }
}
