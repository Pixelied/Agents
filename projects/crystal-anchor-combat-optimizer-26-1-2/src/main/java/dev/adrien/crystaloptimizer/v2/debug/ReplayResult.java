package dev.adrien.crystaloptimizer.v2.debug;

import java.util.Objects;
import java.util.UUID;

/** Stable observable output of one replay run. */
public record ReplayResult(
    UUID targetId,
    String chosenDecisionKey,
    String decisionClass,
    long finalWorldRevision
) {
    public ReplayResult {
        Objects.requireNonNull(targetId, "targetId");
        if (chosenDecisionKey == null || chosenDecisionKey.isBlank()) {
            throw new IllegalArgumentException("chosenDecisionKey must not be blank");
        }
        if (decisionClass == null || decisionClass.isBlank()) {
            throw new IllegalArgumentException("decisionClass must not be blank");
        }
        if (finalWorldRevision < 0L) {
            throw new IllegalArgumentException("finalWorldRevision must be non-negative");
        }
    }
}
