package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.execution.DeathProtectionActionExecutor;
import dev.pixelied.survival.execution.ExecutionCommand;
import dev.pixelied.survival.execution.ExecutionContext;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.execution.MinecraftCommandDispatcher;
import dev.pixelied.survival.execution.NonTotemActionExecutor;
import dev.pixelied.survival.execution.NonTotemExecutionContext;
import dev.pixelied.survival.execution.ServerAuthorityTracker;
import dev.pixelied.survival.execution.ShieldActionExecutor;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.MinecraftInventorySnapshotFactory;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalCandidateGenerator;
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
    private static final float DEFAULT_BLOCK_ANGLE_DEGREES = 90f;

    private final Minecraft minecraft;
    private final EngineLimits limits;
    private final MinecraftSnapshotFactory playerSnapshots;
    private final MinecraftWorldSnapshotFactory worldSnapshots;
    private final MinecraftSpecialThreatSnapshotAnnotator specialThreatSnapshots;
    private final MinecraftInventorySnapshotFactory inventorySnapshots;
    private final ServerTimingEstimator timingEstimator;
    private final AreaEffectCloudAttributionTracker cloudAttributions;
    private final SplashStatusThreatMemory splashStatusMemory;
    private final ThreatPredictorRegistry predictors;
    private final SurvivalCandidateGenerator candidateGenerator;
    private final DeathProtectionActionExecutor protectionExecutor;
    private final ShieldActionExecutor shieldExecutor;
    private final NonTotemActionExecutor nonTotemExecutor;
    private final MinecraftCommandDispatcher dispatcher;

    private ServerAuthorityTracker authority;
    private LiveState liveState;
    private long clientTick;
    private long previousCaptureNanos;

    public MinecraftSurvivalRuntime(Minecraft minecraft) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.limits = EngineLimits.defaults();
        this.playerSnapshots = new MinecraftSnapshotFactory();
        this.worldSnapshots = new MinecraftWorldSnapshotFactory();
        this.specialThreatSnapshots = new MinecraftSpecialThreatSnapshotAnnotator();
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
        this.candidateGenerator = new SurvivalCandidateGenerator();
        this.protectionExecutor = new DeathProtectionActionExecutor();
        this.shieldExecutor = new ShieldActionExecutor();
        this.nonTotemExecutor = new NonTotemActionExecutor();
        this.dispatcher = new MinecraftCommandDispatcher();
    }

    @Override
    public SurvivalEngine.EngineFrame capture() {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            throw new IllegalStateException("Minecraft player/level are not available");
        }

        clientTick++;
        observeTiming(player);
        TimingSnapshot timing = timingEstimator.snapshot(clientTick);

        InventorySnapshot rawInventory = inventorySnapshots.captureInventory(player);
        if (authority == null) authority = new ServerAuthorityTracker(rawInventory.selectedHotbarIndex());
        authority.observeUntrackedLocalSelection(rawInventory.selectedHotbarIndex(), timing);
        int confirmedSelected = authority.confirmedSelectedSlot(rawInventory.selectedHotbarIndex(), clientTick);
        InventorySnapshot inventory = new InventorySnapshot(
            confirmedSelected,
            rawInventory.slots(),
            rawInventory.activeOffhandShield()
        );
        MenuSlotMap menu = inventorySnapshots.captureMenu(player);

        PlayerSnapshot rawPlayer = playerSnapshots.capture(player);
        PlayerSnapshot playerSnapshot = withConservativeBlocking(rawPlayer, player, timing);
        WorldSnapshot rawWorld = worldSnapshots.capture(minecraft.level, player, limits);
        WorldSnapshot specialWorld = specialThreatSnapshots.annotate(minecraft.level, player, rawWorld);
        WorldSnapshot world = cloudAttributions.annotate(clientTick, specialWorld);
        PredictionContext context = new PredictionContext(playerSnapshot, world, timing, limits);
        List<ThreatEvent> predicted = predictors.predictAll(context);
        cloudAttributions.observePredictedThreats(clientTick, predicted);
        splashStatusMemory.observePredictedThreats(context, predicted);
        ThreatTimeline timeline = new ThreatTimeline(predicted);
        List<SurvivalAction> candidates = candidateGenerator.generate(context, timeline, inventory, menu);

        SurvivalEngine.EngineFrame frame = new SurvivalEngine.EngineFrame(context, timeline, candidates);
        liveState = new LiveState(frame, inventory, menu, timing, playerSnapshot);
        return frame;
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
        return dispatchIfNeeded(status, state.timing());
    }

    @Override
    public ExecutionStatus observe(SurvivalAction action, SurvivalEngine.EngineFrame frame) {
        Objects.requireNonNull(action, "action");
        LiveState state = requireLiveState(frame);
        if (action instanceof SurvivalAction.EquipDeathProtection) {
            return protectionExecutor.observe(executionContext(state));
        }
        if (action instanceof SurvivalAction.RaiseShield) {
            return shieldExecutor.observe(executionContext(state));
        }
        return nonTotemExecutor.observe(nonTotemContext(state));
    }

    public Optional<SurvivalEngine.EngineFrame> lastFrame() {
        return liveState == null ? Optional.empty() : Optional.of(liveState.frame());
    }

    private void observeTiming(LocalPlayer player) {
        long now = System.nanoTime();
        if (previousCaptureNanos != 0L && now > previousCaptureNanos) {
            timingEstimator.observeClientTickNanos(now - previousCaptureNanos);
        }
        previousCaptureNanos = now;

        if (minecraft.getConnection() == null) return;
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(player.getUUID());
        if (info != null) timingEstimator.observeRttMillis(Math.max(0, info.getLatency()));
    }

    private PlayerSnapshot withConservativeBlocking(
        PlayerSnapshot snapshot,
        LocalPlayer player,
        TimingSnapshot timing
    ) {
        BlockingSnapshot blocking = snapshot.blocking();
        if (!blocking.usingBlockingItem()) return withHeadYaw(snapshot, blocking, player.getYHeadRot());

        SurvivalAction.Hand localHand = hand(player.getUsedItemHand());
        boolean localUsing = player.isUsingItem();
        int confirmedTicks = authority.confirmedUseTicks(localUsing, localHand, clientTick);
        if (!authority.confirmedUsingItem(localUsing, localHand, clientTick) && localUsing) {
            long latestDelay = Math.max(0L, timing.nextPacketProcessingWindow().latest() - timing.clientTick());
            int delay = latestDelay >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) latestDelay;
            confirmedTicks = Math.max(0, player.getTicksUsingItem() - delay);
        }

        BlockingSnapshot conservative = new BlockingSnapshot(
            blocking.usingBlockingItem(),
            blocking.blockedFraction(),
            confirmedTicks,
            blocking.requiredUseTicks()
        );
        return withHeadYaw(snapshot, conservative, player.getYHeadRot());
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
        boolean trackedUsing = localUsing && authority.confirmedUsingItem(localUsing, localHand, clientTick);
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
            shieldAngleValid(state.frame().timeline(), state.player())
        );
    }

    private NonTotemExecutionContext nonTotemContext(LiveState state) {
        return new NonTotemExecutionContext(executionContext(state), state.player(), Set.of());
    }

    private boolean shieldAngleValid(ThreatTimeline timeline, PlayerSnapshot player) {
        float yaw = parseFloat(player.state("head_yaw"), Float.NaN);
        if (!Float.isFinite(yaw) || timeline.events().isEmpty()) return false;
        for (ThreatEvent event : timeline.events()) {
            if (!event.blockable() || event.canDisableBlocking() || event.sourcePosition().isEmpty()) return false;
            if (!ServerAuthorityTracker.withinHorizontalBlockAngle(
                player.position(), yaw, event.sourcePosition().get(), DEFAULT_BLOCK_ANGLE_DEGREES
            )) return false;
        }
        return true;
    }

    private ExecutionStatus dispatchIfNeeded(ExecutionStatus status, TimingSnapshot timing) {
        if (!(status instanceof ExecutionStatus.WaitingForServer waiting) || waiting.command().isEmpty()) return status;
        ExecutionCommand command = waiting.command().get();
        if (!dispatcher.dispatch(minecraft, command)) {
            return new ExecutionStatus.Failed("client could not dispatch the planned server-valid command", true);
        }

        if (command instanceof ExecutionCommand.SelectHotbar select) {
            authority.sentHotbarSelection(select.hotbarIndex(), timing);
        } else if (command instanceof ExecutionCommand.UseItem use) {
            authority.sentUseItem(use.hand(), timing);
        } else if (command instanceof ExecutionCommand.AimAndUseItem aim) {
            authority.sentUseItem(aim.hand(), timing);
        }
        return status;
    }

    private LiveState requireLiveState(SurvivalEngine.EngineFrame frame) {
        if (liveState == null || liveState.frame() != frame) {
            throw new IllegalStateException("execution must use the frame captured for the current engine tick");
        }
        return liveState;
    }

    private static SurvivalAction.Hand hand(InteractionHand hand) {
        return hand == InteractionHand.OFF_HAND ? SurvivalAction.Hand.OFF_HAND : SurvivalAction.Hand.MAIN_HAND;
    }

    private static float parseFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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
