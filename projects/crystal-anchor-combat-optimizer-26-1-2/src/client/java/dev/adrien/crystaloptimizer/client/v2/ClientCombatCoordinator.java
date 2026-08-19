package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.execution.RotationController;
import dev.adrien.crystaloptimizer.client.execution.VanillaInteractionDispatcher;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.execution.CommitScheduler;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.ArbitrationResult;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveCombatEngine;
import dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.FastOpportunitySelector;
import dev.adrien.crystaloptimizer.v2.strategy.HurtWindowTracker;
import dev.adrien.crystaloptimizer.v2.timing.TimingDistribution;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
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
        ClientLiveCombatView liveView = new ClientLiveCombatView(
            minecraft,
            revisions::worldRevision,
            revisions::targetRevision,
            revisions::inventoryRevision,
            configService::revision
        );
        CommitScheduler compatibilityScheduler = new CommitScheduler(new InventoryCoordinator());
        VanillaInteractionDispatcher vanilla = new VanillaInteractionDispatcher(
            minecraft,
            new RotationController(minecraft, MAX_VISIBLE_ROTATION_DEGREES_PER_UPDATE),
            compatibilityScheduler,
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
        OptimizerConfig config = configService.current();
        diagnostics.recordConfig(config);
        if (!config.enabled()) {
            return;
        }
        strategicTick.run();
    }

    public void onEvent(CombatEvent event) {
        Objects.requireNonNull(event, "event");
        eventObserver.accept(event);
        OptimizerConfig config = configService.current();
        diagnostics.recordConfig(config);
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

    private static double immediateLethalMillis(DamageMap map) {
        return map.opportunities().values().stream()
            .filter(DamageOpportunity::lethal)
            .filter(opportunity -> opportunity.targetDamage().confidence() >= 0.80)
            .mapToDouble(opportunity -> opportunity.timing().p90Millis())
            .filter(Double::isFinite)
            .min()
            .orElse(Double.POSITIVE_INFINITY);
    }
}
