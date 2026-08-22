package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.reconcile.PlanAssumption;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit counterfactual geometry used only for strategic evaluation.
 *
 * <p>A hypothesis is never authoritative. Every geometry delta must be paired
 * with observable assumptions and a hard feedback boundary that must be crossed
 * before a terminal action may rely on that delta.</p>
 */
public record WorldHypothesis(
    BlockDeltaOverlay geometry,
    Set<PlanAssumption> assumptions,
    TimingTransition feedbackBoundary,
    double confidence
) {
    public WorldHypothesis {
        Objects.requireNonNull(geometry, "geometry");
        Objects.requireNonNull(assumptions, "assumptions");
        Objects.requireNonNull(feedbackBoundary, "feedbackBoundary");
        assumptions = Set.copyOf(assumptions);
        if (assumptions.isEmpty()) {
            throw new IllegalArgumentException("counterfactual geometry requires at least one observable assumption");
        }
        if (!feedbackBoundary.hardFeedback()) {
            throw new IllegalArgumentException("counterfactual geometry requires a hard feedback boundary");
        }
        if (!Double.isFinite(confidence) || confidence <= 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be finite and in (0, 1]");
        }
    }
}
