package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;

@FunctionalInterface
public interface ReactiveBurstSink {
    BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config);
}
