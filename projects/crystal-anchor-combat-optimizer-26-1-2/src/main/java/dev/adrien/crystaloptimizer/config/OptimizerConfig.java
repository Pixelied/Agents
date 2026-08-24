package dev.adrien.crystaloptimizer.config;

import dev.adrien.crystaloptimizer.execution.RotationMode;
import java.util.Objects;

public record OptimizerConfig(
    boolean enabled,
    OptimizerStrategy strategy,
    double targetRange,
    float minDamage,
    float maxSelfDamage,
    float facePlaceHealth,
    boolean crystals,
    boolean anchors,
    boolean autoRestock,
    RotationMode rotationMode,
    boolean hud
) {
    public OptimizerConfig {
        Objects.requireNonNull(strategy, "strategy");
        Objects.requireNonNull(rotationMode, "rotationMode");
    }

    public static OptimizerConfig defaults() {
        return new OptimizerConfig(
            false,
            OptimizerStrategy.LETHAL_SPEED,
            12.0,
            4.0f,
            12.0f,
            8.0f,
            true,
            true,
            true,
            RotationMode.ADAPTIVE,
            true
        );
    }

    public OptimizerConfig validated() {
        check("targetRange", targetRange, 1.0, 16.0);
        check("minDamage", minDamage, 0.0, 40.0);
        check("maxSelfDamage", maxSelfDamage, 0.0, 40.0);
        check("facePlaceHealth", facePlaceHealth, 0.0, 40.0);
        return this;
    }

    public OptimizerConfig withEnabled(boolean next) {
        return new OptimizerConfig(
            next,
            strategy,
            targetRange,
            minDamage,
            maxSelfDamage,
            facePlaceHealth,
            crystals,
            anchors,
            autoRestock,
            rotationMode,
            hud
        );
    }

    private static void check(String name, double value, double min, double max) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalArgumentException(name + " outside [" + min + "," + max + "]");
        }
    }
}
