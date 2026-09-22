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
    private static final long MOUSE_FALLBACK_DELAY_NS = 35_000_000L;
    private static final long COMBAT_INTENT_HOLD_NS = 900_000_000L;
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

        if (minecraft.options.keyAttack.isDown()) {
            lastAttackInputNanos = now;
        }

        if (!hardContextAllowsAssistance(minecraft, player)) {
            clearTransientState();
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        if (temporarilyPausedByAction(minecraft)) {
            decayController(dt, config.preset().deceleration());
            pullAwayEvidenceDegrees = AimMath.approach(
                    pullAwayEvidenceDegrees,
                    0.0,
                    PULL_AWAY_EVIDENCE_DECAY_PER_SECOND * dt
            );
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        if (config.requireAttack() && !combatIntentActive(now)) {
            clearTransientState();
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        LivingEntity activeTarget = resolveTarget(level, player, now);
        if (activeTarget == null) {
            decayController(dt, config.preset().deceleration());
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        Vec3 aimPoint = dynamicAimPoint(player, activeTarget);
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

        if (pullAwayEvidenceDegrees >= PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES) {
            target = null;
            correctionYawVelocity = 0.0;
            correctionPitchVelocity = 0.0;
            pullAwayEvidenceDegrees = 0.0;
            suppressedUntilNanos = now + REACQUIRE_COOLDOWN_NS;
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

        desiredYawVelocity = AimMath.clamp(desiredYawVelocity, -preset.maxYawSpeed(), preset.maxYawSpeed());
        desiredPitchVelocity = AimMath.clamp(desiredPitchVelocity, -preset.maxPitchSpeed(), preset.maxPitchSpeed());

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
                PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES * 0.18,
                PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES,
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

    private boolean temporarilyPausedByAction(Minecraft minecraft) {
        if (!config.pauseActions()) return false;
        if (minecraft.options.keyUse.isDown()) return true;

        // If Require Attack is enabled, vanilla may report block destruction
        // from the same attack input. Do not treat that as a separate pause.
        return !config.requireAttack()
                && minecraft.gameMode != null
                && minecraft.gameMode.isDestroying();
    }

    private boolean combatIntentActive(long now) {
        if (!config.requireAttack()) return true;
        if (minecraftAttackHeld()) return true;
        return AimMath.combatIntentActive(now, lastAttackInputNanos, COMBAT_INTENT_HOLD_NS);
    }

    private boolean minecraftAttackHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.keyAttack.isDown();
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

        // Still soften corrections near the configured edge, but never let the
        // edge factor collapse to zero for a target that is already considered valid.
        double edgeFalloff = AimMath.smoothstep(fov * 0.62, fov, angularError);
        double edgeFactor = 1.0 - 0.65 * edgeFalloff;

        double inputSpeed = Math.hypot(rawYaw, rawPitch) / Math.max(dt, 1.0e-4);
        double targetAgeSeconds = Math.max(0L, now - targetAcquiredNanos) / 1_000_000_000.0;
        double trackingCommitment = AimMath.smoothstep(0.05, 0.18, targetAgeSeconds);
        double maxFlickSuppression = AimMath.lerp(0.42, 0.20, trackingCommitment);
        double flickFactor = 1.0 - maxFlickSuppression * AimMath.smoothstep(
                FLICK_START_DEGREES_PER_SECOND,
                FLICK_END_DEGREES_PER_SECOND,
                inputSpeed
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
                pullAwayEvidence / PULL_AWAY_EVIDENCE_THRESHOLD_DEGREES,
                0.0,
                1.0
        );

        double intentFactor;
        if (inputMagnitude < 0.02) {
            intentFactor = 0.94;
        } else if (alignment >= 0.0) {
            intentFactor = 0.90 + 0.10 * alignment;
        } else {
            intentFactor = 0.94 - 0.34 * pullAwayProgress;
        }

        double acquisitionSeconds = Math.max(0L, now - targetAcquiredNanos) / 1_000_000_000.0;
        double acquisitionFactor = 0.48 + 0.52 * AimMath.smoothstep(0.0, ACQUISITION_RAMP_SECONDS, acquisitionSeconds);

        double distance = player.getEyePosition().distanceTo(selectionAimPoint(activeTarget));
        double closeFactor = 0.62 + 0.38 * AimMath.smoothstep(1.15, 2.7, distance);
        double longFactor = 1.0 - 0.10 * AimMath.smoothstep(5.0, 8.0, distance);
        double distanceFactor = closeFactor * longFactor;

        double attackFactor;
        if (config.requireAttack()) {
            attackFactor = combatIntentActive(now) ? 1.06 : 1.0;
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

    private Vec3 dynamicAimPoint(LocalPlayer player, LivingEntity activeTarget) {
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
        double depth = Math.max(0.1, eye.distanceTo(center));
        Vec3 projected = eye.add(player.getLookAngle().scale(depth));

        return new Vec3(
                AimMath.clampToRegion(projected.x, minX, maxX),
                AimMath.clampToRegion(projected.y, minY, maxY),
                AimMath.clampToRegion(projected.z, minZ, maxZ)
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
