package dev.pixelied.survival.debug;

import java.util.Objects;

public record DecisionRecord(
    long tick,
    String threatSummary,
    String actionSummary,
    String executionStatus,
    String reason
) {
    public DecisionRecord {
        if (tick < 0) throw new IllegalArgumentException("tick must be non-negative");
        threatSummary = Objects.requireNonNull(threatSummary, "threatSummary");
        actionSummary = Objects.requireNonNull(actionSummary, "actionSummary");
        executionStatus = Objects.requireNonNull(executionStatus, "executionStatus");
        reason = Objects.requireNonNull(reason, "reason");
    }
}
