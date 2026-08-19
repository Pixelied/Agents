package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import java.util.Objects;

@FunctionalInterface
public interface ReactiveBurstSink {
    BurstReceipt dispatch(ReactiveDecision decision, OptimizerConfig config);

    default BurstReceipt dispatchFrom(
        ReactiveDecision decision,
        OptimizerConfig config,
        int startIndex
    ) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(config, "config");
        if (startIndex < 0 || startIndex > decision.actions().size()) {
            throw new IllegalArgumentException("startIndex outside reactive decision");
        }
        if (startIndex == 0) {
            return dispatch(decision, config);
        }
        if (startIndex == decision.actions().size()) {
            return BurstReceipt.empty();
        }
        return dispatch(new ReactiveDecision(
            decision.actionId(),
            decision.slot(),
            decision.approval(),
            decision.actions().subList(startIndex, decision.actions().size()),
            decision.eventObservedNanos(),
            decision.decisionCompleteNanos()
        ), config);
    }
}
