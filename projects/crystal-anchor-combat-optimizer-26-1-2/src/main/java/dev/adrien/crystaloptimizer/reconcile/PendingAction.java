package dev.adrien.crystaloptimizer.reconcile;

import java.util.List;

public record PendingAction(
    String id,
    int interactionSequence,
    long sentNanos,
    List<PlanAssumption> expectedObservations,
    Status status
) {
    public PendingAction {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (interactionSequence < -1) {
            throw new IllegalArgumentException("interactionSequence must be -1 or non-negative");
        }
        if (sentNanos < 0L) {
            throw new IllegalArgumentException("sentNanos must be non-negative");
        }
        expectedObservations = List.copyOf(expectedObservations);
        if (status == null) {
            throw new NullPointerException("status");
        }
    }

    public PendingAction(
        String id,
        int interactionSequence,
        long sentNanos,
        List<PlanAssumption> expectedObservations
    ) {
        this(id, interactionSequence, sentNanos, expectedObservations, Status.WAITING);
    }

    public PendingAction withStatus(Status nextStatus) {
        return new PendingAction(id, interactionSequence, sentNanos, expectedObservations, nextStatus);
    }

    public enum Status {
        WAITING,
        CONFIRMED,
        FAILED
    }
}
