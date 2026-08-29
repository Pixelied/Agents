package dev.pixelied.survival.core;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.ServerDamageStateReconciler;
import dev.pixelied.survival.damage.ServerHurtStateTracker;
import dev.pixelied.survival.execution.DeathProtectionActionExecutor;
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.DeathProtectionRestorationController;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.ExecutionContext;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.execution.EquipmentAuthorityProjection;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import dev.pixelied.survival.execution.MinecraftServerStateEvidence;
import dev.pixelied.survival.execution.NonTotemActionExecutor;
import dev.pixelied.survival.execution.NonTotemExecutionContext;
import dev.pixelied.survival.execution.PendingEquipmentMutation;
import dev.pixelied.survival.execution.ServerAuthorityTracker;
import dev.pixelied.survival.execution.ServerStateEvidenceSnapshot;
import dev.pixelied.survival.execution.ShieldActionExecutor;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import dev.pixelied.survival.planner.AuthorityAwareCandidateGenerator;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.threat.AreaEffectCloudAttributionTracker;
import dev.pixelied.survival.threat.EnvironmentPredictorRegistry;
import dev.pixelied.survival.threat.EvokerFangsPredictor;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.threat.FallPredictor;
import dev.pixelied.survival.threat.GuardianBeamPredictor;
import dev.pixelied.survival.threat.MeleePredictor;
import dev.pixelied.survival.threat.ProjectilePredictor;
import dev.pixelied.survival.threat.ReactiveDamagePredictor;
import dev.pixelied.survival.threat.ShulkerBulletPredictor;
import dev.pixelied.survival.threat.SplashStatusThreatMemory;
import dev.pixelied.survival.threat.ThreatPredictor;
import dev.pixelied.survival.threat.ThreatPredictorRegistry;
import dev.pixelied.survival.threat.WardenSonicBoomPredictor;
import dev.pixelied.survival.threat.opportunity.BedOpportunityPredictor;
import dev.pixelied.survival.threat.opportunity.CrystalOpportunityPredictor;
import dev.pixelied.survival.threat.opportunity.LethalOpportunity;
import dev.pixelied.survival.threat.opportunity.LethalOpportunityRegistry;
import dev.pixelied.survival.threat.opportunity.MeleeApproachOpportunityPredictor;
import dev.pixelied.survival.threat.opportunity.OpportunityTimelineAssembler;
import dev.pixelied.survival.threat.opportunity.ProjectileReleaseOpportunityPredictor;
import dev.pixelied.survival.threat.opportunity.RespawnAnchorOpportunityPredictor;
import dev.pixelied.survival.threat.opportunity.TntMinecartOpportunityPredictor;
import dev.pixelied.survival.timing.ServerTimingEstimator;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class MinecraftSurvivalRuntime implements SurvivalEngine.RuntimeAdapter {
    private final Minecraft minecraft;
    private final EngineLimits limits;
    private final MinecraftSnapshotFactory playerSnapshots;
    private final MinecraftWorldSnapshotFactory worldSnapshots;
    private final MinecraftSpecialThreatSnapshotAnnotator specialThreatSnapshots;
    private final MinecraftContactHazardSnapshotAnnotator contactHazardSnapshots;
    private final MinecraftReactiveThreatSnapshotAnnotator reactiveThreatSnapshots;
    private final MinecraftInventorySnapshotFactory inventorySnapshots;
    private final ServerTimingEstimator timingEstimator;
    private final AreaEffectCloudAttributionTracker cloudAttributions;
    private final SplashStatusThreatMemory splashStatusMemory;
    private final ThreatPredictorRegistry predictors;
    private final LethalOpportunityRegistry opportunityPredictors;
    private final OpportunityTimelineAssembler opportunityTimelineAssembler;
    private final AuthorityAwareCandidateGenerator candidateGenerator;
    private final DeathProtectionActionExecutor protectionExecutor;
    private final DeathProtectionRestorationController restorationController;
    private final ShieldActionExecutor shieldExecutor;
    private final NonTotemActionExecutor nonTotemExecutor;
    private final MinecraftCommandDispatcher dispatcher;
    private final DeathProtectionPopTracker popTracker;
    private final ServerDamageStateReconciler damageReconciler;
    private final ServerHurtStateTracker hurtStateTracker;

    private final CaptureTickClock captureTickClock = new CaptureTickClock();
    private ServerAuthorityTracker authority;
    private LiveState liveState;
    private LocalPlayer lastPlayer;
    private long clientTick;
    private long previousCaptureNanos;
    private PlayerSnapshot damageBaseline;
    private List<ThreatEvent> damageCandidates = List.of();
    private TimingSnapshot damageBaselineTiming;
    private int damageBaselinePlayerTick;
    private long damageBaselineEvidenceRevision;
    private long damageObservationGeneration = -1L;
    private int damagePendingStartPlayerTick = Integer.MIN_VALUE;

    public MinecraftSurvivalRuntime(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.limits = EngineLimits.defaults();
        this.playerSnapshots = new MinecraftSnapshotFactory();
        this.worldSnapshots = new MinecraftWorldSnapshotFactory();
        this.specialThreatSnapshots = new MinecraftSpecialThreatSnapshotAnnotator();
        this.contactHazardSnapshots = new MinecraftContactHazardSnapshotAnnotator();
        this.reactiveThreatSnapshots = new MinecraftReactiveThreatSnapshotAnnotator();
        this.inventorySnapshots = new MinecraftInventorySnapshotFactory();
        this.timingEstimator = new ServerTimingEstimator();
        this.cloudAttributions = new AreaEffectCloudAttributionTracker();
        this.splashStatusMemory = new SplashStatusThreatMemory();
        EnvironmentPredictorRegistry environment = EnvironmentPredictorRegistry.defaults();
        this.predictors = new ThreatPredictorRegistry(List.<ThreatPredictor>of(
            new ExplosionPredictor(),
            new ProjectilePredictor(),
            new ShulkerBulletPredictor(),
            new GuardianBeamPredictor(),
            new WardenSonicBoomPredictor(),
            new EvokerFangsPredictor(),
            new MeleePredictor(),
            new FallPredictor(),
            new ReactiveDamagePredictor(),
            environment::predict,
            splashStatusMemory
        ));
        this.opportunityPredictors = new LethalOpportunityRegistry(List.of(
            new CrystalOpportunityPredictor(),
            new BedOpportunityPredictor(),
            new RespawnAnchorOpportunityPredictor(),
            new TntMinecartOpportunityPredictor(),
            new MeleeApproachOpportunityPredictor(),
            new ProjectileReleaseOpportunityPredictor()
        ));
        this.opportunityTimelineAssembler = new OpportunityTimelineAssembler();
        this.candidateGenerator = new AuthorityAwareCandidateGenerator();
        this.protectionExecutor = new DeathProtectionActionExecutor();
        this.restorationController = new DeathProtectionRestorationController();
        this.shieldExecutor = new ShieldActionExecutor();
        this.nonTotemExecutor = new NonTotemActionExecutor();
        this.dispatcher = new MinecraftCommandDispatcher();
        this.popTracker = DeathProtectionPopTracker.global();
        this.damageReconciler = new ServerDamageStateReconciler();
        this.hurtStateTracker = new ServerHurtStateTracker();
    }

    @Override
    public SurvivalEngine.EngineFrame capture() {
        return capture(RescuePolicy.smartDefaults(), SafetyMode.BALANCED);
    }

    @Override
    public SurvivalEngine.EngineFrame capture(RescuePolicy policy) {
        return capture(policy, SafetyMode.BALANCED);
    }

    @Override
    public SurvivalEngine.EngineFrame capture(RescuePolicy policy, SafetyMode safetyMode) {
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(safetyMode, "safetyMode");
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            throw new IllegalStateException("Minecraft player/level are not available");
        }
        if (player != lastPlayer) {
            resetTransientState();
            lastPlayer = player;
        }

        boolean logicalTickAdvanced = captureTickClock.observe(player.tickCount);
        clientTick = captureTickClock.clientTick();
        observeTiming(player, logicalTickAdvanced);
        TimingSnapshot timing = timingEstimator.snapshot(clientTick);

        InventorySnapshot rawInventory = inventorySnapshots.captureInventory(player);
        PlayerSnapshot rawPlayer = playerSnapshots.capture(player);
        if (authority == null) authority = new ServerAuthorityTracker(rawInventory, rawPlayer.mitigation());
        // Evidence hooks run after vanilla applies clientbound state. Consume those authoritative
        // deltas before classifying any remaining local equipment change as prediction/user action.
        ServerStateEvidenceSnapshot serverEvidence = MinecraftServerStateEvidence.snapshot();
        authority.observeServerEvidence(serverEvidence, rawInventory);
        authority.observeUntrackedLocalSelection(rawInventory, timing);
        authority.observeUntrackedLocalMitigation(rawPlayer.mitigation(), timing);
        EquipmentAuthorityProjection equipment = authority.equipmentProjection(
            rawInventory,
            rawPlayer.mitigation(),
            clientTick
        );
        popTracker.reconcile(equipment, rawInventory, serverEvidence, clientTick);
        InventorySnapshot inventory = popTracker.conservativeInventoryAfterPop(rawInventory, equipment, clientTick);
        MenuSlotMap menu = inventorySnapshots.captureMenu(player);

        DeathProtectionSnapshot projectedProtection = popTracker.projectedDeathProtectionAt(equipment, clientTick);
        PlayerSnapshot authorityPlayer = withAuthoritativeDeathProtection(
            rawPlayer,
            equipment,
            projectedProtection,
            clientTick
        );
        PlayerSnapshot contactPlayer = contactHazardSnapshots.annotate(player, authorityPlayer);
        PlayerSnapshot playerSnapshot = withConservativeBlocking(contactPlayer, player, timing);
        LocalDamageObservationBuffer.Snapshot damageEvidence = LocalDamageObservationBuffer.snapshot();
        DamageReconciliation damageReconciliation = reconcileServerDamage(
            playerSnapshot,
            player.tickCount,
            timing,
            damageEvidence
        );
        playerSnapshot = damageReconciliation.player();
        WorldSnapshot rawWorld = worldSnapshots.capture(minecraft.level, player, limits);
        WorldSnapshot specialWorld = specialThreatSnapshots.annotate(minecraft.level, player, rawWorld);
        WorldSnapshot world = cloudAttributions.annotate(clientTick, specialWorld);
        MinecraftReactiveThreatSnapshotAnnotator.AnnotatedSnapshot reactive = reactiveThreatSnapshots.annotate(
            minecraft,
            playerSnapshot,
            world
        );
        PredictionContext context = new PredictionContext(reactive.player(), reactive.world(), timing, limits, safetyMode);
        List<ThreatEvent> predicted = predictors.predictAll(context);
        cloudAttributions.observePredictedThreats(clientTick, predicted);
        splashStatusMemory.observePredictedThreats(context, predicted);
        ThreatTimeline actualTimeline = new ThreatTimeline(predicted);
        List<LethalOpportunity> opportunities = opportunityPredictors.predictAll(context);
        ThreatTimeline planningTimeline = opportunityTimelineAssembler.assemble(actualTimeline, opportunities, limits.maxThreats());
        List<SurvivalAction> candidates = candidateGenerator.generate(
            context,
            planningTimeline,
            rawInventory,
            menu,
            policy,
            equipment,
            popTracker
        );

        SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(context, actualTimeline, opportunities, planningTimeline, candidates);
        liveState = new LiveState(frame, inventory, menu, timing, reactive.player());
        if (damageReconciliation.advanceBaseline()) {
            rememberDamageBaseline(reactive.player(), predicted, timing, player.tickCount, damageEvidence);
        }
        return frame;
    }

    @Override
    public void maintainRestoration(
        SurvivalEngine.EngineFrame frame,
        boolean restorationEnabled,
        boolean lethalWithoutProtection,
        boolean survivalActionActive
    ) {
        LiveState state = requireLiveState(frame);
        protectionExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);
        shieldExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);
        nonTotemExecutor.takeRestorationCheckpoint().ifPresent(restorationController::arm);
        Optional<ExecutionCommand> restore = restorationController.update(
            restorationEnabled,
            lethalWithoutProtection,
            survivalActionActive,
            popTracker.generation(),
            popTracker.consumptionUnresolved(),
            executionContext(state)
        );
        if (restore.isEmpty()) return;

        ExecutionCommand command = restore.get();
        if (!dispatcher.dispatch(minecraft, command)) {
            restorationController.abort();
            return;
        }
        if (command instanceof ExecutionCommand.SelectHotbar select) {
            authority.sentHotbarSelection(
                select.hotbarIndex(),
                state.timing(),
                state.inventory(),
                PendingEquipmentMutation.Origin.RESTORE
            );
        }
    }

    @Override
    public ExecutionStatus begin(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
        Objects.requireNonNull(action, "action");
        LiveState state = requireLiveState(frame);
        ExecutionStatus status;
        if (action instanceof SurvivalAction.EquipDeathProtection protection) {
            status = protectionExecutor.begin(protection, executionContext(state));
        } else if (action instanceof SurvivalAction.RaiseShield shield) {
            status = shieldExecutor.begin(shield, executionContext(state));
        } else {
            status = nonTotemExecutor.begin(action, nonTotemContext(state));
        }
        return dispatchIfNeeded(status, state, equipmentOrigin(action));
    }

    @Override
    public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
        Objects.requireNonNull(action, "action");
        LiveState state = requireLiveState(frame);
        ExecutionStatus status;
        if (action instanceof SurvivalAction.EquipDeathProtection) {
            status = protectionExecutor.observe(executionContext(state));
        } else if (action instanceof SurvivalAction.RaiseShield) {
            status = shieldExecutor.observe(executionContext(state));
        } else {
            status = nonTotemExecutor.observe(nonTotemContext(state));
        }
        return dispatchIfNeeded(status, state, equipmentOrigin(action));
    }

    @Override
    public int remainingServerTicks(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
        Objects.requireNonNull(action, "action");
        LiveState state = requireLiveState(frame);
        if (action instanceof SurvivalAction.EquipDeathProtection) {
            return protectionExecutor.remainingServerTicks(executionContext(state));
        }
        if (action instanceof SurvivalAction.RaiseShield) {
            return shieldExecutor.remainingServerTicks(executionContext(state));
        }
        return nonTotemExecutor.remainingServerTicks(nonTotemContext(state));
    }

    public void reset() {
        resetTransientState();
        lastPlayer = null;
    }

    public Optional<SurvivalEngine.EngineFrame> lastFrame() {
        return liveState == null ? Optional.empty() : Optional.of(liveState.frame());
    }

    private void observeTiming(LocalPlayer player, boolean logicalTickAdvanced) {
        if (logicalTickAdvanced) {
            long now = System.nanoTime();
            if (previousCaptureNanos != 0L && now > previousCaptureNanos) {
                timingEstimator.observeClientTickNanos(now - previousCaptureNanos);
            }
            previousCaptureNanos = now;
        }

        if (minecraft.getConnection() == null) return;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
        if (info != null) timingEstimator.observeRttMillis(Math.max(0, info.getLatency()));
    }


    private DamageReconciliation reconcileServerDamage(
        PlayerSnapshot current,
        int playerTick,
        TimingSnapshot timing,
        LocalDamageObservationBuffer.Snapshot evidence
    ) {
        if (damageBaseline == null || damageObservationGeneration != evidence.generation()) {
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            damageObservationGeneration = evidence.generation();
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }
        if (current.deadOrDying()) {
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }

        int elapsed = elapsedPlayerTicks(damageBaselinePlayerTick, playerTick);
        if (!evidence.damageEventsCompleteSince(damageBaselineEvidenceRevision)) {
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }

        boolean healthChanged = !nearlyEqual(damageBaseline.health(), current.health());
        boolean absorptionChanged = !nearlyEqual(damageBaseline.absorption(), current.absorption());
        boolean stateChanged = healthChanged || absorptionChanged;
        if (healthChanged && !matchesAuthoritativeValue(evidence.health(), damageBaselineEvidenceRevision, current.health())) {
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }
        if (absorptionChanged && !matchesAuthoritativeValue(evidence.absorption(), damageBaselineEvidenceRevision, current.absorption())) {
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }

        List<ServerDamageStateReconciler.DamageEventObservation> damageEvents = damageEventObservations(evidence);
        if (!stateChanged && !damageEvents.isEmpty()) {
            if (damageEvents.size() > 1 || pendingExpired(playerTick, timing)) {
                hurtStateTracker.invalidate();
                damagePendingStartPlayerTick = Integer.MIN_VALUE;
                return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
            }
            beginPending(playerTick);
            // A full-hit packet is positive evidence that the prior hurt state may already be stale.
            // Keep the old internal baseline for later health/absorption correlation, but never expose
            // its active cooldown as trusted while the authoritative state update is still pending.
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), false);
        }

        HurtState reconciled = damageReconciler.reconcile(
            damageBaseline,
            damageCandidates,
            current.health(),
            current.absorption(),
            damageEvents
        );
        boolean matched = reconciled.confidence() == Confidence.MATCHED;
        if (stateChanged && !matched) {
            // Health and damage-event packets are separate evidence streams. If the authoritative
            // value arrives first, retain the old baseline briefly so a later damage-event packet can
            // still establish a full hit. Differential hits already match above without that packet.
            if (damageEvents.isEmpty() && !pendingExpired(playerTick, timing)) {
                beginPending(playerTick);
                return new DamageReconciliation(withHurtState(current, HurtState.unknown()), false);
            }
            boolean oneCausallyCompatibleEvent = damageEvents.size() == 1
                && damageCandidates.stream().anyMatch(candidate ->
                    candidate.damage().sourceKey().equals(damageEvents.getFirst().sourceKey())
                        && candidate.impact().overlaps(damageEvents.getFirst().observedAt())
                );
            if (oneCausallyCompatibleEvent && !pendingExpired(playerTick, timing)) {
                beginPending(playerTick);
                return new DamageReconciliation(withHurtState(current, HurtState.unknown()), false);
            }
            hurtStateTracker.invalidate();
            damagePendingStartPlayerTick = Integer.MIN_VALUE;
            return new DamageReconciliation(withHurtState(current, HurtState.unknown()), true);
        }

        damagePendingStartPlayerTick = Integer.MIN_VALUE;
        hurtStateTracker.recordReconciled(reconciled);
        hurtStateTracker.tick(elapsed);
        return new DamageReconciliation(withHurtState(current, hurtStateTracker.current()), true);
    }

    private List<ServerDamageStateReconciler.DamageEventObservation> damageEventObservations(
        LocalDamageObservationBuffer.Snapshot evidence
    ) {
        TickWindow age = damageBaselineTiming.observationAgeWindow();
        return evidence.damageEventsAfter(damageBaselineEvidenceRevision).stream()
            .map(event -> {
                long arrivalOffset = elapsedPlayerTicks(damageBaselinePlayerTick, event.playerTick());
                long earliest = Math.max(0L, arrivalOffset - age.latest());
                long latest = Math.max(earliest, arrivalOffset - age.earliest());
                return new ServerDamageStateReconciler.DamageEventObservation(
                    event.sourceKey(),
                    new TickWindow(earliest, latest)
                );
            })
            .toList();
    }

    private void rememberDamageBaseline(
        PlayerSnapshot player,
        List<ThreatEvent> predicted,
        TimingSnapshot timing,
        int playerTick,
        LocalDamageObservationBuffer.Snapshot evidence
    ) {
        damageBaseline = player;
        damageCandidates = List.copyOf(predicted);
        damageBaselineTiming = timing;
        damageBaselinePlayerTick = playerTick;
        damageBaselineEvidenceRevision = evidence.revision();
        damageObservationGeneration = evidence.generation();
        damagePendingStartPlayerTick = Integer.MIN_VALUE;
    }

    private void beginPending(int playerTick) {
        if (damagePendingStartPlayerTick == Integer.MIN_VALUE) damagePendingStartPlayerTick = playerTick;
    }

    private boolean pendingExpired(int playerTick, TimingSnapshot timing) {
        if (damagePendingStartPlayerTick == Integer.MIN_VALUE) return false;
        long grace = Math.max(1L, timing.observationAgeWindow().latest() + 1L);
        return elapsedPlayerTicks(damagePendingStartPlayerTick, playerTick) > grace;
    }

    private static boolean matchesAuthoritativeValue(
        LocalDamageObservationBuffer.ValueEvidence evidence,
        long afterRevision,
        float currentValue
    ) {
        return evidence != null
            && evidence.revision() > afterRevision
            && nearlyEqual(evidence.value(), currentValue);
    }

    private static int elapsedPlayerTicks(int before, int after) {
        long elapsed = (long) after - before;
        if (elapsed < 0L || elapsed >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return (int) elapsed;
    }

    private static boolean nearlyEqual(float first, float second) {
        return Math.abs(first - second) <= 0.0001f;
    }

    private static PlayerSnapshot withHurtState(PlayerSnapshot player, HurtState hurtState) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(),
            hurtState, player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), player.stateProperties()
        );
    }

    private PlayerSnapshot withConservativeBlocking(
        PlayerSnapshot snapshot,
        LocalPlayer player,
        TimingSnapshot timing
    ) {
        boolean localUsing = player.isUsingItem();
        SurvivalAction.Hand localHand = localUsing ? hand(player.getUsedItemHand()) : null;
        boolean trackedUsing = authority.confirmedUsingItem(localUsing, localHand, clientTick);

        BlockingSnapshot blocking = snapshot.blocking();
        if (!blocking.usingBlockingItem()) return withHeadYaw(snapshot, blocking, player.getYHeadRot());

        int confirmedTicks = trackedUsing
            ? authority.confirmedUseTicks(true, localHand, clientTick)
            : 0;
        if (!trackedUsing && localUsing) {
            long latestDelay = Math.max(0L, timing.nextPacketProcessingWindow().latest() - timing.clientTick());
            int delay = latestDelay >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latestDelay;
            confirmedTicks = Math.max(0, player.getTicksUsingItem() - delay);
        }

        BlockingSnapshot conservative = blocking.withElapsedUseTicks(confirmedTicks);
        return withHeadYaw(snapshot, conservative, player.getYHeadRot());
    }

    private static PlayerSnapshot withAuthoritativeDeathProtection(
        PlayerSnapshot player,
        EquipmentAuthorityProjection equipment,
        DeathProtectionSnapshot protection,
        long serverTick
    ) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), equipment.conservativeMitigationAt(serverTick),
            player.statusEffects(), player.blocking(), player.hurtState(),
            protection, player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), player.stateProperties()
        );
    }

    private static PlayerSnapshot withHeadYaw(PlayerSnapshot player, BlockingSnapshot blocking, float headYaw) {
        var state = new java.util.LinkedHashMap<>(player.stateProperties());
        state.put("head_yaw", Float.toString(headYaw));
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(),
            player.deadOrDying(), player.difficulty(), player.mitigation(), player.statusEffects(), blocking,
            player.hurtState(), player.deathProtection(), player.boundingBox(), player.position(), player.velocity(),
            player.equipmentItemKeys(), state
        );
    }

    private ExecutionContext executionContext(LiveState state) {
        LocalPlayer player = minecraft.player;
        if (player == null) throw new IllegalStateException("Minecraft player disappeared during execution");

        boolean localUsing = player.isUsingItem();
        SurvivalAction.Hand localHand = localUsing ? hand(player.getUsedItemHand()) : null;
        boolean trackedUsing = authority.confirmedUsingItem(localUsing, localHand, clientTick);
        int useTicks;
        boolean serverUsing;
        if (trackedUsing) {
            serverUsing = true;
            useTicks = authority.confirmedUseTicks(true, localHand, clientTick);
        } else if (localUsing) {
            long latestDelay = Math.max(0L, state.timing().nextPacketProcessingWindow().latest() - state.timing().clientTick());
            int delay = latestDelay >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latestDelay;
            useTicks = Math.max(0, player.getTicksUsingItem() - delay);
            serverUsing = player.getTicksUsingItem() >= delay;
        } else {
            serverUsing = false;
            useTicks = 0;
        }

        return new ExecutionContext(
            state.inventory(),
            state.menu(),
            state.timing(),
            clientTick,
            serverUsing,
            serverUsing ? localHand : null,
            useTicks,
            true
        );
    }

    private NonTotemExecutionContext nonTotemContext(LiveState state) {
        return new NonTotemExecutionContext(executionContext(state), state.player(), Set.of());
    }

    private ExecutionStatus dispatchIfNeeded(
        ExecutionStatus status,
        LiveState state,
        PendingEquipmentMutation.Origin origin
    ) {
        if (!(status instanceof ExecutionStatus.WaitingForServer waiting) || waiting.command().isEmpty()) return status;
        ExecutionCommand command = waiting.command().get();
        if (!dispatcher.dispatch(minecraft, command)) {
            return new ExecutionStatus.Failed("client could not dispatch the planned server-valid command", true);
        }

        if (command instanceof ExecutionCommand.SelectHotbar select) {
            authority.sentHotbarSelection(
                select.hotbarIndex(),
                state.timing(),
                state.inventory(),
                origin
            );
        } else if (command instanceof ExecutionCommand.UseItem use) {
            authority.sentUseItem(use.hand(), state.timing());
        } else if (command instanceof ExecutionCommand.AimAndUseItem aim) {
            authority.sentUseItem(aim.hand(), state.timing());
        }
        return status;
    }

    private static PendingEquipmentMutation.Origin equipmentOrigin(SurvivalAction action) {
        return action instanceof SurvivalAction.EquipDeathProtection
            ? PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION
            : PendingEquipmentMutation.Origin.SURVIVAL_ITEM;
    }

    private LiveState requireLiveState(SurvivalEngine.EngineFrame frame) {
        if (liveState == null || liveState.frame() != frame) {
            throw new IllegalStateException("execution must use the frame captured for the current engine tick");
        }
        return liveState;
    }

    private void resetTransientState() {
        if (authority != null) authority.reset();
        authority = null;
        liveState = null;
        timingEstimator.reset();
        cloudAttributions.reset();
        splashStatusMemory.reset();
        protectionExecutor.reset();
        restorationController.abort();
        shieldExecutor.reset();
        nonTotemExecutor.reset();
        popTracker.reset();
        hurtStateTracker.invalidate();
        damageBaseline = null;
        damageCandidates = List.of();
        damageBaselineTiming = null;
        damageBaselineEvidenceRevision = 0L;
        damageObservationGeneration = -1L;
        damagePendingStartPlayerTick = Integer.MIN_VALUE;
        LocalDamageObservationBuffer.invalidate();
        captureTickClock.resetObservation();
        previousCaptureNanos = 0L;
    }

    private static SurvivalAction.Hand hand(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? SurvivalAction.Hand.OFF_HAND : SurvivalAction.Hand.MAIN_HAND;
    }

    private record DamageReconciliation(PlayerSnapshot player, boolean advanceBaseline) {
    }

    private record LiveState(
        SurvivalEngine.EngineFrame frame,
        InventorySnapshot inventory,
        MenuSlotMap menu,
        TimingSnapshot timing,
        PlayerSnapshot player
    ) {
    }
}
