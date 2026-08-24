package studio.pixelied.pearlcatch;

import static studio.pixelied.pearlcatch.PearlCatchDebug.*;
import static studio.pixelied.pearlcatch.CatchAttemptTracker.*;

import studio.pixelied.pearlcatch.core.GeneralCatchSolver;
import studio.pixelied.pearlcatch.core.Rotation;
import studio.pixelied.pearlcatch.core.ServerKnownMovementEstimator;
import studio.pixelied.pearlcatch.core.ServerTimingWindow;
import studio.pixelied.pearlcatch.core.VanillaProjectilePhysics;
import studio.pixelied.pearlcatch.core.Vec3d;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 26.1.2 joint pearl/wind catcher.
 *
 * Source-backed invariants used here:
 *  - The ender pearl, not the wind charge, must hit the other projectile. Ender pearls are not pickable/
 *    redirectable; wind charges are.
 *  - ProjectileUtil clips the pearl movement segment against the wind AABB. Starting inside the AABB is not a hit.
 *  - Pearl motion applies gravity 0.03 then inertia 0.99 before the movement/collision sweep.
 *  - Player-thrown pearl and wind charge both launch at power 1.5 with uncertainty 1.0 and inherit thrower motion.
 *  - ServerboundUseItemPacket carries yaw/pitch and the server snaps to those values immediately before Item#use.
 *    Silent rotation therefore temporarily changes the local yaw/pitch around vanilla gameMode.useItem so the
 *    vanilla use packet itself contains the solver rotation; a standalone look packet alone is not sufficient.
 */
final class CatchCoordinator {

    private long clientTick;
    private final List<TrackingShot> activeShots = new ArrayList<>();
    private final List<PendingCatch> pendingCatches = new ArrayList<>();
    private final List<LegitPearlLaunch> legitPearlLaunches = new ArrayList<>();
    private final List<LegitRestore> legitRestores = new ArrayList<>();
    private long nextAttemptId = 1L;
    private final VanillaInputExecutor vanillaInput = new VanillaInputExecutor();
    private DebugSweep sweep;
    private TraceSession traceSession;
    private CameraRestore pendingCameraRestore;
    private final ServerKnownMovementEstimator serverMovementEstimator = new ServerKnownMovementEstimator();
    private int movementEstimatorPlayerId = Integer.MIN_VALUE;
    private ClientLevel executionLevel;
    private int executionPlayerId = Integer.MIN_VALUE;
    private static final double NETWORK_TIMING_ROTATION_TOLERANCE_DEGREES = 0.10;

    /** Capture the start position before LocalPlayer#tick moves and sends this tick's movement packet. */
    public void beginClientTick(Minecraft mc) {
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) {
            resetMovementEstimator();
            return;
        }
        ensureMovementEstimatorPlayer(player);
        serverMovementEstimator.beginTick(toCore(player.position()));
    }

    /**
     * Finalize the packet-space movement estimate before any G/H item use in END_CLIENT_TICK.
     * Fabric fires END_CLIENT_TICK at Minecraft#tick RETURN, after LocalPlayer has already sent its
     * normal movement packet and the client tick-end packet.
     */
    public void captureEndClientTick(Minecraft mc) {
        LocalPlayer player = mc == null ? null : mc.player;
        if (player == null) {
            resetMovementEstimator();
            return;
        }
        ensureMovementEstimatorPlayer(player);
        serverMovementEstimator.endTick(toCore(player.position()));
    }

    public void tick(Minecraft mc, PearlCatchConfig config) {
        clientTick++;
        config.sanitize();

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.connection == null) {
            if (executionLevel != null || executionPlayerId != Integer.MIN_VALUE) resetExecutionState();
            return;
        }
        if (executionLevel != level || executionPlayerId != player.getId()) {
            resetExecutionState();
            executionLevel = level;
            executionPlayerId = player.getId();
        }
        if (player.isDeadOrDying()) {
            resetExecutionState();
            executionLevel = level;
            executionPlayerId = player.getId();
            return;
        }

        if (!config.enabled) {
            cancelAllOwnedState(mc, player, config, "DISABLED_CLEANUP");
            return;
        }

        if (pendingCameraRestore != null && clientTick >= pendingCameraRestore.restoreAtTick()) {
            player.setYRot(pendingCameraRestore.yaw());
            player.setXRot(pendingCameraRestore.pitch());
            pendingCameraRestore = null;
        }

        settleLegitInputLease(mc, player);

        // Timing-critical wind preparation gets first chance at the single real-input lease.
        for (PendingCatch pending : new ArrayList<>(pendingCatches)) {
            tickPendingCatch(mc, player, level, pending, config);
        }
        for (LegitRestore restore : new ArrayList<>(legitRestores)) {
            tickLegitRestore(mc, player, restore, config);
        }
        for (LegitPearlLaunch launch : new ArrayList<>(legitPearlLaunches)) {
            tickLegitPearlLaunch(mc, player, level, launch, config);
        }

        for (TrackingShot shot : new ArrayList<>(activeShots)) {
            updateTracking(mc, player, level, shot, config);
        }

        if (config.debugVisualization) {
            for (TrackingShot shot : activeShots) renderVisualization(level, shot, config);
        }

        if (!activeShots.isEmpty() && config.debugOverlay && clientTick % 4 == 0) {
            showOverlay(mc, activeShots.get(activeShots.size() - 1), activeShots.size() + pendingCatches.size());
        }

        if (sweep != null && sweep.active() && !hasDebugAttempt(legitPearlLaunches, pendingCatches, activeShots)) {
            tickSweep(mc, player, level, config);
        }
    }

    public void triggerAutoPearlCatch(Minecraft mc, PearlCatchConfig config) {
        config.sanitize();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.connection == null) return;
        if (!config.enabled) {
            player.sendSystemMessage(Component.literal("Pearl Catcher is disabled in Mod Menu."));
            return;
        }
        Rotation target = new Rotation(player.getYRot(), player.getXRot());
        startCatchAttempt(mc, player, level, target, "manual", false, config);
    }

    public void triggerVerticalPearlCatch(Minecraft mc, PearlCatchConfig config) {
        config.sanitize();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.connection == null) return;
        if (!config.enabled) {
            player.sendSystemMessage(Component.literal("Pearl Catcher is disabled in Mod Menu."));
            return;
        }
        Rotation target = new Rotation(player.getYRot(), -90.0);
        startCatchAttempt(mc, player, level, target, "vertical", false, config);
    }

    public void toggleDebugSweep(Minecraft mc, PearlCatchConfig config) {
        config.sanitize();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.connection == null) return;

        if (sweep != null && sweep.active()) {
            finishSweep(mc, player, config, "stopped by user");
            return;
        }
        double step = config.pitchSweepStep;
        if (config.pitchSweepEnd >= config.pitchSweepStart && step < 0) step = -step;
        if (config.pitchSweepEnd < config.pitchSweepStart && step > 0) step = -step;
        sweep = new DebugSweep(
                player.getYRot(), config.pitchSweepStart, config.pitchSweepEnd, step,
                0, 0, true, player.getYRot(), player.getXRot()
        );
        traceSession = config.debugExport ? TraceSession.start(player, config, solverHorizon(config)) : null;
        player.sendSystemMessage(Component.literal(
                "Pearl Catcher debug sweep: " + fmt(config.pitchSweepStart) + "° → " + fmt(config.pitchSweepEnd)
                        + "° step " + fmt(step) + "°."
        ));
        if (traceSession != null) {
            player.sendSystemMessage(Component.literal("Trace JSON: " + traceSession.jsonPath));
            player.sendSystemMessage(Component.literal("Trace text: " + traceSession.textPath));
        }
    }

    private void tickSweep(Minecraft mc, LocalPlayer player, ClientLevel level, PearlCatchConfig config) {
        if (sweep == null || !sweep.active()) return;
        if (sweep.waitTicks > 0) {
            sweep.waitTicks--;
            return;
        }
        if (sweep.done()) {
            finishSweep(mc, player, config, "finished normally");
            return;
        }

        ItemLocation readyPearl = resolveItemLocation(player, Items.ENDER_PEARL);
        if (readyPearl != null && isOnCooldown(player, readyPearl)) {
            sweep.readinessWaitTicks++;
            if (sweep.readinessWaitTicks >= config.maxTicksPerPitch) {
                Rotation waitingTarget = new Rotation(sweep.yaw, sweep.currentPitch);
                recordUnlaunchedShot(player, waitingTarget, "debug pitch " + fmt(sweep.currentPitch),
                        "WAITING_FOR_PEARL_READY_TIMEOUT", config);
                sweep.readinessWaitTicks = 0;
                sweep.advance();
                sweep.waitTicks = config.debugBetweenShotsTicks;
            }
            return;
        }
        sweep.readinessWaitTicks = 0;

        float yaw = sweep.yaw;
        float pitch = (float)sweep.currentPitch;
        // The sweep pitch is the target crosshair ray. Item uses may silently rotate away from it to create the intercept.
        player.setYRot(yaw);
        player.setXRot(pitch);
        Rotation target = new Rotation(yaw, pitch);
        String label = "debug pitch " + fmt(pitch);
        player.sendSystemMessage(Component.literal("Pearl Catcher debug: " + label));

        boolean launched = startCatchAttempt(mc, player, level, target, label, true, config);
        sweep.advance();
        if (!launched) {
            sweep.waitTicks = config.debugBetweenShotsTicks;
        }
    }

    private void finishSweep(Minecraft mc, LocalPlayer player, PearlCatchConfig config, String reason) {
        if (traceSession != null) {
            traceSession.finishReason = reason;
            traceSession.write();
            if (config.debugChat) {
                player.sendSystemMessage(Component.literal("Pearl Catcher trace saved: " + traceSession.jsonPath));
            }
        }
        if (sweep != null) {
            player.setYRot(sweep.originalYaw);
            player.setXRot(sweep.originalPitch);
        }
        List<Long> debugOwners = new ArrayList<>();
        for (TrackingShot shot : activeShots) if (shot.debug) debugOwners.add(shot.attemptId);
        for (PendingCatch pending : pendingCatches) if (pending.debug) debugOwners.add(pending.attemptId);
        for (LegitPearlLaunch launch : legitPearlLaunches) if (launch.debug) debugOwners.add(launch.attemptId);
        for (long owner : debugOwners.stream().distinct().toList()) {
            cancelOwner(mc, player, config, owner, "DEBUG_SWEEP_CANCELLED");
        }
        sweep = null;
        traceSession = null;
        player.sendSystemMessage(Component.literal("Pearl Catcher debug sweep " + reason + "."));
    }

    private boolean startCatchAttempt(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            Rotation target,
            String label,
            boolean debug,
            PearlCatchConfig config
    ) {
        if (player.isPassenger()) {
            if (debug) recordUnlaunchedShot(player, target, label, "PASSENGER_MOVEMENT_UNSUPPORTED", config);
            if (!debug || config.debugChat) {
                player.sendSystemMessage(Component.literal("Pearl Catcher: riding movement is not modeled safely; catch refused."));
            }
            return false;
        }
        if (config.itemSwitchMode == PearlCatchConfig.ItemSwitchMode.LEGIT) {
            return startLegitPearlCatch(mc, player, level, target, label, debug, config);
        }
        return launchJointShot(mc, player, level, target, label, debug, config);
    }

    private boolean startLegitPearlCatch(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            Rotation target,
            String label,
            boolean debug,
            PearlCatchConfig config
    ) {
        ItemLocation pearl = resolveItemLocation(player, Items.ENDER_PEARL);
        ItemLocation wind = resolveItemLocation(player, Items.WIND_CHARGE);
        if (pearl == null || wind == null) {
            String reason = pearl == null ? "NO_ENDER_PEARL_AVAILABLE" : "NO_WIND_CHARGE_AVAILABLE";
            if (debug) recordUnlaunchedShot(player, target, label, reason, config);
            if (!debug || config.debugChat) {
                player.sendSystemMessage(Component.literal("Pearl Catcher: "
                        + (pearl == null ? "no ender pearl" : "no wind charge") + " in hotbar/offhand."));
            }
            return false;
        }

        long attemptId = nextAttemptId++;
        ShotTrace trace = ShotTrace.preparing(label, target, player, clientTick);
        trace.activeAttemptCountAtLaunch = activeShots.size() + pendingCatches.size() + legitPearlLaunches.size() + 1;
        trace.itemSwitchMode = config.itemSwitchMode.name();
        LegitPearlLaunch launch = new LegitPearlLaunch(
                attemptId, label, debug, clientTick, target,
                player.getInventory().getSelectedSlot(),
                entityIds(level, ThrownEnderpearl.class), entityIds(level, WindCharge.class), trace
        );
        legitPearlLaunches.add(launch);
        if (debug && traceSession != null) traceSession.shots.add(trace);
        if (!debug || config.debugChat) {
            player.sendSystemMessage(Component.literal("Pearl Catcher: Legit execution queued; waiting for confirmed vanilla input state."));
        }
        return true;
    }

    private void tickLegitPearlLaunch(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            LegitPearlLaunch launch,
            PearlCatchConfig config
    ) {
        if (!legitPearlLaunches.contains(launch)) return;
        launch.ageTicks++;

        if (launch.waitingForPearl) {
            launch.trace.action = "WAIT_PEARL_OBSERVED";
            if (clientTick - launch.useRequestedClientTick > Math.max(12, solverHorizon(config))) {
                finishLegitPearlLaunch(player, config, launch, "PEARL_ENTITY_TIMEOUT_AFTER_LEGIT_USE");
            }
            return;
        }

        if (mc.screen != null || mc.getOverlay() != null) {
            launch.trace.action = "WAIT_SCREEN_BLOCKS_LEGIT_INPUT";
            return;
        }
        if (hasLegitInputLease(launch.attemptId)) return;
        if (vanillaInput.hasLease()) {
            launch.trace.action = "WAIT_OTHER_LEGIT_INPUT";
            return;
        }
        if (launch.ageTicks > Math.max(40, solverHorizon(config) * 2)) {
            finishLegitPearlLaunch(player, config, launch, "LEGIT_PEARL_PREPARATION_TIMEOUT");
            return;
        }

        ItemLocation pearl = resolveItemLocation(player, Items.ENDER_PEARL);
        ItemLocation wind = resolveItemLocation(player, Items.WIND_CHARGE);
        if (pearl == null || wind == null) {
            finishLegitPearlLaunch(player, config, launch,
                    pearl == null ? "ENDER_PEARL_MOVED_OR_MISSING" : "WIND_CHARGE_MOVED_OR_MISSING");
            return;
        }

        if (!selectedMainIs(player, Items.ENDER_PEARL)) {
            if (pearl.hand() == InteractionHand.OFF_HAND) {
                launch.pearlOffhandSwapRequested = true;
                launch.pearlSwapSlot = player.getInventory().getSelectedSlot();
                launch.pearlSwapOriginalSelectedItem = player.getMainHandItem().getItem();
                if (queueLegitSwap(mc, launch.attemptId, Items.ENDER_PEARL, launch.pearlSwapSlot)) {
                    launch.trace.pearlItemPrepRequestedClientTick = clientTick;
                    launch.trace.action = "LEGIT_SWAP_PEARL_FROM_OFFHAND";
                    launch.trace.resolvedHand = InteractionHand.OFF_HAND.name();
                }
                return;
            }
            if (pearl.slot() >= 0) {
                if (queueLegitHotbar(mc, launch.attemptId, pearl.slot())) {
                    launch.trace.pearlItemPrepRequestedClientTick = clientTick;
                    launch.trace.action = "LEGIT_SELECT_PEARL_SLOT";
                    launch.trace.resolvedSlot = pearl.slot();
                }
                return;
            }
        }

        if (launch.pearlOffhandSwapRequested && selectedMainIs(player, Items.ENDER_PEARL)) {
            launch.pearlOffhandSwapped = true;
        }

        ItemLocation readyPearl = resolveSelectedMainLocation(player, Items.ENDER_PEARL);
        if (readyPearl == null) {
            launch.trace.action = "WAIT_PEARL_MAIN_HAND_CONFIRMATION";
            return;
        }
        if (launch.trace.pearlItemPrepConfirmedClientTick == null) {
            launch.trace.pearlItemPrepConfirmedClientTick = clientTick;
        }
        if (isOnCooldown(player, readyPearl)) {
            launch.trace.action = "WAIT_PEARL_COOLDOWN";
            return;
        }
        if (player.isUsingItem()) {
            launch.trace.action = "WAIT_PLAYER_USING_ITEM_BEFORE_PEARL";
            return;
        }

        int minimumWindDelay = legitPreparationLeadTicks(player, Items.WIND_CHARGE);
        if (minimumWindDelay < 0) {
            finishLegitPearlLaunch(player, config, launch, "WIND_CHARGE_MOVED_OR_MISSING");
            return;
        }

        Rotation effectiveTarget = solverTargetForExecution(player, launch.target, launch.label, config.rotationMode);
        Vec3 launchEyeMc = player.getEyePosition();
        Vec3d launchEye = toCore(launchEyeMc);
        Vec3d inherited = currentInheritedMotion(player);
        GeneralCatchSolver.Plan plan = solveGeneral(
                launchEye, inherited, null, 0, launchEye, inherited, effectiveTarget, config, minimumWindDelay);
        if (plan == null || plan.pearlRotation() == null) {
            launch.trace.action = "WAIT_NO_CURRENT_SOLUTION";
            return;
        }

        Rotation pearlUseRotation = config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA
                ? effectiveTarget : plan.pearlRotation();
        if (config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA
                && angleDistance(new Rotation(player.getYRot(), player.getXRot()), plan.pearlRotation()) > 0.35) {
            launch.trace.action = "WAIT_CURRENT_CAMERA_PEARL_CONSTRAINT";
            return;
        }

        List<Vec3> predictedPearl = predictPearl(launchEyeMc, plan.pearlRotation(), inherited, plan.pearlCatchTick());
        Vec3d nominalPearlLaunch = VanillaProjectilePhysics.nominalLaunchVelocity(plan.pearlRotation(), inherited);
        WindCharge existingWindHazard = firstExistingWindHazard(
                level, launchEye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                nominalPearlLaunch, plan.pearlCatchTick());
        if (existingWindHazard != null) {
            launch.trace.action = "WAIT_OLDER_WIND_HAZARD";
            return;
        }
        RuntimePathSafety.Result pearlSafety = RuntimePathSafety.checkPearl(level, player, predictedPearl);
        if (!pearlSafety.safe()) {
            launch.trace.action = "WAIT_PEARL_PATH_UNSAFE_" + pearlSafety.reason();
            return;
        }

        launch.commandedPearlRotation = plan.pearlRotation();
        launch.launchEye = launchEyeMc;
        launch.launchInheritedMotion = inherited;
        launch.predictedPearl = predictedPearl;
        launch.latestPlan = plan;
        launch.trace.plan = PlanTrace.from(plan);
        launch.trace.predictedPearlTrajectory.clear();
        for (Vec3 point : predictedPearl) launch.trace.predictedPearlTrajectory.add(vecTrace(point));
        launch.trace.solverInheritedMovementAtLaunch = vecTrace(fromCore(inherited));
        launch.trace.clientKnownMovementAtLaunch = vecTrace(player.getKnownMovement());
        launch.trace.minimumExecutableWindDelay = minimumWindDelay;
        launch.trace.resolvedHand = InteractionHand.MAIN_HAND.name();
        launch.trace.resolvedSlot = player.getInventory().getSelectedSlot();

        if (!queueLegitUse(mc, player, launch.attemptId, Items.ENDER_PEARL, pearlUseRotation, false, config)) {
            launch.trace.action = "WAIT_USE_KEY_UNAVAILABLE";
            return;
        }
        launch.waitingForPearl = true;
        launch.serverRotationNeedsRestore = config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET;
        launch.useRequestedClientTick = clientTick;
        launch.trace.action = "LEGIT_PEARL_USE_REQUESTED";
        launch.trace.useRequestedClientTick = clientTick;
    }

    private void finishLegitPearlLaunch(
            LocalPlayer player,
            PearlCatchConfig config,
            LegitPearlLaunch launch,
            String reason
    ) {
        if (!legitPearlLaunches.contains(launch)) return;
        vanillaInput.cancelOwner(launch.attemptId);
        if (launch.serverRotationNeedsRestore && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            restoreServerRotationAfterFinalUse(player, player.getYRot(), player.getXRot());
            launch.serverRotationNeedsRestore = false;
        }
        if (config.autoRestoreSlot) {
            boolean restoreOffhand = launch.pearlOffhandSwapped && launch.pearlSwapSlot >= 0;
            int ownedSlot = restoreOffhand ? launch.pearlSwapSlot : player.getInventory().getSelectedSlot();
            legitRestores.add(new LegitRestore(
                    launch.attemptId, launch.previousSlot, ownedSlot, restoreOffhand,
                    launch.pearlSwapSlot, launch.pearlSwapOriginalSelectedItem
            ));
        }
        launch.trace.finishReason = reason;
        if (launch.debug && traceSession != null) traceSession.write();
        if (config.debugChat || !launch.debug) {
            player.sendSystemMessage(Component.literal("Pearl Catcher Legit result: " + reason));
        }
        legitPearlLaunches.remove(launch);
        if (launch.debug && sweep != null && sweep.active()) sweep.waitTicks = config.debugBetweenShotsTicks;
    }

    private int legitPreparationLeadTicks(LocalPlayer player, Item item) {
        ItemLocation location = resolveItemLocation(player, item);
        if (location == null) return -1;
        if (selectedMainIs(player, item)) return 0;
        // A hotbar click or swap request is consumed on the next vanilla handleKeybinds pass.
        // Re-solving after confirmation handles any extra network delay; this is only a lower bound.
        return 1;
    }

    private static Rotation solverTargetForExecution(
            LocalPlayer player, Rotation requestedTarget, String label, PearlCatchConfig.RotationMode mode
    ) {
        if (mode != PearlCatchConfig.RotationMode.CURRENT_CAMERA) return requestedTarget;
        // H and the pitch debugger are fixed-ray requests. Current rotation may execute them only if the
        // real camera already matches; it must never silently turn them into an ordinary G catch.
        if ("vertical".equals(label) || label.startsWith("debug pitch ")) return requestedTarget;
        return new Rotation(player.getYRot(), player.getXRot());
    }

    private boolean queueLegitHotbar(Minecraft mc, long ownerAttemptId, int slot) {
        return vanillaInput.queueHotbar(mc, clientTick, ownerAttemptId, slot);
    }

    private boolean queueLegitSwap(Minecraft mc, long ownerAttemptId, Item expectedMainItem, int selectedSlot) {
        return vanillaInput.queueSwap(mc, clientTick, ownerAttemptId, expectedMainItem, selectedSlot);
    }

    private boolean queueLegitUse(
            Minecraft mc, LocalPlayer player, long ownerAttemptId, Item expectedMainItem, Rotation rotation,
            boolean restoreServerAfterUse, PearlCatchConfig config
    ) {
        VanillaInputExecutor.QueueUseResult result = vanillaInput.queueUse(
                mc, player, clientTick, ownerAttemptId, expectedMainItem, rotation, restoreServerAfterUse, config.rotationMode);
        if (result.cameraRestore() != null) {
            pendingCameraRestore = new CameraRestore(
                    result.cameraRestore().restoreAtTick(), result.cameraRestore().yaw(), result.cameraRestore().pitch());
        }
        return result.queued();
    }

    private void settleLegitInputLease(Minecraft mc, LocalPlayer player) {
        VanillaInputExecutor.LeaseEvent event = vanillaInput.settle(mc, player, clientTick);
        if (event.kind() == VanillaInputExecutor.LeaseEventKind.NONE) return;

        for (LegitPearlLaunch launch : legitPearlLaunches) {
            if (launch.attemptId != event.ownerAttemptId() || !launch.waitingForPearl) continue;
            launch.waitingForPearl = false;
            launch.useRequestedClientTick = -1;
            launch.trace.useRequestedClientTick = null;
            launch.trace.action = event.kind() == VanillaInputExecutor.LeaseEventKind.USE_EXPIRED
                    ? "LEGIT_USE_CONFIRMATION_TIMEOUT_RETRY"
                    : "WAIT_SCREEN_CANCELLED_QUEUED_PEARL_USE";
            return;
        }
        for (PendingCatch pending : pendingCatches) {
            if (pending.attemptId != event.ownerAttemptId() || !pending.windUseRequested) continue;
            pending.windUseRequested = false;
            pending.windUseRequestedClientTick = -1;
            pending.trace.useRequestedClientTick = null;
            pending.trace.action = event.kind() == VanillaInputExecutor.LeaseEventKind.USE_EXPIRED
                    ? "LEGIT_WIND_USE_CONFIRMATION_TIMEOUT_RETRY"
                    : "WAIT_SCREEN_CANCELLED_QUEUED_WIND_USE";
            return;
        }
    }

    private boolean hasLegitInputLease(long attemptId) {
        return vanillaInput.hasLease(attemptId);
    }

    /* END LEGIT EXECUTION */

    private boolean launchJointShot(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            Rotation target,
            String label,
            boolean debug,
            PearlCatchConfig config
    ) {
        ItemLocation pearlLocation = resolveItemLocation(player, Items.ENDER_PEARL);
        ItemLocation windLocation = resolveItemLocation(player, Items.WIND_CHARGE);
        if (pearlLocation == null || windLocation == null) {
            String reason = pearlLocation == null ? "NO_ENDER_PEARL_AVAILABLE" : "NO_WIND_CHARGE_AVAILABLE";
            if (debug) recordUnlaunchedShot(player, target, label, reason, config);
            player.sendSystemMessage(Component.literal("Pearl Catcher: "
                    + (pearlLocation == null ? "no ender pearl" : "no wind charge") + " in hotbar/offhand."));
            return false;
        }

        Vec3 launchEyeMc = player.getEyePosition();
        Vec3d launchEye = toCore(launchEyeMc);
        Vec3d inherited = currentInheritedMotion(player);
        GeneralCatchSolver.Plan plan = solveGeneral(
                launchEye, inherited, null, 0, launchEye, inherited, target, config);
        if (plan == null || plan.pearlRotation() == null) {
            if (debug) recordUnlaunchedShot(player, target, label, "NO_SAFE_PHYSICAL_INTERCEPT", config);
            if (!debug || config.debugChat) player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: no sufficiently robust pearl/wind intercept for this crosshair ray."));
            return false;
        }

        if (config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA) {
            Rotation actualCamera = new Rotation(player.getYRot(), player.getXRot());
            double pearlDelta = angleDistance(actualCamera, plan.pearlRotation());
            double windDelta = plan.windDelayTicksFromNow() == 0 ? angleDistance(actualCamera, plan.windRotation()) : 0.0;
            if (pearlDelta > 0.35 || windDelta > 0.35) {
                if (debug) recordUnlaunchedShot(player, target, label, "CURRENT_CAMERA_CANNOT_EXECUTE_GENERAL_PLAN", config);
                if (!debug || config.debugChat) player.sendSystemMessage(Component.literal(
                        "Pearl Catcher: current-camera mode cannot execute this physical catch."));
                return false;
            }
        }

        List<Vec3> predictedPearl = predictPearl(launchEyeMc, plan.pearlRotation(), inherited, plan.pearlCatchTick());
        Vec3d nominalPearlLaunch = VanillaProjectilePhysics.nominalLaunchVelocity(plan.pearlRotation(), inherited);
        WindCharge existingWindHazard = firstExistingWindHazard(
                level, launchEye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                nominalPearlLaunch, plan.pearlCatchTick());
        if (existingWindHazard != null) {
            if (debug) recordUnlaunchedShot(player, target, label, "OLDER_WIND_INTERSECTS_NEW_PEARL_PATH", config);
            if (!debug || config.debugChat) player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: older wind charge #" + existingWindHazard.getId()
                            + " crosses this new pearl path; retry after it clears."));
            return false;
        }

        RuntimePathSafety.Result pearlSafety = RuntimePathSafety.checkPearl(level, player, predictedPearl);
        if (!pearlSafety.safe()) {
            if (debug) recordUnlaunchedShot(player, target, label, pearlSafety.reason(), config);
            if (!debug || config.debugChat) player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: planned pearl path is not runtime-safe (" + pearlSafety.reason() + ")."));
            return false;
        }

        Set<Integer> existingPearls = entityIds(level, ThrownEnderpearl.class);
        Set<Integer> existingWinds = entityIds(level, WindCharge.class);
        long attemptId = nextAttemptId++;
        int previousSlot = player.getInventory().getSelectedSlot();
        float cameraYaw = player.getYRot();
        float cameraPitch = player.getXRot();

        if (plan.windDelayTicksFromNow() == 0) {
            List<Vec3> predictedWind = predictWind(launchEyeMc, plan.windRotation(), inherited, plan.windCompletedTicksAtCatch());
            RuntimePathSafety.Result windSafety = RuntimePathSafety.checkWind(level, player, predictedWind);
            if (!windSafety.safe()) {
                if (debug) recordUnlaunchedShot(player, target, label, windSafety.reason(), config);
                if (!debug || config.debugChat) player.sendSystemMessage(Component.literal(
                        "Pearl Catcher: planned wind path is not runtime-safe (" + windSafety.reason() + ")."));
                return false;
            }

            UseResult use = executeImmediateUses(mc, player, plan, target, config,
                    previousSlot, cameraYaw, cameraPitch);
            if (!use.pearlUsed() || !use.windUsed()) {
                if (debug) recordUnlaunchedShot(player, target, label,
                        use.pearlUsed() ? "WIND_USE_FAILED" : "PEARL_USE_FAILED", config);
                player.sendSystemMessage(Component.literal("Pearl Catcher: vanilla item use failed."));
                return false;
            }

            ShotTrace export = ShotTrace.launched(label, "THREW_PEARL_AND_WIND", target, player, plan,
                    predictedPearl, predictedWind, clientTick);
            export.activeAttemptCountAtLaunch = activeShots.size() + pendingCatches.size() + 1;
            export.itemSwitchMode = config.itemSwitchMode.name();
            export.resolvedHand = pearlLocation.hand().name();
            export.resolvedSlot = pearlLocation.slot() >= 0 ? pearlLocation.slot() : null;
            export.minimumExecutableWindDelay = 0;
            export.solverInheritedMovementAtLaunch = vecTrace(fromCore(inherited));
            export.clientKnownMovementAtLaunch = vecTrace(player.getKnownMovement());
            activeShots.add(new TrackingShot(
                    attemptId, label, debug, clientTick, target, plan, "IMMEDIATE", previousSlot,
                    existingPearls, existingWinds, predictedPearl, predictedWind, export
            ));
            if (debug && traceSession != null) traceSession.shots.add(export);
            announcePlan(player, debug, config, plan, "throw now");
            return true;
        }

        boolean pearlUsed = executeFastSingleUse(mc, player, Items.ENDER_PEARL, plan.pearlRotation(), target,
                config, previousSlot, cameraYaw, cameraPitch);
        if (!pearlUsed) {
            if (debug) recordUnlaunchedShot(player, target, label, "PEARL_USE_FAILED", config);
            player.sendSystemMessage(Component.literal("Pearl Catcher: vanilla pearl use failed."));
            return false;
        }

        ShotTrace export = ShotTrace.pending(label, target, player, plan, predictedPearl, clientTick);
        export.activeAttemptCountAtLaunch = activeShots.size() + pendingCatches.size() + 1;
        export.itemSwitchMode = config.itemSwitchMode.name();
        export.resolvedHand = pearlLocation.hand().name();
        export.resolvedSlot = pearlLocation.slot() >= 0 ? pearlLocation.slot() : null;
        export.minimumExecutableWindDelay = 0;
        export.solverInheritedMovementAtLaunch = vecTrace(fromCore(inherited));
        export.clientKnownMovementAtLaunch = vecTrace(player.getKnownMovement());
        PendingCatch pending = new PendingCatch(
                label, debug, clientTick, target, plan.pearlRotation(), previousSlot,
                existingPearls, existingWinds, launchEyeMc, inherited, predictedPearl, export
        );
        pending.attemptId = attemptId;
        pending.serverRotationNeedsRestore = config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET;
        pendingCatches.add(pending);
        if (debug && traceSession != null) traceSession.shots.add(export);
        announcePlan(player, debug, config, plan, "wait " + plan.windDelayTicksFromNow() + "t");
        return true;
    }

    private static GeneralCatchSolver.Plan solveGeneral(
            Vec3d pearlLaunchEye,
            Vec3d pearlInherited,
            Vec3d knownPearlLaunchVelocity,
            int completedPearlTicks,
            Vec3d currentEye,
            Vec3d currentInherited,
            Rotation target,
            PearlCatchConfig config
    ) {
        return solveGeneral(pearlLaunchEye, pearlInherited, knownPearlLaunchVelocity, completedPearlTicks,
                currentEye, currentInherited, target, config, 0);
    }

    private static GeneralCatchSolver.Plan solveGeneral(
            Vec3d pearlLaunchEye,
            Vec3d pearlInherited,
            Vec3d knownPearlLaunchVelocity,
            int completedPearlTicks,
            Vec3d currentEye,
            Vec3d currentInherited,
            Rotation target,
            PearlCatchConfig config,
            int minimumWindDelayTicks
    ) {
        Vec3d pearlLaunchPosition = pearlLaunchEye.add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0);
        return GeneralCatchSolver.solveExecutable(new GeneralCatchSolver.Request(
                pearlLaunchPosition,
                pearlInherited,
                knownPearlLaunchVelocity,
                completedPearlTicks,
                currentEye,
                currentInherited,
                target,
                solverHorizon(config),
                config.solverSearchDistance(),
                config.maxCrosshairDistance,
                config.targetCatchDistance,
                64,
                Math.max(0, minimumWindDelayTicks)
        ));
    }

    private static ServerTimingWindow serverTimingWindow(LocalPlayer player) {
        var info = player.connection.getPlayerInfo(player.getUUID());
        int latencyMs = info == null ? -1 : info.getLatency();
        return ServerTimingWindow.fromRoundTripLatencyMs(latencyMs);
    }

    private static GeneralCatchSolver.Plan solveAcrossServerTimingWindow(
            LocalPlayer player,
            Vec3d pearlLaunchEye,
            Vec3d pearlInherited,
            Vec3d knownPearlLaunchVelocity,
            int completedPearlTicks,
            Vec3d currentEye,
            Vec3d currentInherited,
            Rotation target,
            PearlCatchConfig config
    ) {
        ServerTimingWindow timing = serverTimingWindow(player);
        if (!timing.supported()) return null;

        GeneralCatchSolver.Plan earliest = solveGeneral(
                pearlLaunchEye, pearlInherited, knownPearlLaunchVelocity,
                completedPearlTicks + timing.minLeadTicks(),
                currentEye.add(currentInherited.scale(timing.minLeadTicks())),
                currentInherited, target, config);
        GeneralCatchSolver.Plan latest = solveGeneral(
                pearlLaunchEye, pearlInherited, knownPearlLaunchVelocity,
                completedPearlTicks + timing.maxLeadTicks(),
                currentEye.add(currentInherited.scale(timing.maxLeadTicks())),
                currentInherited, target, config);

        if (earliest == null || latest == null) return null;
        if (earliest.windDelayTicksFromNow() != 0 || latest.windDelayTicksFromNow() != 0) return null;
        if (earliest.pearlCatchTick() != latest.pearlCatchTick()) return null;
        if (angleDistance(earliest.windRotation(), latest.windRotation()) > NETWORK_TIMING_ROTATION_TOLERANCE_DEGREES) {
            return null;
        }
        return earliest;
    }

    private static int solverHorizon(PearlCatchConfig config) {
        int distanceDriven = (int)Math.ceil(config.targetCatchDistance) + 12;
        return Math.min(120, Math.max(24, Math.max(config.maxPredictionTicks, distanceDriven)));
    }



    private Vec3d currentInheritedMotion(LocalPlayer player) {
        ensureMovementEstimatorPlayer(player);
        Vec3d serverKnown = serverMovementEstimator.estimateAtPosition(toCore(player.position()));
        return VanillaProjectilePhysics.inheritedMotion(serverKnown, player.onGround());
    }

    private void ensureMovementEstimatorPlayer(LocalPlayer player) {
        if (movementEstimatorPlayerId != player.getId()) {
            serverMovementEstimator.reset();
            movementEstimatorPlayerId = player.getId();
        }
    }

    private void resetMovementEstimator() {
        serverMovementEstimator.reset();
        movementEstimatorPlayerId = Integer.MIN_VALUE;
    }

    private void cancelOwner(
            Minecraft mc, LocalPlayer player, PearlCatchConfig config, long attemptId, String reason
    ) {
        vanillaInput.cancelOwner(attemptId);
        legitRestores.removeIf(restore -> restore.attemptId == attemptId);

        for (LegitPearlLaunch launch : new ArrayList<>(legitPearlLaunches)) {
            if (launch.attemptId != attemptId) continue;
            if (launch.serverRotationNeedsRestore && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
                restoreServerRotationAfterFinalUse(player, player.getYRot(), player.getXRot());
            }
            launch.trace.finishReason = reason;
            legitPearlLaunches.remove(launch);
        }
        for (PendingCatch pending : new ArrayList<>(pendingCatches)) {
            if (pending.attemptId != attemptId) continue;
            if (pending.serverRotationNeedsRestore && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
                restoreServerRotationAfterFinalUse(player, player.getYRot(), player.getXRot());
            }
            pending.trace.finishReason = reason;
            pendingCatches.remove(pending);
        }
        activeShots.removeIf(shot -> shot.attemptId == attemptId);
    }

    private void cancelAllOwnedState(Minecraft mc, LocalPlayer player, PearlCatchConfig config, String reason) {
        List<Long> owners = new ArrayList<>();
        for (LegitPearlLaunch launch : legitPearlLaunches) owners.add(launch.attemptId);
        for (PendingCatch pending : pendingCatches) owners.add(pending.attemptId);
        for (TrackingShot shot : activeShots) owners.add(shot.attemptId);
        for (LegitRestore restore : legitRestores) owners.add(restore.attemptId);
        if (vanillaInput.hasLease()) owners.add(vanillaInput.leaseOwnerAttemptId());
        for (long owner : owners.stream().distinct().toList()) cancelOwner(mc, player, config, owner, reason);

        if (pendingCameraRestore != null) {
            player.setYRot(pendingCameraRestore.yaw());
            player.setXRot(pendingCameraRestore.pitch());
            pendingCameraRestore = null;
        }
        vanillaInput.cancelAll();
        LegitSilentUseBridge.cancel();
        legitRestores.clear();
        activeShots.clear();
        pendingCatches.clear();
        legitPearlLaunches.clear();
        if (sweep != null) {
            player.setYRot(sweep.originalYaw);
            player.setXRot(sweep.originalPitch);
        }
        sweep = null;
        traceSession = null;
    }

    private void resetExecutionState() {
        activeShots.clear();
        pendingCatches.clear();
        legitPearlLaunches.clear();
        legitRestores.clear();
        vanillaInput.cancelAll();
        pendingCameraRestore = null;
        sweep = null;
        traceSession = null;
        LegitSilentUseBridge.cancel();
        resetMovementEstimator();
        executionLevel = null;
        executionPlayerId = Integer.MIN_VALUE;
    }

    /** Earliest practical hook for learning actual randomized projectile state. */
    public void onEntityLoaded(Minecraft mc, Entity entity, ClientLevel level, PearlCatchConfig config) {
        if (mc == null || mc.player == null || mc.level != level) return;

        if (entity instanceof ThrownEnderpearl pearl) {
            if (!ProjectileTracker.isOwnedByLocal(pearl, mc.player)) return;
            if (isPearlClaimed(pearl.getId(), pendingCatches, activeShots)) return;

            LegitPearlLaunch legitLaunch = legitPearlLaunches.stream()
                    .filter(l -> l.waitingForPearl && l.launchEye != null)
                    .filter(l -> !l.existingPearls.contains(pearl.getId()))
                    .filter(l -> pearl.position().distanceToSqr(l.launchEye) <= 256.0)
                    .min(Comparator.comparingLong(l -> l.startClientTick))
                    .orElse(null);
            if (legitLaunch != null) {
                PendingCatch pending = new PendingCatch(
                        legitLaunch.label, legitLaunch.debug, legitLaunch.startClientTick, legitLaunch.target,
                        legitLaunch.commandedPearlRotation, legitLaunch.previousSlot,
                        legitLaunch.existingPearls, legitLaunch.existingWinds, legitLaunch.launchEye,
                        legitLaunch.launchInheritedMotion, legitLaunch.predictedPearl, legitLaunch.trace
                );
                pending.attemptId = legitLaunch.attemptId;
                pending.legit = true;
                pending.serverRotationNeedsRestore = legitLaunch.serverRotationNeedsRestore;
                pending.pearlId = pearl.getId();
                pending.pearlSeenClientTick = clientTick;
                pending.executorOwnedSlot = mc.player.getInventory().getSelectedSlot();
                pending.pearlOffhandRestoreNeeded = legitLaunch.pearlOffhandSwapped;
                pending.pearlSwapSlot = legitLaunch.pearlSwapSlot;
                pending.pearlSwapOriginalSelectedItem = legitLaunch.pearlSwapOriginalSelectedItem;
                pending.trace.pearlEntityId = pearl.getId();
                pending.trace.projectileObservedClientTick = clientTick;
                pendingCatches.add(pending);
                legitPearlLaunches.remove(legitLaunch);
                replanPendingCatch(mc, mc.player, level, pending, pearl, config, true);
                return;
            }

            PendingCatch pending = pendingCatches.stream()
                    .filter(p -> !p.windUsed && !p.windUseRequested && p.pearlId < 0)
                    .filter(p -> !p.existingPearls.contains(pearl.getId()))
                    .filter(p -> pearl.position().distanceToSqr(p.launchEye) <= 256.0)
                    .min(Comparator.comparingLong(p -> p.startClientTick))
                    .orElse(null);
            if (pending == null) return;

            pending.pearlId = pearl.getId();
            pending.pearlSeenClientTick = clientTick;
            pending.trace.pearlEntityId = pearl.getId();
            pending.trace.projectileObservedClientTick = clientTick;
            replanPendingCatch(mc, mc.player, level, pending, pearl, config, true);
            return;
        }

        if (entity instanceof WindCharge wind) {
            if (!ProjectileTracker.isOwnedByLocal(wind, mc.player)) return;
            PendingCatch pending = pendingCatches.stream()
                    .filter(p -> p.legit && p.windUseRequested && p.windId < 0)
                    .filter(p -> !p.existingWinds.contains(wind.getId()))
                    .filter(p -> wind.position().distanceToSqr(mc.player.getEyePosition()) <= 256.0)
                    .min(Comparator.comparingLong(p -> p.windUseRequestedClientTick))
                    .orElse(null);
            if (pending != null) {
                pending.windId = wind.getId();
                pending.trace.windEntityId = wind.getId();
                completeLegitWindObservation(mc, mc.player, level, pending, wind, config);
            }
        }
    }

    private void tickPendingCatch(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            PendingCatch pending,
            PearlCatchConfig config
    ) {
        if (!pendingCatches.contains(pending)) return;
        pending.ageTicks++;

        if (pending.legit && pending.windUseRequested) {
            WindCharge wind = pending.windId >= 0
                    ? ProjectileTracker.findOwnedById(level, player, pending.windId, WindCharge.class)
                    : ProjectileTracker.findNewOwned(level, player, combinedWindExclusions(pending), WindCharge.class, player.getEyePosition());
            if (wind != null) {
                pending.windId = wind.getId();
                pending.trace.windEntityId = wind.getId();
                completeLegitWindObservation(mc, player, level, pending, wind, config);
                return;
            }
            if (clientTick - pending.windUseRequestedClientTick > Math.max(12, solverHorizon(config))) {
                finishPendingCatch(player, config, pending, "WIND_ENTITY_TIMEOUT_AFTER_LEGIT_USE");
            }
            return;
        }

        ThrownEnderpearl pearl = null;
        if (pending.pearlId < 0) {
            Set<Integer> excluded = new HashSet<>(pending.existingPearls);
            excluded.addAll(claimedPearlIds(pendingCatches, activeShots));
            pearl = ProjectileTracker.findNewOwned(level, player, excluded, ThrownEnderpearl.class, pending.launchEye);
            if (pearl != null) {
                pending.pearlId = pearl.getId();
                pending.pearlSeenClientTick = clientTick;
                pending.trace.pearlEntityId = pearl.getId();
            }
        } else {
            pearl = ProjectileTracker.findOwnedById(level, player, pending.pearlId, ThrownEnderpearl.class);
        }

        if (pearl != null) {
            replanPendingCatch(mc, player, level, pending, pearl, config, false);
            return;
        }

        if (pending.pearlId >= 0 && clientTick - pending.pearlSeenClientTick > 2) {
            finishPendingCatch(player, config, pending, "PEARL_DISAPPEARED_BEFORE_WIND_USE");
            return;
        }
        if (pending.ageTicks >= solverHorizon(config)) {
            finishPendingCatch(player, config, pending, "PEARL_ENTITY_TIMEOUT_BEFORE_WIND_USE");
        }
    }

    private Set<Integer> combinedWindExclusions(PendingCatch pending) {
        Set<Integer> excluded = new HashSet<>(pending.existingWinds);
        excluded.addAll(claimedWindIds(activeShots));
        return excluded;
    }

    private boolean restoreLegitPearlOffhandIfNeeded(
            Minecraft mc, LocalPlayer player, PendingCatch pending
    ) {
        if (!pending.pearlOffhandRestoreNeeded) return true;
        if (mc.screen != null || mc.getOverlay() != null) {
            pending.trace.action = "WAIT_SCREEN_BEFORE_PEARL_OFFHAND_RESTORE";
            return false;
        }
        if (pending.pearlSwapSlot < 0 || player.getInventory().getSelectedSlot() != pending.pearlSwapSlot) {
            // The user changed slots after our swap. Do not swap an unrelated slot into offhand.
            pending.pearlOffhandRestoreNeeded = false;
            pending.trace.action = "SKIP_PEARL_OFFHAND_RESTORE_MANUAL_SLOT_CHANGE";
            return true;
        }
        if (pending.pearlSwapOriginalSelectedItem != null
                && mainHandMatchesExpected(player, pending.pearlSwapOriginalSelectedItem)) {
            pending.pearlOffhandRestoreNeeded = false;
            pending.pearlRestoreSwapRequested = false;
            return true;
        }
        if (hasLegitInputLease(pending.attemptId) || vanillaInput.hasLease()) return false;
        if (queueLegitSwap(mc, pending.attemptId, pending.pearlSwapOriginalSelectedItem, pending.pearlSwapSlot)) {
            pending.pearlRestoreSwapRequested = true;
            pending.trace.action = "LEGIT_RESTORE_PEARL_OFFHAND";
        }
        return false;
    }

    /**
     * Prepare wind in main hand using only configured vanilla keys.
     * Return 0 when Use can be queued for the next handleKeybinds pass, 1 while one or more preparation
     * steps are still outstanding, or -1 when the wind charge disappeared.
     */
    private int prepareLegitWindItem(Minecraft mc, LocalPlayer player, PendingCatch pending) {
        if (mc.screen != null || mc.getOverlay() != null) {
            pending.trace.action = "WAIT_SCREEN_BLOCKS_LEGIT_WIND_PREP";
            return 1;
        }
        if (selectedMainIs(player, Items.WIND_CHARGE)) {
            if (pending.trace.windItemPrepConfirmedClientTick == null) {
                pending.trace.windItemPrepConfirmedClientTick = clientTick;
            }
            if (pending.windOffhandSwapRequested) pending.windOffhandSwapped = true;
            pending.executorOwnedSlot = player.getInventory().getSelectedSlot();
            pending.trace.resolvedHand = InteractionHand.MAIN_HAND.name();
            pending.trace.resolvedSlot = pending.executorOwnedSlot;
            return 0;
        }
        if (hasLegitInputLease(pending.attemptId) || vanillaInput.hasLease()) return 1;

        ItemLocation wind = resolveItemLocation(player, Items.WIND_CHARGE);
        if (wind == null) return -1;
        if (wind.hand() == InteractionHand.OFF_HAND) {
            pending.windOffhandSwapRequested = true;
            pending.windSwapSlot = player.getInventory().getSelectedSlot();
            pending.windSwapOriginalSelectedItem = player.getMainHandItem().getItem();
            pending.executorOwnedSlot = pending.windSwapSlot;
            if (queueLegitSwap(mc, pending.attemptId, Items.WIND_CHARGE, pending.windSwapSlot)) {
                pending.trace.windItemPrepRequestedClientTick = clientTick;
                pending.trace.action = "LEGIT_SWAP_WIND_FROM_OFFHAND";
                pending.trace.resolvedHand = InteractionHand.OFF_HAND.name();
            }
            return 1;
        }
        pending.executorOwnedSlot = wind.slot();
        if (queueLegitHotbar(mc, pending.attemptId, wind.slot())) {
            pending.trace.windItemPrepRequestedClientTick = clientTick;
            pending.trace.action = "LEGIT_SELECT_WIND_SLOT";
            pending.trace.resolvedSlot = wind.slot();
        }
        return 1;
    }

    private boolean replanPendingCatch(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            PendingCatch pending,
            ThrownEnderpearl pearl,
            PearlCatchConfig config,
            boolean fromEntityLoadEvent
    ) {
        if (!pendingCatches.contains(pending) || pending.windUsed || pending.windUseRequested) return false;
        pending.solveAttempts++;

        int completedTicks = Math.max(0, pearl.tickCount);
        Vec3d observedVelocity = toCore(pearl.getDeltaMovement());
        Vec3d launchVelocity = VanillaProjectilePhysics.reconstructPearlLaunchVelocity(observedVelocity, completedTicks);
        pending.actualPearlLaunchVelocity = launchVelocity;
        pending.actualPearlObservedEntityTick = completedTicks;
        pending.trace.actualPearlLaunchVelocity = vecTrace(fromCore(launchVelocity));
        Vec3d projectileInferred = VanillaProjectilePhysics.inferInheritedMotion(pending.pearlRotation, launchVelocity);
        pending.trace.projectileInferredInheritedMovement = vecTrace(fromCore(projectileInferred));
        if (pending.trace.solverInheritedMovementAtLaunch != null) {
            Vec3d solverInherited = toCore(new Vec3(
                    pending.trace.solverInheritedMovementAtLaunch.x(),
                    pending.trace.solverInheritedMovementAtLaunch.y(),
                    pending.trace.solverInheritedMovementAtLaunch.z()));
            pending.trace.projectileMovementEstimateError = projectileInferred.subtract(solverInherited).length();
        }
        pending.trace.actualPearlObservedEntityTick = completedTicks;
        pending.trace.solvedFromEntityLoadEvent |= fromEntityLoadEvent;

        int minimumWindDelay = 0;
        if (pending.legit) {
            if (!restoreLegitPearlOffhandIfNeeded(mc, player, pending)) return false;
            int preparation = prepareLegitWindItem(mc, player, pending);
            if (preparation < 0) {
                finishPendingCatch(player, config, pending, "WIND_CHARGE_MOVED_OR_MISSING");
                return false;
            }
            minimumWindDelay = preparation;
        }
        pending.trace.minimumExecutableWindDelay = minimumWindDelay;

        Vec3 currentEyeMc = player.getEyePosition();
        Vec3d currentEye = toCore(currentEyeMc);
        Vec3d currentInherited = currentInheritedMotion(player);
        Rotation solveTarget = solverTargetForExecution(player, pending.target, pending.label, config.rotationMode);
        GeneralCatchSolver.Plan raw = solveGeneral(
                toCore(pending.launchEye), pending.launchInheritedMotion, launchVelocity, completedTicks,
                currentEye, currentInherited, solveTarget, config, minimumWindDelay);
        if (raw == null) {
            pending.trace.action = "WAIT_NO_CURRENT_SOLUTION";
            if (pending.solveAttempts >= Math.max(12, solverHorizon(config))) {
                finishPendingCatch(player, config, pending, "NO_FUTURE_GENERAL_INTERCEPT");
            }
            return false;
        }

        GeneralCatchSolver.Plan plan = withPearlRotation(raw, pending.pearlRotation);
        pending.latestPlan = plan;
        pending.trace.plan = PlanTrace.from(plan);
        pending.trace.action = plan.windDelayTicksFromNow() == 0
                ? (pending.legit ? "LEGIT_WIND_READY_FOR_USE_TICK" : "THROW_WIND_NOW")
                : "WAIT_" + plan.windDelayTicksFromNow() + "T";

        if (plan.windDelayTicksFromNow() > 0) return false;

        GeneralCatchSolver.Plan timingSafe = solveAcrossServerTimingWindow(
                player, toCore(pending.launchEye), pending.launchInheritedMotion, launchVelocity, completedTicks,
                currentEye, currentInherited, solveTarget, config);
        if (timingSafe == null) {
            pending.trace.action = "WAIT_NETWORK_TIMING_UNCERTAIN";
            return false;
        }
        plan = withPearlRotation(timingSafe, pending.pearlRotation);
        pending.latestPlan = plan;
        pending.trace.plan = PlanTrace.from(plan);

        if (config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA
                && angleDistance(new Rotation(player.getYRot(), player.getXRot()), plan.windRotation()) > 0.35) {
            pending.trace.action = "WAIT_CURRENT_CAMERA_WIND_CONSTRAINT";
            return false;
        }

        List<Vec3> predictedPearl = predictPearlFromVelocity(
                toCore(pending.launchEye).add(0.0, VanillaProjectilePhysics.PEARL_SPAWN_Y_OFFSET, 0.0),
                launchVelocity, plan.pearlCatchTick());
        ServerTimingWindow timingWindow = serverTimingWindow(player);
        if (!timingWindow.supported()) {
            pending.trace.action = "WAIT_NETWORK_TIMING_UNCERTAIN";
            return false;
        }
        Vec3 earliestWindStart = fromCore(currentEye.add(currentInherited.scale(timingWindow.minLeadTicks())));
        Vec3 latestWindStart = fromCore(currentEye.add(currentInherited.scale(timingWindow.maxLeadTicks())));
        List<Vec3> predictedWind = predictWind(earliestWindStart, plan.windRotation(), currentInherited,
                plan.windCompletedTicksAtCatch());
        List<Vec3> latestPredictedWind = predictWind(latestWindStart, plan.windRotation(), currentInherited,
                plan.windCompletedTicksAtCatch());
        int futurePathStart = Math.min(completedTicks, Math.max(0, predictedPearl.size() - 1));
        List<Vec3> futurePearlPath = predictedPearl.subList(futurePathStart, predictedPearl.size());
        RuntimePathSafety.Result pearlSafety = RuntimePathSafety.checkPearl(level, player, futurePearlPath, completedTicks);
        if (!pearlSafety.safe()) {
            pending.trace.action = "WAIT_PEARL_PATH_UNSAFE_" + pearlSafety.reason();
            return false;
        }
        RuntimePathSafety.Result windSafety = RuntimePathSafety.checkWind(level, player, predictedWind);
        RuntimePathSafety.Result latestWindSafety = RuntimePathSafety.checkWind(level, player, latestPredictedWind);
        if (!windSafety.safe() || !latestWindSafety.safe()) {
            String reason = !windSafety.safe() ? windSafety.reason() : latestWindSafety.reason();
            pending.trace.action = "WAIT_WIND_PATH_UNSAFE_" + reason;
            return false;
        }

        pending.trace.predictedPearlTrajectory.clear();
        pending.trace.predictedWindTrajectory.clear();
        for (Vec3 p : predictedPearl) pending.trace.predictedPearlTrajectory.add(vecTrace(p));
        for (Vec3 p : predictedWind) pending.trace.predictedWindTrajectory.add(vecTrace(p));

        if (pending.legit) {
            ItemLocation readyWind = resolveSelectedMainLocation(player, Items.WIND_CHARGE);
            if (readyWind == null) {
                pending.trace.action = "WAIT_WIND_MAIN_HAND_CONFIRMATION";
                return false;
            }
            if (isOnCooldown(player, readyWind)) {
                pending.trace.action = "WAIT_WIND_COOLDOWN";
                return false;
            }
            if (player.isUsingItem()) {
                pending.trace.action = "WAIT_PLAYER_USING_ITEM_BEFORE_WIND";
                return false;
            }
            if (vanillaInput.hasLease()) return false;

            Rotation useRotation = config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA
                    ? new Rotation(player.getYRot(), player.getXRot()) : plan.windRotation();
            if (!queueLegitUse(mc, player, pending.attemptId, Items.WIND_CHARGE, useRotation, true, config)) {
                pending.trace.action = "WAIT_WIND_USE_KEY_UNAVAILABLE";
                return false;
            }
            pending.windUseRequested = true;
            pending.windUseRequestedClientTick = clientTick;
            pending.queuedWindRotation = plan.windRotation();
            pending.trace.attemptedWindThrowShotTick = completedTicks;
            pending.trace.attemptedWindThrowClientTick = clientTick;
            pending.trace.useRequestedClientTick = clientTick;
            pending.trace.action = "LEGIT_WIND_USE_REQUESTED";
            return true;
        }

        float cameraYaw = player.getYRot();
        float cameraPitch = player.getXRot();
        int windActionPreviousSlot = player.getInventory().getSelectedSlot();
        boolean windUsed = executeFastSingleUse(mc, player, Items.WIND_CHARGE, plan.windRotation(), pending.target,
                config, windActionPreviousSlot, cameraYaw, cameraPitch);
        if (!windUsed) {
            finishPendingCatch(player, config, pending, "WIND_USE_FAILED");
            return false;
        }
        pending.windUsed = true;
        if (pending.serverRotationNeedsRestore && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            restoreServerRotationAfterFinalUse(player, cameraYaw, cameraPitch);
            pending.serverRotationNeedsRestore = false;
        }
        pending.trace.attemptedWindThrowShotTick = completedTicks;
        pending.trace.attemptedWindThrowClientTick = clientTick;
        pending.trace.plan = PlanTrace.from(plan);
        pending.trace.action = "THREW_WIND";

        TrackingShot shot = new TrackingShot(
                pending.attemptId, pending.label, pending.debug, pending.startClientTick, pending.target, plan,
                "DELAYED_FAST", pending.previousSlot, pending.existingPearls, pending.existingWinds,
                predictedPearl, predictedWind, pending.trace
        );
        shot.pearlId = pearl.getId();
        shot.pearlSeen = true;
        shot.ageTicks = completedTicks;
        shot.lastObservedPearlEntityTick = completedTicks;
        pending.trace.pearlEntityId = pearl.getId();
        activeShots.add(shot);
        pendingCatches.remove(pending);

        if (config.debugChat || !pending.debug) {
            player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: re-solved real pearl → wind " + rot(plan.windRotation())
                            + " | target " + fmt(config.targetCatchDistance) + "b → " + fmt(plan.crosshairRange()) + "b"
                            + " | delay=0 | clearance " + fmt(plan.collisionClearance()) + "b"
                            + " | reliability " + fmt(plan.robustHitFraction() * 100.0) + "%"
            ));
        }
        return true;
    }

    private void completeLegitWindObservation(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            PendingCatch pending,
            WindCharge wind,
            PearlCatchConfig config
    ) {
        if (!pendingCatches.contains(pending) || !pending.windUseRequested || pending.latestPlan == null) return;
        ThrownEnderpearl pearl = ProjectileTracker.findOwnedById(level, player, pending.pearlId, ThrownEnderpearl.class);
        int completedTicks = pearl != null ? Math.max(0, pearl.tickCount) : Math.max(0, pending.actualPearlObservedEntityTick);

        pending.windUsed = true;
        pending.serverRotationNeedsRestore = false;
        pending.windId = wind.getId();
        pending.trace.windEntityId = wind.getId();
        pending.trace.projectileObservedClientTick = clientTick;
        pending.trace.action = "LEGIT_WIND_OBSERVED";

        List<Vec3> predictedPearl = new ArrayList<>();
        for (VecTrace point : pending.trace.predictedPearlTrajectory) {
            predictedPearl.add(new Vec3(point.x(), point.y(), point.z()));
        }
        List<Vec3> predictedWind = new ArrayList<>();
        for (VecTrace point : pending.trace.predictedWindTrajectory) {
            predictedWind.add(new Vec3(point.x(), point.y(), point.z()));
        }

        TrackingShot shot = new TrackingShot(
                pending.attemptId, pending.label, pending.debug, pending.startClientTick, pending.target, pending.latestPlan,
                "DELAYED_LEGIT", pending.previousSlot, pending.existingPearls, pending.existingWinds,
                predictedPearl, predictedWind, pending.trace
        );
        shot.pearlId = pending.pearlId;
        shot.windId = wind.getId();
        shot.pearlSeen = pearl != null;
        shot.windSeen = true;
        shot.ageTicks = completedTicks;
        shot.lastObservedPearlEntityTick = completedTicks;
        activeShots.add(shot);
        pendingCatches.remove(pending);

        scheduleLegitRestoreFromPending(player, pending, config);

        if (config.debugChat || !pending.debug) {
            player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: Legit wind observed → catch t=" + pending.latestPlan.pearlCatchTick()
                            + " | clearance " + fmt(pending.latestPlan.collisionClearance()) + "b"));
        }
    }

    private void scheduleLegitRestoreFromPending(
            LocalPlayer player, PendingCatch pending, PearlCatchConfig config
    ) {
        if (!config.autoRestoreSlot) return;
        boolean restoreOffhand = pending.windOffhandSwapped && pending.windSwapSlot >= 0;
        int ownedSlot = pending.executorOwnedSlot >= 0 ? pending.executorOwnedSlot : player.getInventory().getSelectedSlot();
        legitRestores.add(new LegitRestore(
                pending.attemptId, pending.previousSlot, ownedSlot,
                restoreOffhand, pending.windSwapSlot, pending.windSwapOriginalSelectedItem
        ));
    }

    private void tickLegitRestore(
            Minecraft mc, LocalPlayer player, LegitRestore restore, PearlCatchConfig config
    ) {
        if (!legitRestores.contains(restore)) return;
        if (!config.autoRestoreSlot) {
            legitRestores.remove(restore);
            return;
        }
        if (mc.screen != null || mc.getOverlay() != null || vanillaInput.hasLease()) return;

        if (restore.restoreOffhand && !restore.offhandRestored) {
            if (player.getInventory().getSelectedSlot() != restore.swapSlot) {
                // User or a newer attempt changed slots. Never swap a different slot into offhand.
                legitRestores.remove(restore);
                return;
            }
            if (restore.originalSelectedItem != null && mainHandMatchesExpected(player, restore.originalSelectedItem)) {
                restore.offhandRestored = true;
            } else {
                if (queueLegitSwap(mc, restore.attemptId, restore.originalSelectedItem, restore.swapSlot)) {
                    restore.swapRequested = true;
                }
                return;
            }
        }

        if (restore.previousSlot == player.getInventory().getSelectedSlot()) {
            legitRestores.remove(restore);
            return;
        }
        if (player.getInventory().getSelectedSlot() != restore.ownedSlot
                && (!restore.restoreOffhand || player.getInventory().getSelectedSlot() != restore.swapSlot)) {
            // Manual/newer selection wins over restoration.
            legitRestores.remove(restore);
            return;
        }
        if (queueLegitHotbar(mc, restore.attemptId, restore.previousSlot)) {
            restore.ownedSlot = restore.previousSlot;
            restore.slotRestoreRequested = true;
        }
    }

    private static GeneralCatchSolver.Plan withPearlRotation(GeneralCatchSolver.Plan p, Rotation pearlRotation) {
        if (p.pearlRotation() != null) return p;
        return new GeneralCatchSolver.Plan(
                pearlRotation, p.windRotation(), p.windDelayTicksFromNow(), p.pearlCatchTick(),
                p.windCompletedTicksAtCatch(), p.firstWindPearlSegment(), p.interceptPoint(),
                p.windPositionAtCatch(), p.crosshairDistance(), p.crosshairRange(), p.targetDistanceError(),
                p.collisionClearance(), p.firstCollisionTick(), p.robustHitFraction(), p.score(), p.pearlLaunchKnown()
        );
    }

    private void finishPendingCatch(
            LocalPlayer player,
            PearlCatchConfig config,
            PendingCatch pending,
            String reason
    ) {
        if (!pendingCatches.contains(pending)) return;
        if (pending.serverRotationNeedsRestore && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            restoreServerRotationAfterFinalUse(player, player.getYRot(), player.getXRot());
            pending.serverRotationNeedsRestore = false;
        }
        if (pending.legit) {
            vanillaInput.cancelOwner(pending.attemptId);
            if (config.autoRestoreSlot) {
                boolean restoreWindOffhand = pending.windOffhandSwapped && pending.windSwapSlot >= 0;
                boolean restorePearlOffhand = !restoreWindOffhand
                        && pending.pearlOffhandRestoreNeeded && pending.pearlSwapSlot >= 0;
                int swapSlot = restoreWindOffhand ? pending.windSwapSlot
                        : (restorePearlOffhand ? pending.pearlSwapSlot : -1);
                Item original = restoreWindOffhand ? pending.windSwapOriginalSelectedItem
                        : (restorePearlOffhand ? pending.pearlSwapOriginalSelectedItem : null);
                int ownedSlot = swapSlot >= 0 ? swapSlot
                        : (pending.executorOwnedSlot >= 0 ? pending.executorOwnedSlot : player.getInventory().getSelectedSlot());
                legitRestores.add(new LegitRestore(
                        pending.attemptId, pending.previousSlot, ownedSlot,
                        restoreWindOffhand || restorePearlOffhand, swapSlot, original
                ));
            }
        }
        pending.trace.finishReason = reason;
        if (pending.debug && traceSession != null) traceSession.write();
        if (config.debugChat || !pending.debug) {
            player.sendSystemMessage(Component.literal("Pearl Catcher pending result: " + reason));
        }
        pendingCatches.remove(pending);
        if (pending.debug && sweep != null && sweep.active()) sweep.waitTicks = config.debugBetweenShotsTicks;
    }

    private UseResult executeImmediateUses(
            Minecraft mc,
            LocalPlayer player,
            GeneralCatchSolver.Plan plan,
            Rotation target,
            PearlCatchConfig config,
            int previousSlot,
            float cameraYaw,
            float cameraPitch
    ) {
        boolean pearlUsed = false;
        boolean windUsed = false;
        int executorSelectedSlot = previousSlot;
        try {
            ItemLocation pearl = resolveItemLocation(player, Items.ENDER_PEARL);
            if (pearl == null || isOnCooldown(player, pearl)) return new UseResult(false, false);
            executorSelectedSlot = selectFastLocation(player, pearl, executorSelectedSlot);
            pearlUsed = useHandAtRotation(mc, player, pearl.hand(),
                    config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA ? target : plan.pearlRotation(),
                    config.rotationMode);
            if (!pearlUsed) return new UseResult(false, false);

            ItemLocation wind = resolveItemLocation(player, Items.WIND_CHARGE);
            if (wind == null || isOnCooldown(player, wind)) return new UseResult(true, false);
            executorSelectedSlot = selectFastLocation(player, wind, executorSelectedSlot);
            windUsed = useHandAtRotation(mc, player, wind.hand(),
                    config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA ? target : plan.windRotation(),
                    config.rotationMode);
            return new UseResult(true, windUsed);
        } finally {
            restoreAfterUse(player, config, previousSlot, executorSelectedSlot, cameraYaw, cameraPitch);
            if (pearlUsed && config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
                restoreServerRotationAfterFinalUse(player, cameraYaw, cameraPitch);
            }
        }
    }

    private boolean executeFastSingleUse(
            Minecraft mc,
            LocalPlayer player,
            Item requiredItem,
            Rotation rotation,
            Rotation target,
            PearlCatchConfig config,
            int restoreSlot,
            float cameraYaw,
            float cameraPitch
    ) {
        int executorSelectedSlot = player.getInventory().getSelectedSlot();
        try {
            ItemLocation location = resolveItemLocation(player, requiredItem);
            if (location == null || isOnCooldown(player, location)) return false;
            executorSelectedSlot = selectFastLocation(player, location, executorSelectedSlot);
            return useHandAtRotation(mc, player, location.hand(),
                    config.rotationMode == PearlCatchConfig.RotationMode.CURRENT_CAMERA ? target : rotation,
                    config.rotationMode);
        } finally {
            restoreAfterUse(player, config, restoreSlot, executorSelectedSlot, cameraYaw, cameraPitch);
        }
    }

    private void restoreAfterUse(
            LocalPlayer player,
            PearlCatchConfig config,
            int previousSlot,
            int executorSelectedSlot,
            float cameraYaw,
            float cameraPitch
    ) {
        if (config.autoRestoreSlot && player.getInventory().getSelectedSlot() == executorSelectedSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
        if (config.rotationMode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            // Local camera restoration only. A standalone return Rot packet can zero server-known movement
            // before a later wind throw on fast-moving players, so 2.5 never emits one.
            player.setYRot(cameraYaw);
            player.setXRot(cameraPitch);
        } else if (config.rotationMode == PearlCatchConfig.RotationMode.VISIBLE_CAMERA) {
            pendingCameraRestore = new CameraRestore(clientTick + 1, cameraYaw, cameraPitch);
        }
    }

    private static void restoreServerRotationAfterFinalUse(LocalPlayer player, float yaw, float pitch) {
        if (player == null || player.connection == null) return;
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                yaw, pitch, player.onGround(), player.horizontalCollision));
    }

    private static void announcePlan(
            LocalPlayer player, boolean debug, PearlCatchConfig config, GeneralCatchSolver.Plan plan, String action
    ) {
        if (config.debugChat || !debug) {
            player.sendSystemMessage(Component.literal(
                    "Pearl Catcher: target " + fmt(config.targetCatchDistance) + "b → achievable "
                            + fmt(plan.crosshairRange()) + "b | wind " + action
                            + " | catch t=" + plan.pearlCatchTick()
                            + " | clearance " + fmt(plan.collisionClearance()) + "b"
                            + " | reliability " + fmt(plan.robustHitFraction() * 100.0) + "%"
            ));
        }
    }

    private boolean useHandAtRotation(Minecraft mc, LocalPlayer player, InteractionHand hand, Rotation rotation, PearlCatchConfig.RotationMode mode) {
        float yaw = (float)rotation.yaw();
        float pitch = (float)rotation.pitch();
        if (mode == PearlCatchConfig.RotationMode.SILENT_PACKET) {
            // ServerboundUseItemPacket already carries yaw/pitch and the server snaps to those values before Item#use.
            // Do NOT send a standalone Rot packet here: ServerGamePacketListenerImpl treats a rotation-only movement
            // packet as zero positional movement and overwrites ServerPlayer#lastKnownClientMovement with Vec3.ZERO,
            // which destroys elytra/player momentum inheritance immediately before the projectile spawns.
            float oldYaw = player.getYRot();
            float oldPitch = player.getXRot();
            player.setYRot(yaw);
            player.setXRot(pitch);
            try {
                InteractionResult result = mc.gameMode.useItem(player, hand);
                if (result.consumesAction()) player.swing(hand);
                return result.consumesAction();
            } finally {
                player.setYRot(oldYaw);
                player.setXRot(oldPitch);
            }
        }

        if (mode == PearlCatchConfig.RotationMode.VISIBLE_CAMERA) {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
        InteractionResult result = mc.gameMode.useItem(player, hand);
        if (result.consumesAction()) player.swing(hand);
        return result.consumesAction();
    }

    private void updateTracking(
            Minecraft mc,
            LocalPlayer player,
            ClientLevel level,
            TrackingShot shot,
            PearlCatchConfig config
    ) {
        if (!activeShots.contains(shot)) return;
        shot.ageTicks++;

        if (shot.pearlId < 0) {
            Set<Integer> excluded = new HashSet<>(shot.existingPearls);
            excluded.addAll(claimedPearlIds(pendingCatches, activeShots));
            ThrownEnderpearl p = ProjectileTracker.findNewOwned(level, player, excluded, ThrownEnderpearl.class, shot.startEye);
            if (p != null) {
                shot.pearlId = p.getId();
                shot.pearlSeen = true;
                shot.trace.pearlEntityId = p.getId();
            }
        }
        if (shot.windId < 0) {
            Set<Integer> excluded = new HashSet<>(shot.existingWinds);
            excluded.addAll(claimedWindIds(activeShots));
            WindCharge w = ProjectileTracker.findNewOwned(level, player, excluded, WindCharge.class, shot.startEye);
            if (w != null) {
                shot.windId = w.getId();
                shot.windSeen = true;
                shot.trace.windEntityId = w.getId();
            }
        }

        ThrownEnderpearl pearl = shot.pearlId >= 0 ? ProjectileTracker.findOwnedById(level, player, shot.pearlId, ThrownEnderpearl.class) : null;
        WindCharge wind = shot.windId >= 0 ? ProjectileTracker.findOwnedById(level, player, shot.windId, WindCharge.class) : null;
        if (pearl != null) {
            shot.pearlSeen = true;
            if (shot.trace.actualPearlLaunchVelocity == null && shot.plan.pearlRotation() != null) {
                int completedTicks = Math.max(0, pearl.tickCount);
                Vec3d launchVelocity = VanillaProjectilePhysics.reconstructPearlLaunchVelocity(
                        toCore(pearl.getDeltaMovement()), completedTicks);
                Vec3d projectileInferred = VanillaProjectilePhysics.inferInheritedMotion(shot.plan.pearlRotation(), launchVelocity);
                shot.trace.actualPearlLaunchVelocity = vecTrace(fromCore(launchVelocity));
                shot.trace.projectileInferredInheritedMovement = vecTrace(fromCore(projectileInferred));
                shot.trace.actualPearlObservedEntityTick = completedTicks;
                if (shot.trace.solverInheritedMovementAtLaunch != null) {
                    VecTrace v = shot.trace.solverInheritedMovementAtLaunch;
                    shot.trace.projectileMovementEstimateError = projectileInferred
                            .subtract(new Vec3d(v.x(), v.y(), v.z())).length();
                }
            }
            shot.lastObservedPearlEntityTick = pearl.tickCount;
        }
        if (wind != null) shot.windSeen = true;

        EntityTickTrace pearlTrace = pearl == null ? null : entityTrace(pearl);
        EntityTickTrace windTrace = wind == null ? null : entityTrace(wind);
        PlayerTickTrace playerTrace = playerTrace(player);
        SolverTickTrace solverTrace = solverTrace(shot, pearl, wind);
        shot.trace.ticks.add(new TickTrace(shot.ageTicks, clientTick, playerTrace, pearlTrace, windTrace, solverTrace));
        trim(shot.trace.ticks, config.debugTrailLimit);

        if (pearl != null) {
            Vec3 pos = pearl.position();
            shot.actualPearl.add(pos);
            trim(shot.actualPearl, config.debugTrailLimit);
            shot.lastPearlPosition = pos;
        }
        if (wind != null) {
            Vec3 pos = wind.position();
            shot.actualWind.add(pos);
            trim(shot.actualWind, config.debugTrailLimit);
        }

        if (pearl != null && wind != null) updateClosest(shot, pearl, wind);

        if (shot.pearlSeen && pearl == null) shot.pearlMissingTicks++;
        else shot.pearlMissingTicks = 0;

        if (shot.pearlMissingTicks >= 2) {
            Vec3 plannedIntercept = fromCore(shot.plan.interceptPoint());
            double teleportError = player.position().distanceTo(plannedIntercept);
            boolean timingNearPlan = shot.lastObservedPearlEntityTick >= 0
                    && Math.abs(shot.lastObservedPearlEntityTick - shot.plan.pearlCatchTick()) <= 3;
            String reason;
            if (shot.windSeen && teleportError <= Math.max(4.0, config.maxCrosshairDistance + 2.0)) {
                reason = "LIKELY_CATCH_TELEPORT_NEAR_PLANNED_INTERCEPT";
            } else if (shot.windSeen && timingNearPlan) {
                reason = "LIKELY_CATCH_PEARL_DISAPPEARED_NEAR_PLANNED_TICK";
            } else {
                reason = "PEARL_DISAPPEARED_CLIENT_RESULT_UNCONFIRMED";
            }
            shot.trace.teleportDistanceToPlannedIntercept = finiteOrNull(teleportError);
            finishShot(player, config, shot, reason);
            return;
        }

        if (shot.ageTicks >= config.maxTicksPerPitch) {
            finishShot(player, config, shot, "TIMEOUT_WAITING_FOR_PEARL_TO_DISAPPEAR");
        }
    }


    private void finishShot(LocalPlayer player, PearlCatchConfig config, TrackingShot shot, String reason) {
        if (!activeShots.contains(shot)) return;
        shot.trace.finishReason = reason;
        shot.trace.closest = new ClosestTrace(
                finiteOrNull(shot.closestGap), finiteOrNull(shot.closestCenterGap), shot.closestClientTick,
                shot.clientInterpolatedClipHint, shot.firstClientClipHintTick, vecTrace(shot.firstClientClipHintPoint),
                vecTrace(shot.closestPearl), vecTrace(shot.closestWind),
                "CLIENT_INTERPOLATION_HINT_ONLY_NOT_SERVER_COLLISION_EVIDENCE"
        );
        if (shot.debug && traceSession != null) traceSession.write();
        if (config.debugChat || !shot.debug) {
            player.sendSystemMessage(Component.literal("Pearl Catcher result: " + reason
                    + (Double.isFinite(shot.closestGap) ? " | closest=" + fmt(shot.closestGap) + "b" : "")));
        }
        activeShots.remove(shot);
        if (shot.debug && sweep != null && sweep.active()) sweep.waitTicks = config.debugBetweenShotsTicks;
    }

    private void recordUnlaunchedShot(LocalPlayer player, Rotation target, String label, String reason, PearlCatchConfig config) {
        if (traceSession == null) return;
        ShotTrace trace = ShotTrace.unlaunched(label, target, player, clientTick, reason);
        traceSession.shots.add(trace);
        traceSession.write();
    }





    private static ItemLocation resolveItemLocation(LocalPlayer player, Item item) {
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty() && offhand.getItem() == item) {
            return new ItemLocation(InteractionHand.OFF_HAND, -1);
        }
        int selected = player.getInventory().getSelectedSlot();
        ItemStack selectedStack = player.getInventory().getItem(selected);
        if (!selectedStack.isEmpty() && selectedStack.getItem() == item) {
            return new ItemLocation(InteractionHand.MAIN_HAND, selected);
        }
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                return new ItemLocation(InteractionHand.MAIN_HAND, slot);
            }
        }
        return null;
    }

    private static ItemLocation resolveSelectedMainLocation(LocalPlayer player, Item item) {
        int selected = player.getInventory().getSelectedSlot();
        ItemStack stack = player.getInventory().getItem(selected);
        return !stack.isEmpty() && stack.getItem() == item
                ? new ItemLocation(InteractionHand.MAIN_HAND, selected)
                : null;
    }

    private static boolean selectedMainIs(LocalPlayer player, Item item) {
        ItemStack stack = player.getMainHandItem();
        return !stack.isEmpty() && stack.getItem() == item;
    }

    private static boolean mainHandMatchesExpected(LocalPlayer player, Item item) {
        ItemStack stack = player.getMainHandItem();
        return item == Items.AIR ? stack.isEmpty() : (!stack.isEmpty() && stack.getItem() == item);
    }

    private static ItemStack stackForLocation(LocalPlayer player, ItemLocation location) {
        return location.hand() == InteractionHand.OFF_HAND
                ? player.getOffhandItem()
                : player.getInventory().getItem(location.slot());
    }

    private static boolean isOnCooldown(LocalPlayer player, ItemLocation location) {
        ItemStack stack = stackForLocation(player, location);
        return !stack.isEmpty() && player.getCooldowns().isOnCooldown(stack);
    }

    private static int selectFastLocation(LocalPlayer player, ItemLocation location, int currentExecutorSlot) {
        if (location.hand() == InteractionHand.MAIN_HAND && player.getInventory().getSelectedSlot() != location.slot()) {
            player.getInventory().setSelectedSlot(location.slot());
            return location.slot();
        }
        return currentExecutorSlot;
    }



    private record ItemLocation(InteractionHand hand, int slot) {}
    private record UseResult(boolean pearlUsed, boolean windUsed) {}
    private record CameraRestore(long restoreAtTick, float yaw, float pitch) {}


}
