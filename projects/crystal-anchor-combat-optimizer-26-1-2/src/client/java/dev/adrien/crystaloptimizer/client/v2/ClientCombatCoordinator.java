package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
import dev.adrien.crystaloptimizer.client.execution.HotbarRestocker;
import dev.adrien.crystaloptimizer.client.execution.RotationController;
import dev.adrien.crystaloptimizer.client.execution.VanillaInteractionDispatcher;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveCombatEngine;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.FastOpportunitySelector;
import dev.adrien.crystaloptimizer.v2.strategy.HurtWindowTracker;
import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public final class ClientCombatCoordinator {
    private static final float MAX_VISIBLE_ROTATION_DEGREES_PER_UPDATE = 35.0f;

    private final OptimizerConfigService configService;
    private final CombatBlackboard blackboard;
    private final ReactiveCombatEngine reactive;
    private final ActionArbiter arbiter;
    private final LiveCombatView liveView;
    private final PendingItemLedger pendingItems;
    private final ReactiveBurstSink burstDispatcher;
    private final ClientCombatDiagnostics diagnostics;
    private final Runnable strategicTick;
    private final Consumer<CombatEvent> eventObserver;
    private PendingContinuation continuation;

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
        this(
            configService,
            blackboard,
            reactive,
            arbiter,
            liveView,
            pendingItems,
            burstDispatcher,
            diagnostics,
            strategicTick,
            ignored -> {}
        );
    }

    private ClientCombatCoordinator(
        OptimizerConfigService configService,
        CombatBlackboard blackboard,
        ReactiveCombatEngine reactive,
        ActionArbiter arbiter,
        LiveCombatView liveView,
        PendingItemLedger pendingItems,
        ReactiveBurstSink burstDispatcher,
        ClientCombatDiagnostics diagnostics,
        Runnable strategicTick,
        Consumer<CombatEvent> eventObserver
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
        this.eventObserver = Objects.requireNonNull(eventObserver, "eventObserver");
    }

    public static ClientCombatCoordinator create(
        Minecraft minecraft,
        OptimizerConfigService configService
    ) {
        Objects.requireNonNull(minecraft, "minecraft");
        Objects.requireNonNull(configService, "configService");

        CombatBlackboard blackboard = new CombatBlackboard();
        ClientRevisionTracker revisions = new ClientRevisionTracker();
        TimingEngine timingEngine = ClientTimingObserver.instance().timingEngine();
        ClientDamageMapBuilder damageMaps = new ClientDamageMapBuilder(minecraft, timingEngine);
        ClientStrategicScanner scanner = new ClientStrategicScanner(
            damageMaps,
            blackboard,
            new FastOpportunitySelector(),
            new HurtWindowTracker()
        );
        PendingItemLedger pendingItems = new PendingItemLedger();
        InventoryCoordinator inventory = new InventoryCoordinator();
        HotbarRestocker restocker = new HotbarRestocker(minecraft, inventory);
        ClientLiveCombatView liveView = new ClientLiveCombatView(
            minecraft,
            revisions::worldRevision,
            revisions::targetRevision,
            revisions::inventoryRevision,
            configService::revision
        );
        VanillaInteractionDispatcher vanilla = new VanillaInteractionDispatcher(
            minecraft,
            new RotationController(minecraft, MAX_VISIBLE_ROTATION_DEGREES_PER_UPDATE),
            configService.current().rotationMode()
        );
        ReactiveBurstDispatcher burstDispatcher = new ReactiveBurstDispatcher(
            vanilla,
            liveView,
            pendingItems
        );
        ClientCombatDiagnostics diagnostics = new ClientCombatDiagnostics();
        TargetManager targets = new TargetManager();

        Runnable strategicTick = () -> {
            OptimizerConfig config = configService.current();
            LocalPlayer self = minecraft.player;
            ClientLevel level = minecraft.level;
            if (self == null || level == null) {
                targets.clear();
                diagnostics.recordTarget("");
                return;
            }
            if (config.autoRestock()
                && pendingItems.reservationCount() == 0
                && restocker.restockOne(self)) {
                revisions.markInventoryMutation();
                return;
            }

            long worldRevision = revisions.worldRevision();
            Optional<AbstractClientPlayer> selected = targets.select(
                self,
                level,
                config,
                candidate -> immediateLethalMillis(damageMaps.update(
                    candidate,
                    worldRevision,
                    revisions.targetRevision(candidate.getUUID()),
                    config
                ))
            );
            if (selected.isEmpty()) {
                diagnostics.recordTarget("");
                return;
            }

            AbstractClientPlayer target = selected.orElseThrow();
            long nowNanos = System.nanoTime();
            scanner.scan(
                target,
                worldRevision,
                revisions.targetRevision(target.getUUID()),
                revisions.inventoryRevision(),
                configService.revision(),
                config,
                nowNanos
            );
            diagnostics.recordTarget(target.getName().getString());

            TimingDistribution placeSpawn = timingEngine.distribution(
                TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
                nowNanos
            );
            if (placeSpawn.sampleCount() > 0) {
                diagnostics.recordPlaceSpawnTiming(
                    placeSpawn.p50Millis(),
                    placeSpawn.p90Millis()
                );
            }
        };

        return new ClientCombatCoordinator(
            configService,
            blackboard,
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            liveView,
            pendingItems,
            burstDispatcher,
            diagnostics,
            strategicTick,
            revisions::observe
        );
    }

    public void tick() {
        pendingItems.reconcile(liveView::observedCount, System.nanoTime());
        OptimizerConfig config = configService.current();
        diagnostics.recordConfig(config);
        if (!config.enabled()) {
            continuation = null;
            return;
        }
        if (resumeContinuation(config)) {
            return;
        }
        strategicTick.run();
        reactive.decideProactive(blackboard.snapshot(), System.nanoTime())
            .ifPresent(decision -> dispatchDecision(decision, 0, config));
    }

    public void onEvent(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        eventObserver.accept(event);
        pendingItems.reconcile(
            liveView::observedCount,
            Math.max(event.timestampNanos(), System.nanoTime())
        );
        OptimizerConfig config = configService.current();
        diagnostics.recordConfig(config);
        if (!config.enabled()) {
            continuation = null;
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
        if (continuation != null
            && !preempts(selected.slot(), continuation.decision().slot())) {
            return;
        }
        continuation = null;
        dispatchDecision(selected, 0, config);
    }

    public ClientCombatDiagnostics diagnostics() {
        return diagnostics;
    }

    private boolean resumeContinuation(OptimizerConfig config) {
        PendingContinuation pending = continuation;
        if (pending == null) {
            return false;
        }
        if (pending.waitTicks() > 0) {
            continuation = pending.withWaitTicks(pending.waitTicks() - 1);
            return true;
        }
        if (pending.nextActionIndex() >= pending.decision().actions().size()) {
            continuation = null;
            return true;
        }

        dispatchDecision(pending.decision(), pending.nextActionIndex(), config);
        return true;
    }

    private void dispatchDecision(
        ReactiveDecision decision,
        int startIndex,
        OptimizerConfig config
    ) {
        long nowNanos = System.nanoTime();
        ArbitrationResult allowed;
        if (startIndex == 0) {
            allowed = arbiter.evaluate(
                decision.approval(),
                decision.actions(),
                liveView,
                pendingItems,
                config,
                nowNanos
            );
        } else if (decision.approval().resources().isEmpty()) {
            allowed = arbiter.evaluateFrom(
                decision.approval(),
                decision.actions(),
                startIndex,
                liveView,
                pendingItems,
                config,
                nowNanos
            );
        } else {
            allowed = arbiter.evaluateContinuation(
                decision.approval(),
                decision.actions(),
                startIndex,
                ReactiveBurstDispatcher.groupReservationId(decision.actionId()),
                liveView,
                pendingItems,
                config,
                nowNanos
            );
        }
        if (!allowed.allowed()) {
            diagnostics.recordRejection(allowed.reason());
            continuation = null;
            return;
        }

        BurstReceipt receipt = startIndex == 0
            ? burstDispatcher.dispatch(decision, config)
            : burstDispatcher.dispatchFrom(decision, config, startIndex);
        diagnostics.recordDispatch(decision, System.nanoTime());
        updateContinuation(decision, startIndex, receipt);
    }

    private void updateContinuation(
        ReactiveDecision decision,
        int startIndex,
        BurstReceipt receipt
    ) {
        List<DispatchReceipt> receipts = receipt.receipts();
        if (receipts.isEmpty()) {
            continuation = null;
            return;
        }

        int sentCount = 0;
        while (sentCount < receipts.size()
            && receipts.get(sentCount).status() == DispatchReceipt.Status.SENT) {
            sentCount++;
        }
        int nextActionIndex = startIndex + sentCount;

        if (sentCount == receipts.size()) {
            continuation = nextActionIndex < decision.actions().size()
                ? new PendingContinuation(decision, nextActionIndex, 0)
                : null;
            return;
        }

        DispatchReceipt terminal = receipts.get(sentCount);
        continuation = switch (terminal.status()) {
            case DEFERRED -> new PendingContinuation(decision, nextActionIndex, 0);
            case WAITING -> new PendingContinuation(
                decision,
                Math.min(decision.actions().size(), nextActionIndex + 1),
                terminal.waitTicks()
            );
            case FAILED -> null;
            case SENT -> throw new IllegalStateException("sent receipt escaped sent prefix");
        };
    }

    private static boolean preempts(ApprovalSlot challenger, ApprovalSlot pending) {
        return challenger.ordinal() < pending.ordinal();
    }

    private static double immediateLethalMillis(DamageMap map) {
        return map.opportunities().values().stream()
            .filter(DamageOpportunity::lethal)
            .filter(opportunity -> opportunity.targetDamage().confidence() >= 0.80)
            .mapToDouble(opportunity -> opportunity.timing().p90Millis())
            .filter(Double::isFinite)
            .min()
            .orElse(Double.POSITIVE_INFINITY);
    }

    private record PendingContinuation(
        ReactiveDecision decision,
        int nextActionIndex,
        int waitTicks
    ) {
        private PendingContinuation {
            Objects.requireNonNull(decision, "decision");
            if (nextActionIndex < 0 || nextActionIndex > decision.actions().size()) {
                throw new IllegalArgumentException("nextActionIndex outside reactive decision");
            }
            if (waitTicks < 0) {
                throw new IllegalArgumentException("waitTicks must be non-negative");
            }
        }

        PendingContinuation withWaitTicks(int nextWaitTicks) {
            return new PendingContinuation(decision, nextActionIndex, nextWaitTicks);
        }
    }
}
