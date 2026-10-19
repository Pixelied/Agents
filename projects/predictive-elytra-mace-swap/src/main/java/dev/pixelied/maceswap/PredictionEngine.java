package dev.pixelied.maceswap;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Short-horizon, non-invasive trajectory forecast. Flight equations follow 26.3
 * LivingEntity#updateFallFlyingMovement; fallDistance and attack reach use
 * vanilla's 26.3 rules. Future player inputs / remote target inputs cannot be
 * known, so the forecast deliberately errs toward not swapping.
 */
public final class PredictionEngine {
    private final Map<Integer, ObservedTarget> observations = new HashMap<>();
    private int tick;

    private static final class ObservedTarget {
        Vec3 last;
        Vec3 velocity = Vec3.ZERO;
        int seenTick;
        ObservedTarget(Vec3 position, int tick) { last = position; seenTick = tick; }
    }

    public record Forecast(int targetId, int smashTick, double targetDistance) {}

    public void clear() { observations.clear(); tick = 0; }

    public void observe(Minecraft mc, SwapConfig config) {
        tick++;
        if (mc.player == null || mc.level == null) return;
        AABB box = mc.player.getBoundingBox().inflate(config.searchRadius);
        for (Entity entity : mc.level.getEntities(mc.player, box, e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator())) {
            Vec3 location = entity.position();
            ObservedTarget observed = observations.get(entity.getId());
            if (observed == null || tick - observed.seenTick > 5) {
                observations.put(entity.getId(), new ObservedTarget(location, tick));
            } else {
                int elapsed = Math.max(1, tick - observed.seenTick);
                Vec3 instant = location.subtract(observed.last).scale(1.0 / elapsed);
                // Smoothing helps with network interpolation without assuming a static target.
                observed.velocity = observed.velocity.scale(0.4).add(instant.scale(0.6));
                observed.last = location;
                observed.seenTick = tick;
            }
        }
        if (tick % 40 == 0) observations.entrySet().removeIf(e -> tick - e.getValue().seenTick > 40);
    }

    /** @param exitAtTick projected server-acknowledged end of gliding */
    public Forecast forecast(Minecraft mc, SwapConfig config, int exitAtTick) {
        if (mc.player == null || mc.level == null) return null;
        var player = mc.player;
        if (player.isInWater() || player.isPassenger() || player.hasEffect(MobEffects.LEVITATION) || player.onGround()) return null;

        ItemStack mace = player.getMainHandItem();
        AttackRange attackRange = player.getAttackRangeWith(mace);
        double maxReach = attackRange.effectiveMaxRange(player) + attackRange.hitboxMargin();
        maxReach *= (100.0 - config.rangeSafetyPercent) / 100.0;
        maxReach = Math.max(0.0, maxReach);
        double minReach = Math.max(0.0, attackRange.effectiveMinRange(player) - attackRange.hitboxMargin());
        Vec3 look = player.getLookAngle();
        double lookHoriz = Math.hypot(look.x(), look.z());
        double lean = player.getXRot() * (Math.PI / 180.0);
        double lift = Math.cos(lean) * Math.cos(lean);
        double gravity = player.getGravity();
        double airModifier = player.getAttributeValue(Attributes.AIR_DRAG_MODIFIER);
        double horDrag = Math.max(0.0, Math.min(1.0, 1.0 - (1.0 - 0.91F) * airModifier));
        double vertDrag = Math.max(0.0, Math.min(1.0, 1.0 - (1.0 - 0.98F) * airModifier));
        double standEyes = player.getEyeHeight(Pose.STANDING);

        // Candidates are selected once, with no block tracing in the entity-selection loop.
        List<Entity> candidates = mc.level.getEntities(player,
            player.getBoundingBox().inflate(config.searchRadius),
            e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e)
                && e.isAttackable() && !e.isRemoved());
        if (candidates.isEmpty()) return null;
        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        double falling = player.fallDistance;
        double bestScore = Double.POSITIVE_INFINITY;
        Forecast best = null;
        int horizon = config.predictionTicks;

        for (int t = 1; t <= horizon; t++) {
            boolean gliding = t < exitAtTick;
            if (gliding) {
                // Mirrored from LivingEntity#updateFallFlyingMovement in supplied vanilla 26.3.
                double horizontalSpeed = velocity.horizontalDistance();
                double effectiveGravity = velocity.y() <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(gravity, 0.01) : gravity;
                velocity = velocity.add(0.0, effectiveGravity * (-1.0 + lift * 0.75), 0.0);
                if (velocity.y() < 0.0 && lookHoriz > 0.0) {
                    double converted = velocity.y() * -0.1 * lift;
                    velocity = velocity.add(look.x() * converted / lookHoriz, converted, look.z() * converted / lookHoriz);
                }
                if (lean < 0 && lookHoriz > 0) {
                    double converted = horizontalSpeed * -Math.sin(lean) * 0.04;
                    velocity = velocity.add(-look.x() * converted / lookHoriz, converted * 3.2, -look.z() * converted / lookHoriz);
                }
                if (lookHoriz > 0) {
                    velocity = velocity.add(
                        (look.x() / lookHoriz * horizontalSpeed - velocity.x()) * 0.1,
                        0.0,
                        (look.z() / lookHoriz * horizontalSpeed - velocity.z()) * 0.1);
                }
                velocity = velocity.multiply(0.99F, 0.98F, 0.99F);
                if (velocity.y() > -0.5 && falling > 1.0) falling = 1.0;
            }
            // In vanilla travelInAir, pre-friction motion displaces player before gravity/drag.
            Vec3 stepMotion = velocity;
            Vec3 nextPosition = position.add(stepMotion);
            if (!Double.isFinite(nextPosition.x()) || !Double.isFinite(nextPosition.y()) || !Double.isFinite(nextPosition.z())) return null;
            // Conservative collision test. Real world collision movement is resolved by vanilla.
            AABB futureBox = gliding
                ? player.getBoundingBox().move(nextPosition.subtract(player.position()))
                : new AABB(nextPosition.x() - 0.3, nextPosition.y(), nextPosition.z() - 0.3,
                    nextPosition.x() + 0.3, nextPosition.y() + 1.8, nextPosition.z() + 0.3);
            if (!mc.level.noCollision(player, futureBox)) break;
            position = nextPosition;
            falling = TrajectoryMath.fallDistanceAfterMovement(falling, stepMotion.y());
            if (!gliding) {
                double effectiveGravity = stepMotion.y() <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING) ? Math.min(gravity, 0.01) : gravity;
                velocity = new Vec3(stepMotion.x() * horDrag,
                    TrajectoryMath.airVelocityY(stepMotion.y(), effectiveGravity, vertDrag),
                    stepMotion.z() * horDrag);
            }
            if (!TrajectoryMath.maceEligible(falling, gliding)
                || t <= exitAtTick || t > exitAtTick + config.swapTimingTicks) continue;
            Vec3 eye = position.add(0, standEyes, 0);
            for (Entity candidate : candidates) {
                AABB bb = candidate.getBoundingBox();
                ObservedTarget observed = observations.get(candidate.getId());
                Vec3 targetMotion = observed == null || tick - observed.seenTick > 5 ? candidate.getDeltaMovement() : observed.velocity;
                // Dampen extrapolated movement; a constantly sprinting target will be re-predicted on every tick.
                double driftX = TrajectoryMath.dampedTargetMotion(targetMotion.x(), t, 0.88);
                double driftZ = TrajectoryMath.dampedTargetMotion(targetMotion.z(), t, 0.88);
                double driftY = targetMotion.y() * Math.min(3, t);
                AABB targetBox = bb.move(driftX, driftY, driftZ);
                double distance = Math.sqrt(targetBox.distanceToSqr(eye));
                if (distance > maxReach || distance < minReach) continue;
                // Attacks are manual. When 'requireAim' is set, don't equip if a stable aim ray would miss.
                Optional<Vec3> hit = targetBox.clip(eye, eye.add(look.scale(maxReach)));
                if (config.requireAim && hit.isEmpty()) continue;
                Vec3 hitPoint = hit.orElseGet(() -> new Vec3(
                    Math.max(targetBox.minX, Math.min(eye.x(), targetBox.maxX)),
                    Math.max(targetBox.minY, Math.min(eye.y(), targetBox.maxY)),
                    Math.max(targetBox.minZ, Math.min(eye.z(), targetBox.maxZ))));
                BlockHitResult obstruction = mc.level.clip(new ClipContext(eye, hitPoint, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (obstruction.getType() != HitResult.Type.MISS && obstruction.getLocation().distanceToSqr(eye) + 0.01 < hitPoint.distanceToSqr(eye)) continue;
                double score = t * 0.45 + distance;
                if (score < bestScore) {
                    bestScore = score;
                    best = new Forecast(candidate.getId(), t, distance);
                }
            }
        }
        return best;
    }
}
