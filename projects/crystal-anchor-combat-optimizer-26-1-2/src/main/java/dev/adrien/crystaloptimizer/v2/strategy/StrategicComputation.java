package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;

@FunctionalInterface
public interface StrategicComputation {
    StrategicResult compute(StrategicSnapshot snapshot, OptimizerConfig config);
}
