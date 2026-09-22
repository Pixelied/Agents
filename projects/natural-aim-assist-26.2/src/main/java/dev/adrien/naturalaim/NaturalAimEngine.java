package dev.adrien.naturalaim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Frame-rate-aware aim correction controller.
 *
 * Vanilla mouse input is applied before this engine runs. The engine compares that
 * vanilla-produced rotation with the previous final rotation to recover the player's
 * real mouse intent, then adds only a bounded correction. A 20 Hz client-tick
 * fallback keeps the controller alive while the mouse hook is idle.
 */
public final class NaturalAimEngine {
    private static final long TARGET_SCAN_INTERVAL_NS = 40_000_000L;
    private static final long REACQUIRE_COOLDOWN_NS = 120_000_000L;
    private static final long PVP_REACQUIRE_COOLDOWN_NS = 55_000_000L;
    private static final long MOUSE_FALLBACK_DELAY_NS = 35_000_000L;
    private static final long COMBAT_INTENT_HOLD_NS = 900_000_000L;
    private static final long INCIDENTAL_PVP_BLOCK_HIT_GRACE_NS = 280_000_000L;
    private static final long PVP_POST_CLICK_BLOCK_GRACE_NS = 140_000_000L;
    private static final double ACQUISITION_RAMP_SECONDS = 0.060;
    private static final double PULL_AWAY_ALIGNMENT = -0.34;
    private static final double PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES = 0.80;
    private static final double PULL_AWAY_NOISE_FLOOR_DEGREES = 0.008;
    private static final double PULL_AWAY_EVIDENCE_DECAY_PER_SECOND = 3.25;
    private static final double FLICK_START_DEGREES_PER_SECOND = 500.0;
    private static final double FLICK_END_DEGREES_PER_SECOND = 1400.0;
    private static final double AXIS_DEADZONE_DEGREES = 0.04;

    private final NaturalAimConfig config;

    private LocalPlayer lastPlayer;
    private ClientLevel lastLevel;
    private boolean baselineValid;
    private float lastOutputYaw;
    private float lastOutputPitch;
    private long lastFrameNanos;
    private long lastMouseTurnNanos;

    private LivingEntity target;
    private long targetAcquiredNanos;
    private long lastTargetScanNanos;
    private long suppressedUntilNanos;
    private long lastAttackInputNanos;
    private long attackHeldSinceNanos;
    private boolean attackWasDown;

    private double correctionYawVelocity;
    private double correctionPitchVelocity;
    private double pullAwayEvidenceDegrees;

    public NaturalAimEngine(NaturalAimConfig config) {
        this.config = config;
    }

    public void onMouseTurn(Minecraft minecraft) {
        long now = System.nanoTime();
        lastMouseTurnNanos = now;
        update(minecraft, now);
    }

    public void onClientTick(Minecraft minecraft) {
        long now = System.nanoTime();
        if (now - lastMouseTurnNanos < MOUSE_FALLBACK_DELAY_NS) {
            return;
        }
        update(minecraft, now);
    }

    private void update(Minecraft minecraft, long now) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;

        if (player == null || level == null || player != lastPlayer || level != lastLevel) {
            resetSession(player, level, now);
            return;
        }

        float vanillaYaw = player.getYRot();
        float vanillaPitch = player.getXRot();

        if (!baselineValid) {
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        double dt = (now - lastFrameNanos) / 1_000_000_000.0;
        dt = AimMath.clamp(dt, 1.0 / 300.0, 0.05);

        double rawYaw = AimMath.wrapDegrees(vanillaYaw - lastOutputYaw);
        double rawPitch = vanillaPitch - lastOutputPitch;

        boolean attackDown = minecraft.options.keyAttack.isDown();
        if (attackDown) {
            lastAttackInputNanos = now;
            if (!attackWasDown) {
                attackHeldSinceNanos = now;
            }
        } else {
            attackHeldSinceNanos = 0L;
        }
        attackWasDown = attackDown;

        if (!hardContextAllowsAssistance(minecraft, player)) {
            clearTransientState();
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        if (config.requireAttack() && !combatIntentActive(minecraft, now)) {
            clearTransientState();
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        // Resolve/retain the combat target before evaluating mining pause. In real
        // PvP, a click that misses the opponent by a few pixels can briefly make
        // vanilla report block destruction. The old ordering paused assistance
        // before it knew a valid player target was right there.
        LivingEntity activeTarget = resolveTarget(level, player, now);

        if (temporarilyPausedByAction(minecraft, activeTarget != null, now)) {
            decayController(dt, config.preset().deceleration());
            pullAwayEvidenceDegrees = AimMath.approach(
                    pullAwayEvidenceDegrees,
                    0.0,
                    PULL_AWAY_EVIDENCE_DECAY_PER_SECOND * dt
            );
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        if (activeTarget == null) {
            decayController(dt, config.preset().deceleration());
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        Vec3 aimPoint = dynamicAimPoint(player, activeTarget, now);
        RotationError error = rotationError(player.getEyePosition(), vanillaYaw, vanillaPitch, aimPoint);

        double intentPitch = config.verticalAssist() ? rawPitch : 0.0;
        double errorPitch = config.verticalAssist() ? error.pitch() : 0.0;

        double angularError = Math.hypot(error.yaw(), errorPitch);
        if (angularError < AXIS_DEADZONE_DEGREES) {
            pullAwayEvidenceDegrees = 0.0;
            decayController(dt, config.preset().deceleration());
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        double pullAwayThreshold = activeTarget instanceof Player
                ? AimMath.pvpPullAwayThreshold(config.strength())
                : PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES;

        pullAwayEvidenceDegrees = AimMath.updatePullAwayEvidence(
                pullAwayEvidenceDegrees,
                rawYaw,
                intentPitch,
                error.yaw(),
                errorPitch,
                PULL_AWAY_ALIGNMENT,
                PULL_AWAY_NOISE_FLOOR_DEGREES,
                PULL_AWAY_EVIDENCE_DECAY_PER_SECOND,
                dt
        );

        if (pullAwayEvidenceDegrees >= pullAwayThreshold) {
            boolean playerTarget = activeTarget instanceof Player;
            target = null;
            correctionYawVelocity = 0.0;
            correctionPitchVelocity = 0.0;
            pullAwayEvidenceDegrees = 0.0;
            suppressedUntilNanos = now + (playerTarget ? PVP_REACQUIRE_COOLDOWN_NS : REACQUIRE_COOLDOWN_NS);
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        double factor = correctionFactor(
                minecraft, player, activeTarget, rawYaw, intentPitch, error, angularError, dt, now,
                pullAwayEvidenceDegrees
        );
        NaturalAimConfig.Preset preset = config.preset();

        double desiredYawVelocity = Math.abs(error.yaw()) <= AXIS_DEADZONE_DEGREES
                ? 0.0
                : error.yaw() * preset.gain() * factor;
        double desiredPitchVelocity = !config.verticalAssist() || Math.abs(error.pitch()) <= AXIS_DEADZONE_DEGREES
                ? 0.0
                : error.pitch() * preset.gain() * factor * 0.86;

        double targetDistance = player.getEyePosition().distanceTo(selectionAimPoint(activeTarget));
        double turnSpeedScale = activeTarget instanceof Player
                ? AimMath.pvpTurnSpeedScale(targetDistance)
                : 1.0;

        desiredYawVelocity = AimMath.clamp(
                desiredYawVelocity,
                -preset.maxYawSpeed() * turnSpeedScale,
                preset.maxYawSpeed() * turnSpeedScale
        );
        desiredPitchVelocity = AimMath.clamp(
                desiredPitchVelocity,
                -preset.maxPitchSpeed() * Math.sqrt(turnSpeedScale),
                preset.maxPitchSpeed() * Math.sqrt(turnSpeedScale)
        );

        correctionYawVelocity = approachVelocity(correctionYawVelocity, desiredYawVelocity, preset, dt);
        correctionPitchVelocity = approachVelocity(correctionPitchVelocity, desiredPitchVelocity, preset, dt);

        double yawStep = AimMath.boundedStep(correctionYawVelocity, dt, error.yaw());
        double pitchStep = config.verticalAssist()
                ? AimMath.boundedStep(correctionPitchVelocity, dt, error.pitch())
                : 0.0;

        // Immediate stabilizer for tiny movement away from the target. This is
        // intentionally tied to pull-away evidence: hand noise gets resisted,
        // but resistance rapidly disappears once the user is clearly trying to
        // leave the target.
        double opposingInput = AimMath.opposingProjection(
                rawYaw,
                intentPitch,
                error.yaw(),
                errorPitch
        );
        double resistanceRelease = 1.0 - AimMath.smoothstep(
                pullAwayThreshold * 0.18,
                pullAwayThreshold,
                pullAwayEvidenceDegrees
        );
        double resistanceStrength = Math.sqrt(config.strength())
                * (0.55 + 0.35 * preset.strengthScale())
                * resistanceRelease;
        double resistanceDegrees = Math.min(0.45, opposingInput * resistanceStrength);

        if (resistanceDegrees > 0.0 && angularError > 1.0e-6) {
            double unitYaw = error.yaw() / angularError;
            double unitPitch = errorPitch / angularError;

            yawStep = AimMath.boundedCorrection(
                    yawStep + unitYaw * resistanceDegrees,
                    error.yaw()
            );

            if (config.verticalAssist()) {
                pitchStep = AimMath.boundedCorrection(
                        pitchStep + unitPitch * resistanceDegrees,
                        error.pitch()
                );
            }
        }

        float finalYaw = (float) (vanillaYaw + yawStep);
        float finalPitch = AimMath.clamp((float) (vanillaPitch + pitchStep), -90.0f, 90.0f);
        player.setYRot(finalYaw);
        player.setXRot(finalPitch);

        syncBaseline(finalYaw, finalPitch, now);
    }

    public void reset() {
        resetSession(null, null, System.nanoTime());
    }

    private boolean hardContextAllowsAssistance(Minecraft minecraft, LocalPlayer player) {
        if (!config.enabled() || !player.isAlive() || player.isSpectator()) return false;
        if (minecraft.gui.screen() != null) return false;
        return !config.weaponsOnly() || isCombatWeapon(player.getMainHandItem());
    }

    private boolean temporarilyPausedByAction(Minecraft minecraft, boolean combatTargetAvailable, long now) {
        boolean destroyingBlock = minecraft.gameMode != null && minecraft.gameMode.isDestroying();
        long attackHeldNanos = attackHeldSinceNanos > 0L && now >= attackHeldSinceNanos
                ? now - attackHeldSinceNanos
                : Long.MAX_VALUE;
        long recentAttackAgeNanos = lastAttackInputNanos > 0L && now >= lastAttackInputNanos
                ? now - lastAttackInputNanos
                : Long.MAX_VALUE;

        return AimMath.shouldPauseForAction(
                config.pauseActions(),
                minecraft.options.keyUse.isDown(),
                destroyingBlock,
                combatTargetAvailable,
                minecraft.options.keyAttack.isDown(),
                attackHeldNanos,
                recentAttackAgeNanos,
                INCIDENTAL_PVP_BLOCK_HIT_GRACE_NS,
                PVP_POST_CLICK_BLOCK_GRACE_NS
        );
    }

    private boolean combatIntentActive(Minecraft minecraft, long now) {
        if (!config.requireAttack()) return true;
        if (minecraft.options.keyAttack.isDown()) return true;
        return AimMath.combatIntentActive(now, lastAttackInputNanos, COMBAT_INTENT_HOLD_NS);
    }

    private boolean isCombatWeapon(ItemStack stack) {
        return stack.is(ItemTags.SWORDS)
                || stack.is(ItemTags.AXES)
                || stack.is(ItemTags.SPEARS)
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof TridentItem;
    }

    private LivingEntity resolveTarget(ClientLevel level, LocalPlayer player, long now) {
        if (now < suppressedUntilNanos) return null;

        if (target != null && isCommittedTargetValid(player, target)) {
            return target;
        }
        target = null;

        if (now - lastTargetScanNanos < TARGET_SCAN_INTERVAL_NS) return null;
        lastTargetScanNanos = now;

        AABB searchBox = player.getBoundingBox().inflate(config.range());
        List<Entity> candidates = level.getEntities(player, searchBox, this::basicCandidate);

        LivingEntity best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        for (Entity entity : candidates) {
            if (!(entity instanceof LivingEntity living) || !validCandidate(player, living)) continue;

            Vec3 selectionPoint = selectionAimPoint(living);
            RotationError error = rotationError(player.getEyePosition(), player.getYRot(), player.getXRot(), selectionPoint);
            double angular = Math.hypot(error.yaw(), config.verticalAssist() ? error.pitch() : 0.0);
            if (angular > config.assistFov()) continue;

            double distance = player.getEyePosition().distanceTo(selectionPoint);
            double score = angular + distance * 0.03;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        if (best != null) {
            target = best;
            targetAcquiredNanos = now;
            pullAwayEvidenceDegrees = 0.0;
        }
        return target;
    }

    private boolean basicCandidate(Entity entity) {
        return entity instanceof LivingEntity && entity != lastPlayer;
    }

    private boolean validCandidate(LocalPlayer player, LivingEntity candidate) {
        if (candidate == player || !candidate.isAlive() || candidate.isDeadOrDying()) return false;

        if (candidate instanceof Player otherPlayer) {
            if (!config.targetPlayers() || otherPlayer.isSpectator() || otherPlayer.isCreative()) return false;
        } else if (candidate instanceof Mob mob) {
            if (!TargetRules.allowsMobCategory(
                    mob.getType().getCategory(),
                    config.targetHostileMobs(),
                    config.targetPassiveMobs()
            )) return false;
        } else {
            return false;
        }

        if (config.ignoreInvisible() && candidate.isInvisible()) return false;
        if (config.visibleOnly() && !player.hasLineOfSight(candidate)) return false;

        Vec3 selectionPoint = selectionAimPoint(candidate);
        return player.getEyePosition().distanceTo(selectionPoint) <= config.range();
    }

    private boolean isCommittedTargetValid(LocalPlayer player, LivingEntity candidate) {
        if (!validCandidate(player, candidate)) return false;
        RotationError error = rotationError(
                player.getEyePosition(),
                player.getYRot(),
                player.getXRot(),
                selectionAimPoint(candidate)
        );
        double angular = Math.hypot(error.yaw(), config.verticalAssist() ? error.pitch() : 0.0);
        double releaseFov = Math.min(360.0, Math.max(config.assistFov() * 1.45, config.assistFov() + 6.0));
        return angular <= releaseFov;
    }

    private double correctionFactor(
            Minecraft minecraft,
            LocalPlayer player,
            LivingEntity activeTarget,
            double rawYaw,
            double rawPitch,
            RotationError error,
            double angularError,
            double dt,
            long now,
            double pullAwayEvidence
    ) {
        double fov = Math.max(1.0, config.assistFov());

        double inputSpeed = Math.hypot(rawYaw, rawPitch) / Math.max(dt, 1.0e-4);
        double targetAgeSeconds = Math.max(0L, now - targetAcquiredNanos) / 1_000_000_000.0;
        boolean playerTarget = activeTarget instanceof Player;

        // Acquisition should still be softer near the configured edge, but once
        // a real PvP target has been committed, do not throw most of the authority
        // away just because the opponent strafed toward that edge.
        double edgeFalloff = AimMath.smoothstep(fov * 0.62, fov, angularError);
        double edgeSuppression = playerTarget
                ? AimMath.lerp(0.42, 0.12, AimMath.smoothstep(0.045, 0.18, targetAgeSeconds))
                : 0.65;
        double edgeFactor = 1.0 - edgeSuppression * edgeFalloff;

        double flickFactor = playerTarget
                ? AimMath.adaptivePvpFlickFactor(
                        inputSpeed,
                        targetAgeSeconds,
                        FLICK_START_DEGREES_PER_SECOND,
                        FLICK_END_DEGREES_PER_SECOND
                )
                : AimMath.adaptiveFlickFactor(
                        inputSpeed,
                        targetAgeSeconds,
                        FLICK_START_DEGREES_PER_SECOND,
                        FLICK_END_DEGREES_PER_SECOND
                );

        double inputMagnitude = Math.hypot(rawYaw, rawPitch);
        double alignment = AimMath.intentAlignment(
                rawYaw,
                rawPitch,
                error.yaw(),
                config.verticalAssist() ? error.pitch() : 0.0
        );

        // Do not punish the assist just because the mouse moved at all. The old
        // implementation reacted to instantaneous direction, so one tiny delta
        // opposite the target could slash the correction strength or cancel the
        // target outright. Use the accumulated pull-away evidence instead.
        double pullAwayProgress = AimMath.clamp(
                pullAwayEvidence / (playerTarget
                        ? AimMath.pvpPullAwayThreshold(config.strength())
                        : PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES),
                0.0,
                1.0
        );

        double intentFactor;
        if (inputMagnitude < 0.02) {
            intentFactor = playerTarget ? 0.99 : 0.94;
        } else if (alignment >= 0.0) {
            intentFactor = playerTarget
                    ? 0.96 + 0.04 * alignment
                    : 0.90 + 0.10 * alignment;
        } else {
            intentFactor = playerTarget
                    ? 0.97 - 0.18 * pullAwayProgress
                    : 0.94 - 0.34 * pullAwayProgress;
        }

        double acquisitionSeconds = targetAgeSeconds;
        double acquisitionFactor = playerTarget
                ? 0.68 + 0.32 * AimMath.smoothstep(0.0, ACQUISITION_RAMP_SECONDS, acquisitionSeconds)
                : 0.48 + 0.52 * AimMath.smoothstep(0.0, ACQUISITION_RAMP_SECONDS, acquisitionSeconds);

        double distance = player.getEyePosition().distanceTo(selectionAimPoint(activeTarget));
        double longFactor = 1.0 - 0.10 * AimMath.smoothstep(5.0, 8.0, distance);
        double distanceFactor;
        if (playerTarget) {
            // Close melee targets move through far more screen-space per second
            // than a stationary test target. Do not weaken assistance there.
            distanceFactor = AimMath.pvpProximityStrength(distance)
                    * AimMath.pvpCommitmentStrength(targetAgeSeconds)
                    * longFactor;
        } else {
            double closeFactor = 0.62 + 0.38 * AimMath.smoothstep(1.15, 2.7, distance);
            distanceFactor = closeFactor * longFactor;
        }

        double attackFactor;
        if (config.requireAttack()) {
            attackFactor = combatIntentActive(minecraft, now) ? 1.06 : 1.0;
        } else {
            attackFactor = minecraft.options.keyAttack.isDown() ? 1.06 : 1.0;
        }

        return config.strength()
                * config.preset().strengthScale()
                * edgeFactor
                * flickFactor
                * intentFactor
                * acquisitionFactor
                * distanceFactor
                * attackFactor;
    }

    private static double approachVelocity(double current, double desired, NaturalAimConfig.Preset preset, double dt) {
        boolean slowing = Math.signum(current) != Math.signum(desired)
                || Math.abs(desired) < Math.abs(current);
        double rate = slowing ? preset.deceleration() : preset.acceleration();
        return AimMath.approach(current, desired, rate * dt);
    }

    private void decayController(double dt, double deceleration) {
        correctionYawVelocity = AimMath.approach(correctionYawVelocity, 0.0, deceleration * dt);
        correctionPitchVelocity = AimMath.approach(correctionPitchVelocity, 0.0, deceleration * dt);
    }

    private Vec3 dynamicAimPoint(LocalPlayer player, LivingEntity activeTarget, long now) {
        AABB box = activeTarget.getBoundingBox();
        double widthX = box.maxX - box.minX;
        double widthZ = box.maxZ - box.minZ;
        double height = box.maxY - box.minY;

        boolean playerTarget = activeTarget instanceof Player;
        double horizontalInset = playerTarget ? 0.25 : 0.18;
        double verticalMin = playerTarget ? 0.34 : 0.30;
        double verticalMax = playerTarget ? 0.80 : 0.84;

        double marginX = widthX * horizontalInset;
        double marginZ = widthZ * horizontalInset;
        double minX = box.minX + marginX;
        double maxX = box.maxX - marginX;
        double minZ = box.minZ + marginZ;
        double maxZ = box.maxZ - marginZ;
        double minY = box.minY + height * verticalMin;
        double maxY = box.minY + height * verticalMax;

        Vec3 eye = player.getEyePosition();
        Vec3 center = new Vec3(
                (box.minX + box.maxX) * 0.5,
                box.minY + height * 0.58,
                (box.minZ + box.maxZ) * 0.5
        );

        if (playerTarget) {
            Vec3 velocity = activeTarget.getDeltaMovement();
            double horizontalSpeed = Math.hypot(velocity.x, velocity.z);
            double distance = eye.distanceTo(center);
            double leadTicks = AimMath.pvpLeadTicks(horizontalSpeed, distance);

            // Remote-player movement is already interpolated client-side, so this is
            // intentionally a small lead rather than full prediction.
            Vec3 lead = new Vec3(
                    velocity.x * leadTicks,
                    velocity.y * leadTicks * 0.40,
                    velocity.z * leadTicks
            );

            minX += lead.x;
            maxX += lead.x;
            minY += lead.y;
            maxY += lead.y;
            minZ += lead.z;
            maxZ += lead.z;
            center = center.add(lead);
        }

        double depth = Math.max(0.1, eye.distanceTo(center));
        Vec3 projected = eye.add(player.getLookAngle().scale(depth));
        Vec3 clamped = new Vec3(
                AimMath.clampToRegion(projected.x, minX, maxX),
                AimMath.clampToRegion(projected.y, minY, maxY),
                AimMath.clampToRegion(projected.z, minZ, maxZ)
        );

        if (!playerTarget) {
            return clamped;
        }

        double targetAgeSeconds = Math.max(0L, now - targetAcquiredNanos) / 1_000_000_000.0;
        double centerBias = AimMath.pvpCenterBias(config.strength(), targetAgeSeconds);

        return new Vec3(
                AimMath.lerp(clamped.x, center.x, centerBias),
                AimMath.lerp(clamped.y, center.y, centerBias * 0.78),
                AimMath.lerp(clamped.z, center.z, centerBias)
        );
    }

    private static Vec3 selectionAimPoint(LivingEntity target) {
        AABB box = target.getBoundingBox();
        return new Vec3(
                (box.minX + box.maxX) * 0.5,
                box.minY + (box.maxY - box.minY) * 0.58,
                (box.minZ + box.maxZ) * 0.5
        );
    }

    private static RotationError rotationError(Vec3 eye, float currentYaw, float currentPitch, Vec3 point) {
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.hypot(dx, dz);

        double desiredYaw = Math.toDegrees(Math.atan2(dz, dx)) - 90.0;
        double desiredPitch = -Math.toDegrees(Math.atan2(dy, horizontal));
        return new RotationError(
                AimMath.wrapDegrees(desiredYaw - currentYaw),
                AimMath.wrapDegrees(desiredPitch - currentPitch)
        );
    }

    private void clearTransientState() {
        target = null;
        correctionYawVelocity = 0.0;
        correctionPitchVelocity = 0.0;
        pullAwayEvidenceDegrees = 0.0;
    }

    private void resetSession(LocalPlayer player, ClientLevel level, long now) {
        lastPlayer = player;
        lastLevel = level;
        baselineValid = false;
        lastFrameNanos = now;
        target = null;
        targetAcquiredNanos = 0L;
        lastTargetScanNanos = 0L;
        suppressedUntilNanos = 0L;
        lastAttackInputNanos = 0L;
        attackHeldSinceNanos = 0L;
        attackWasDown = false;
        correctionYawVelocity = 0.0;
        correctionPitchVelocity = 0.0;
        pullAwayEvidenceDegrees = 0.0;

        if (player != null) {
            syncBaseline(player.getYRot(), player.getXRot(), now);
        }
    }

    private void syncBaseline(float yaw, float pitch, long now) {
        lastOutputYaw = yaw;
        lastOutputPitch = pitch;
        lastFrameNanos = now;
        baselineValid = true;
    }

    private record RotationError(double yaw, double pitch) {
    }
}
