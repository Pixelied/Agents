package dev.adrien.crystaloptimizer.reconcile;

import java.util.Objects;

public record ReconciliationFailure(
    FailureKind kind,
    PlanAssumption assumption,
    ReconciliationEvent event,
    String detail
) {
    public ReconciliationFailure {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(assumption, "assumption");
        Objects.requireNonNull(event, "event");
        detail = detail == null ? "" : detail;
    }
}
