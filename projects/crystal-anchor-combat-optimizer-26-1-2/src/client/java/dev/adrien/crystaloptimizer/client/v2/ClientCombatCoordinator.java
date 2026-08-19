package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveCombatEngine;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import java.util.Objects;
import java.util.Optional;

public final class ClientCombatCoordinator {
    private final OptimizerConfigService configService;
    private final CombatBlackboard blackboard;
    private final ReactiveCombatEngine reactive;
    private final ActionArbiter arbiter;
    private final LiveCombatView liveView;
    private final PendingItemLedger pendingItems;
    private final ReactiveBurstSink burstDispatcher;
    private final ClientCombatDiagnostics diagnostics;
    private final Runnable strategicTick;

    public ClientCombatCoordinator(
        OptimizerConfigService configService,
        CombatBlackboard blackboard,
        ReactiveCombatEngine reactive,
        ActionArbiter arbiter,
        LiveCombatView liveView,
        PendingItemLedger pendingItems,
        ReactiveBurstSink burstDispatcher,
        ClientCombatDiagnostics diagnostics,
        Runnable strategicTick
    ) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.blackboard = Objects.requireNonNull(blackboard, "blackboard");
        this.reactive = Objects.requireNonNull(reactive, "reactive");
        this.arbiter = Objects.requireNonNull(arbiter, "arbiter");
        this.liveView = Objects.requireNonNull(liveView, "liveView");
        this.pendingItems = Objects.requireNonNull(pendingItems, "pendingItems");
        this.burstDispatcher = Objects.requireNonNull(burstDispatcher, "burstDispatcher");
        this.diagnostics = Objects.requireNonNull(diagnostics, "diagnostics");
        this.strategicTick = Objects.requireNonNull(strategicTick, "strategicTick");
    }

    public void tick() {
        if (!configService.current().enabled()) {
            return;
        }
        strategicTick.run();
    }

    public void onEvent(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        OptimizerConfig config = configService.current();
        if (!config.enabled()) {
            return;
        }

        CombatBlackboardSnapshot snapshot = blackboard.snapshot();
        long decisionCompleteNanos = Math.max(event.timestampNanos(), System.nanoTime());
        Optional<ReactiveDecision> decision = reactive.decide(
            event,
            snapshot,
            decisionCompleteNanos
        );
        if (decision.isEmpty()) {
            return;
        }

        ReactiveDecision selected = decision.orElseThrow();
        ArbitrationResult allowed = arbiter.evaluate(
            selected.approval(),
            selected.actions(),
            liveView,
            pendingItems,
            config,
            System.nanoTime()
        );
        if (!allowed.allowed()) {
            diagnostics.recordRejection(allowed.reason());
            return;
        }

        burstDispatcher.dispatch(selected, config);
        diagnostics.recordDispatch(selected, System.nanoTime());
    }

    public ClientCombatDiagnostics diagnostics() {
        return diagnostics;
    }
}
