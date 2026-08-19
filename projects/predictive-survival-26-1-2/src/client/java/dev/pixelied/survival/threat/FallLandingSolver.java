package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.List;
import java.util.Optional;

public final class FallLandingSolver {
    private static final double EPSILON = 1.0E-9d;

    public Optional<LandingPrediction> solve(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        PlayerSnapshot player = context.player();
        if (Boolean.parseBoolean(value(player, "fall_flying", "false"))) return Optional.empty();

        double gravity = finiteNonNegative(value(player, "effective_gravity", "0.08"), 0.08d);
        double verticalFriction = finitePositive(value(player, "vertical_friction", "0.98"), 0.98d);
        double horizontalFriction = finitePositive(value(player, "horizontal_friction", "0.91"), 0.91d);
        double safeFallDistance = finiteNonNegative(value(player, "safe_fall_distance", "3"), 3d);
        double fallDamageMultiplier = finiteNonNegative(value(player, "fall_damage_multiplier", "1"), 1d);
        double accumulatedFall = finiteNonNegative(value(player, "fall_distance", "0"), 0d);
        boolean suppressingBounce = Boolean.parseBoolean(value(player, "suppressing_bounce", "false"));

        BoundsOffset bounds = BoundsOffset.from(player.boundingBox(), player.position());
        Vec3Snapshot position = player.position();
        Vec3Snapshot velocity = player.velocity();

        for (long tick = 1; tick <= context.limits().maxProjectileHorizonTicks(); tick++) {
            Vec3Snapshot nextPosition = add(position, velocity);
            LandingHit hit = firstLanding(context.world().blocks(), bounds, position, nextPosition);
            if (hit != null) {
                double downwardPart = Math.max(0d, position.y() - hit.playerPosition().y());
                double totalFallDistance = accumulatedFall + downwardPart;
                FallSurface surface = surface(hit.block(), suppressingBounce);
                float raw = rawFallDamage(
                    totalFallDistance + surface.extraFallDistance(),
                    surface.damageModifier(),
                    safeFallDistance,
                    fallDamageMultiplier
                );
                return Optional.of(new LandingPrediction(
                    hit.playerPosition(),
                    tick,
                    hit.block().blockId(),
                    DamageRange.exact(raw)
                ));
            }

            accumulatedFall += Math.max(0d, position.y() - nextPosition.y());
            position = nextPosition;
            velocity = new Vec3Snapshot(
                velocity.x() * horizontalFriction,
                (velocity.y() - gravity) * verticalFriction,
                velocity.z() * horizontalFriction
            );
        }
        return Optional.empty();
    }

    public static float rawFallDamage(
        double fallDistance,
        float damageModifier,
        double safeFallDistance,
        double fallDamageMultiplier
    ) {
        if (!Double.isFinite(fallDistance) || fallDistance < 0d
            || !Float.isFinite(damageModifier) || damageModifier < 0f
            || !Double.isFinite(safeFallDistance) || safeFallDistance < 0d
            || !Double.isFinite(fallDamageMultiplier) || fallDamageMultiplier < 0d) {
            return Float.MAX_VALUE;
        }
        double raw = Math.floor((fallDistance + 1.0E-6d - safeFallDistance) * damageModifier * fallDamageMultiplier);
        if (raw <= 0d) return 0f;
        if (!Double.isFinite(raw) || raw >= Float.MAX_VALUE) return Float.MAX_VALUE;
        return (float) raw;
    }

    private static LandingHit firstLanding(
        List<WorldSnapshot.BlockSnapshot> blocks,
        BoundsOffset bounds,
        Vec3Snapshot from,
        Vec3Snapshot to
    ) {
        double fromBottom = from.y() + bounds.minY();
        double toBottom = to.y() + bounds.minY();
        if (toBottom >= fromBottom - EPSILON) return null;

        LandingHit best = null;
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!confirmedFullCollisionCube(block)) continue;
            double blockMinX = Math.floor(block.position().x());
            double blockMinY = Math.floor(block.position().y());
            double blockMinZ = Math.floor(block.position().z());
            double blockTop = blockMinY + 1d;
            if (fromBottom < blockTop - EPSILON || toBottom > blockTop + EPSILON) continue;

            double t = (blockTop - fromBottom) / (toBottom - fromBottom);
            if (t < -EPSILON || t > 1d + EPSILON) continue;
            t = Math.max(0d, Math.min(1d, t));
            Vec3Snapshot at = interpolate(from, to, t);
            double minX = at.x() + bounds.minX();
            double maxX = at.x() + bounds.maxX();
            double minZ = at.z() + bounds.minZ();
            double maxZ = at.z() + bounds.maxZ();
            if (maxX <= blockMinX + EPSILON || minX >= blockMinX + 1d - EPSILON
                || maxZ <= blockMinZ + EPSILON || minZ >= blockMinZ + 1d - EPSILON) {
                continue;
            }
            if (best == null || t < best.fraction()) best = new LandingHit(t, at, block);
        }
        return best;
    }

    static boolean confirmedFullCollisionCube(WorldSnapshot.BlockSnapshot block) {
        return block.collision()
            && Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"));
    }

    private static FallSurface surface(WorldSnapshot.BlockSnapshot block, boolean suppressingBounce) {
        if ("minecraft:slime_block".equals(block.blockId()) && !suppressingBounce) {
            return new FallSurface(0f, 0d);
        }
        if ("minecraft:hay_block".equals(block.blockId())) {
            return new FallSurface(0.2f, 0d);
        }
        if ("minecraft:pointed_dripstone".equals(block.blockId())
            && Boolean.parseBoolean(block.properties().getOrDefault("stalagmite_tip_up", "true"))) {
            return new FallSurface(2f, 2.5d);
        }
        return new FallSurface(1f, 0d);
    }

    private static String value(PlayerSnapshot player, String key, String fallback) {
        String value = player.state(key);
        return value == null ? fallback : value;
    }

    private static double finiteNonNegative(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double finitePositive(String value, double fallback) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed > 0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Vec3Snapshot add(Vec3Snapshot a, Vec3Snapshot b) {
        return new Vec3Snapshot(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static Vec3Snapshot interpolate(Vec3Snapshot from, Vec3Snapshot to, double t) {
        return new Vec3Snapshot(
            from.x() + (to.x() - from.x()) * t,
            from.y() + (to.y() - from.y()) * t,
            from.z() + (to.z() - from.z()) * t
        );
    }

    private record FallSurface(float damageModifier, double extraFallDistance) {
    }

    private record LandingHit(
        double fraction,
        Vec3Snapshot playerPosition,
        WorldSnapshot.BlockSnapshot block
    ) {
    }

    private record BoundsOffset(
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
    ) {
        static BoundsOffset from(AabbSnapshot box, Vec3Snapshot position) {
            return new BoundsOffset(
                box.minX() - position.x(),
                box.minY() - position.y(),
                box.minZ() - position.z(),
                box.maxX() - position.x(),
                box.maxY() - position.y(),
                box.maxZ() - position.z()
            );
        }
    }
}
