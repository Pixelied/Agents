package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import java.util.Objects;

public record SelectionContext(
    HurtThresholdEstimate threshold,
    float targetEffectiveHealth,
    OptimizerStrategy strategy
) {
    public SelectionContext {
        Objects.requireNonNull(threshold, "threshold");
        Objects.requireNonNull(strategy, "strategy");
        if (!Float.isFinite(targetEffectiveHealth) || targetEffectiveHealth < 0.0f) {
            throw new IllegalArgumentException("targetEffectiveHealth must be finite and non-negative");
        }
    }
}
