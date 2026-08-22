package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import java.util.Objects;

/** Worker-produced bounded sequence plus the terminal combat opportunity it realizes. */
public record PlannedOpportunity(
    FixedActionSequence sequence,
    DamageOpportunity terminalOpportunity,
    double expectedCompletionMillis,
    int hardFeedbackBoundaries,
    boolean certifiedLethal
) {
    public PlannedOpportunity {
        Objects.requireNonNull(sequence, "sequence");
        Objects.requireNonNull(terminalOpportunity, "terminalOpportunity");
        boolean validTime = Double.isFinite(expectedCompletionMillis)
            ? expectedCompletionMillis >= 0.0
            : Double.isInfinite(expectedCompletionMillis) && expectedCompletionMillis > 0.0;
        if (!validTime) {
            throw new IllegalArgumentException("expectedCompletionMillis must be non-negative or +infinity");
        }
        if (hardFeedbackBoundaries < 0) {
            throw new IllegalArgumentException("hardFeedbackBoundaries must be non-negative");
        }
    }
}
