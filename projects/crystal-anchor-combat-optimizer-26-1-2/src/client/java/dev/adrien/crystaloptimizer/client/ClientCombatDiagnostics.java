package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.execution.CommitPhase;
import java.util.Objects;

public record ClientCombatDiagnostics(
    boolean enabled,
    String targetName,
    CommitPhase phase,
    int actionCount,
    boolean lethal,
    double robustness,
    String reconciliation,
    String abortReason,
    double roundTripMillis,
    double jitterMillis
) {
    public ClientCombatDiagnostics {
        targetName = Objects.requireNonNullElse(targetName, "");
        phase = Objects.requireNonNull(phase, "phase");
        if (actionCount < 0) {
            throw new IllegalArgumentException("actionCount must be non-negative");
        }
        robustness = clamp01(robustness);
        reconciliation = Objects.requireNonNullElse(reconciliation, "");
        abortReason = Objects.requireNonNullElse(abortReason, "");
        roundTripMillis = Math.max(0.0, roundTripMillis);
        jitterMillis = Math.max(0.0, jitterMillis);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
