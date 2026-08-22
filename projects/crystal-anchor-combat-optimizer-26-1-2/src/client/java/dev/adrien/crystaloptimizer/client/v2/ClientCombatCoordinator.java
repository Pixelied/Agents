package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
import dev.adrien.crystaloptimizer.client.execution.HotbarRestocker;
import dev.adrien.crystaloptimizer.client.execution.RotationController;
import dev.adrien.crystaloptimizer.client.execution.VanillaInteractionDispatcher;
import dev.adrien.crystaloptimizer.client.intel.RemoteDamageWindowObserver;
import dev.adrien.crystaloptimizer.client.intel.TargetMotionTracker;
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
import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.strategy.FastOpportunitySelector;
import dev.adrien.crystaloptimizer.v2.strategy.HurtWindowTracker;
import dev.adrien.crystaloptimizer.v2.strategy.StrategicCombatPlanner;
import dev.adrien.crystaloptimizer.v2.strategy.TargetPreScore;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;

public final class ClientCombatCoordinator {
    private static final float MAX_VISIBLE_ROTATION_DEGREES_PER_UPDATE = 35.0f;
    private static final int STRATEGIC_REFRESH_TICKS = 2;

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
        HurtWindowTracker hurtWindows = new HurtWindowTracker();
        RemoteDamageWindowObserver.instance().bind(hurtWindows);
        ClientStrategicScanner scanner = new ClientStrategicScanner(
            blackboard,
            new FastOpportunitySelector(),
            hurtWindows
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
        TargetProtectionPolicyConfig protectionConfig = TargetProtectionPolicyConfig.defaults();
        ClientTargetProtectionResolver protectionResolver = new ClientTargetProtectionResolver(minecraft);
        ClientStrategicSnapshotCapture capture = new ClientStrategicSnapshotCapture(
            minecraft,
            revisions,
            timingEngine,
            protectionResolver,
            protectionConfig
        );
        ClientStrategicPlannerService plannerService = new ClientStrategicPlannerService(
            new StrategicCombatPlanner()
        );
        long[] lastSubmitted = {-1L, -1L, -1L, Long.MIN_VALUE, Long.MIN_VALUE};

        Runnable strategicTick = () -> {
            OptimizerConfig config = configService.current();
            LocalPlayer self = minecraft.player;
            ClientLevel level = minecraft.level;
            if (self == null || level == null) {
                TargetMotionTracker.instance().clear();
                targets.clear();
                diagnostics.recordTarget("");
                plannerService.pollLatest();
                return;
            }

            if (config.autoRestock()
                && pendingItems.reservationCount() == 0
                && restocker.restockOne(self)) {
                revisions.markInventoryMutation();
                return;
            }

            Optional<StrategicResult> ready = plannerService.pollLatest();
            if (ready.isPresent()) {
                StrategicResult result = ready.orElseThrow();
                boolean current = result.worldRevision() == revisions.worldRevision()
                    && result.inventoryRevision() == revisions.inventoryRevision()
                    && result.configRevision() == configService.revision()
                    && result.damageMap().targetRevision() == revisions.targetRevision(result.targetId());
                if (current) {
                    AbstractClientPlayer target = level.players().stream()
                        .filter(player -> player.getUUID().equals(result.targetId()))
                        .findFirst()
                        .orElse(null);
                    if (target != null && !target.isRemoved() && !target.isDeadOrDying()) {
                        targets.markSelected(result.targetId());
                        scanner.publish(
                            target,
                            result.damageMap(),
                            result.inventoryRevision(),
                            result.configRevision(),
                            config,
                            System.nanoTime()
                        );
                        diagnostics.recordTarget(target.getName().getString());
                    }
                }
            }

            List<AbstractClientPlayer> observedPlayers = List.copyOf(level.players());
            Set<UUID> protectedIds = protectionResolver.resolve(observedPlayers, protectionConfig);
            List<TargetPreScore> preScores = targets.preScores(self, level, config, protectedIds);
            if (preScores.isEmpty()) {
                targets.clear();
                diagnostics.recordTarget("");
                return;
            }
            Map<UUID, AbstractClientPlayer> playersById = new LinkedHashMap<>();
            for (AbstractClientPlayer player : observedPlayers) {
                playersById.put(player.getUUID(), player);
            }
            List<AbstractClientPlayer> candidates = preScores.stream()
                .map(score -> playersById.get(score.targetId()))
                .filter(Objects::nonNull)
                .toList();

            long worldRevision = revisions.worldRevision();
            long inventoryRevision = revisions.inventoryRevision();
            long configRevision = configService.revision();
            long targetFingerprint = targetRevisionFingerprint(candidates, revisions);
            long tick = self.tickCount;
            boolean revisionsChanged = worldRevision != lastSubmitted[0]
                || inventoryRevision != lastSubmitted[1]
                || configRevision != lastSubmitted[2]
                || targetFingerprint != lastSubmitted[3];
            boolean cadenceDue = lastSubmitted[4] == Long.MIN_VALUE
                || tick - lastSubmitted[4] >= STRATEGIC_REFRESH_TICKS;
            if (revisionsChanged || cadenceDue) {
                capture.capture(candidates, configRevision).ifPresent(snapshot -> {
                    plannerService.submit(snapshot, config);
                    lastSubmitted[0] = snapshot.worldRevision();
                    lastSubmitted[1] = snapshot.inventoryRevision();
                    lastSubmitted[2] = snapshot.configRevision();
                    lastSubmitted[3] = targetFingerprint;
                    lastSubmitted[4] = tick;
                });
            }

            long nowNanos = System.nanoTime();
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
        observeSentExplosionCandidates(decision, startIndex, receipt);
        diagnostics.recordDispatch(decision, System.nanoTime());
        updateContinuation(decision, startIndex, receipt);
    }

    private static void observeSentExplosionCandidates(
        ReactiveDecision decision,
        int startIndex,
        BurstReceipt receipt
    ) {
        int count = Math.min(
            receipt.receipts().size(),
            Math.max(0, decision.actions().size() - startIndex)
        );
        long nowNanos = System.nanoTime();
        for (int offset = 0; offset < count; offset++) {
            if (receipt.receipts().get(offset).status() != DispatchReceipt.Status.SENT) {
                continue;
            }
            var action = decision.actions().get(startIndex + offset);
            if (action instanceof AttackKnownCrystal || action instanceof DetonateAnchor) {
                RemoteDamageWindowObserver.instance().onExplosionCandidate(
                    decision.approval().targetId(),
                    decision.approval().targetDamage().postMitigationExpected(),
                    nowNanos
                );
            }
        }
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

    private static long targetRevisionFingerprint(
        List<AbstractClientPlayer> targets,
        ClientRevisionTracker revisions
    ) {
        long hash = 1125899906842597L;
        for (AbstractClientPlayer target : targets) {
            hash = 31L * hash + target.getUUID().hashCode();
            hash = 31L * hash + revisions.targetRevision(target.getUUID());
        }
        return hash;
    }

    private static boolean preempts(ApprovalSlot challenger, ApprovalSlot pending) {
        return challenger.ordinal() < pending.ordinal();
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
