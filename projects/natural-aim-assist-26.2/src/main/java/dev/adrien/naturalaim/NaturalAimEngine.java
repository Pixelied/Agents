package dev.adrien.naturalaim;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 * real mouse intent, then adds only a bounded correction.
 */
public final class NaturalAimEngine {
    private static final long TARGET_SCAN_INTERVAL_NS = 50_000_000L;
    private static final long REACQUIRE_COOLDOWN_NS = 190_000_000L;
    private static final double ACQUISITION_RAMP_SECONDS = 0.12;
    private static final double MIN_INTENT_DEGREES = 0.045;
    private static final double PULL_AWAY_ALIGNMENT = -0.22;
    private static final double FLICK_START_DEGREES_PER_SECOND = 115.0;
    private static final double FLICK_END_DEGREES_PER_SECOND = 285.0;
    private static final double AXIS_DEADZONE_DEGREES = 0.075;

    private final NaturalAimConfig config;

    private LocalPlayer lastPlayer;
    private ClientLevel lastLevel;
    private boolean baselineValid;
    private float lastOutputYaw;
    private float lastOutputPitch;
    private long lastFrameNanos;

    private LivingEntity target;
    private long targetAcquiredNanos;
    private long lastTargetScanNanos;
    private long suppressedUntilNanos;

    private double correctionYawVelocity;
    private double correctionPitchVelocity;

    public NaturalAimEngine(NaturalAimConfig config) {
        this.config = config;
    }

    public void onMouseTurn(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        long now = System.nanoTime();

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

        if (!contextAllowsAssistance(minecraft, player)) {
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

        if (AimMath.isDeliberatePullAway(
                rawYaw,
                intentPitch,
                error.yaw(),
                errorPitch,
                MIN_INTENT_DEGREES,
                PULL_AWAY_ALIGNMENT
        )) {
            target = null;
            correctionYawVelocity = 0.0;
            correctionPitchVelocity = 0.0;
            suppressedUntilNanos = now + REACQUIRE_COOLDOWN_NS;
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        double angularError = Math.hypot(error.yaw(), errorPitch);
        if (angularError < AXIS_DEADZONE_DEGREES) {
            decayController(dt, config.preset().deceleration());
            syncBaseline(vanillaYaw, vanillaPitch, now);
            return;
        }

        double factor = correctionFactor(
                minecraft, player, activeTarget, rawYaw, intentPitch, error, angularError, dt, now
        );
        NaturalAimConfig.Preset preset = config.preset();

        double desiredYawVelocity = Math.abs(error.yaw()) <= AXIS_DEADZONE_DEGREES
                ? 0.0
                : error.yaw() * preset.gain() * factor;
        double desiredPitchVelocity = !config.verticalAssist() || Math.abs(error.pitch()) <= AXIS_DEADZONE_DEGREES
                ? 0.0
                : error.pitch() * preset.gain() * factor * 0.84;

        desiredYawVelocity = AimMath.clamp(desiredYawVelocity, -preset.maxYawSpeed(), preset.maxYawSpeed());
        desiredPitchVelocity = AimMath.clamp(desiredPitchVelocity, -preset.maxPitchSpeed(), preset.maxPitchSpeed());

        correctionYawVelocity = approachVelocity(correctionYawVelocity, desiredYawVelocity, preset, dt);
        correctionPitchVelocity = approachVelocity(correctionPitchVelocity, desiredPitchVelocity, preset, dt);

        double yawStep = AimMath.boundedStep(correctionYawVelocity, dt, error.yaw());
        double pitchStep = config.verticalAssist()
                ? AimMath.boundedStep(correctionPitchVelocity, dt, error.pitch())
                : 0.0;

        float finalYaw = (float) (vanillaYaw + yawStep);
        float finalPitch = AimMath.clamp((float) (vanillaPitch + pitchStep), -90.0f, 90.0f);
        player.setYRot(finalYaw);
        player.setXRot(finalPitch);

        syncBaseline(finalYaw, finalPitch, now);
    }

    public void reset() {
        resetSession(null, null, System.nanoTime());
    }

    private boolean contextAllowsAssistance(Minecraft minecraft, LocalPlayer player) {
        if (!config.enabled() || !player.isAlive() || player.isSpectator()) return false;
        if (minecraft.gui.screen() != null) return false;
        if (config.requireAttack() && !minecraft.options.keyAttack.isDown()) return false;
        if (config.weaponsOnly() && !isCombatWeapon(player.getMainHandItem())) return false;

        if (config.pauseActions()) {
            if (minecraft.options.keyUse.isDown()) return false;
            if (minecraft.gameMode != null && minecraft.gameMode.isDestroying()) return false;
        }
        return true;
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
            double score = angular + distance * 0.035;
            if (score < bestScore) {
                bestScore = score;
                best = living;
            }
        }

        if (best != null) {
            target = best;
            targetAcquiredNanos = now;
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
        } else if (!config.targetMobs()) {
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
        return angular <= Math.max(config.assistFov() * 1.7, config.assistFov() + 3.0);
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
            long now
    ) {
        double fov = Math.max(2.0, config.assistFov());
        double edgeFactor = 1.0 - AimMath.smoothstep(fov * 0.55, fov, angularError);

        double inputSpeed = Math.hypot(rawYaw, rawPitch) / Math.max(dt, 1.0e-4);
        double flickFactor = 1.0 - 0.94 * AimMath.smoothstep(
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

        double intentFactor;
        if (inputMagnitude < 0.015) {
            intentFactor = 0.42;
        } else if (alignment >= 0.0) {
            intentFactor = 0.56 + 0.44 * alignment;
        } else {
            intentFactor = 0.20 + 0.22 * (alignment - PULL_AWAY_ALIGNMENT) / -PULL_AWAY_ALIGNMENT;
            intentFactor = AimMath.clamp(intentFactor, 0.18, 0.42);
        }

        double acquisitionSeconds = Math.max(0L, now - targetAcquiredNanos) / 1_000_000_000.0;
        double acquisitionFactor = AimMath.smoothstep(0.0, ACQUISITION_RAMP_SECONDS, acquisitionSeconds);

        double distance = player.getEyePosition().distanceTo(selectionAimPoint(activeTarget));
        double closeFactor = 0.45 + 0.55 * AimMath.smoothstep(1.35, 3.0, distance);
        double longFactor = 1.0 - 0.12 * AimMath.smoothstep(4.5, 6.0, distance);
        double distanceFactor = closeFactor * longFactor;

        double attackFactor = minecraft.options.keyAttack.isDown() ? 1.08 : 0.82;

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

        double marginX = widthX * 0.20;
        double marginZ = widthZ * 0.20;
        double minX = box.minX + marginX;
        double maxX = box.maxX - marginX;
        double minZ = box.minZ + marginZ;
        double maxZ = box.maxZ - marginZ;
        double minY = box.minY + height * 0.34;
        double maxY = box.minY + height * 0.82;

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
        correctionYawVelocity = 0.0;
        correctionPitchVelocity = 0.0;

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
