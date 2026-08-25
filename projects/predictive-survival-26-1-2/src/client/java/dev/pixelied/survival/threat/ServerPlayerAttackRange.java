package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.Map;
import java.util.Optional;

/** Source-faithful 26.1.2 server validation for player attack packets. */
public final class ServerPlayerAttackRange {
    public static final double ATTACK_PACKET_BUFFER = 3.0d;

    private ServerPlayerAttackRange() {
    }

    /**
     * Uses the full synchronized/snapshotted AttackRange contract when available. Older synthetic
     * snapshots fall back to the legacy AABB/entity-range check so compatibility tests stay valid.
     */
    public static boolean isWithin(
        WorldSnapshot.EntitySnapshot attacker,
        AabbSnapshot target
    ) {
        Optional<Vec3Snapshot> eye = eyePosition(attacker);
        Optional<AttackProfile> profile = attackProfile(attacker.properties());
        if (eye.isPresent() && profile.isPresent()) {
            return isWithin(eye.get(), target, profile.get());
        }

        double reach = nonNegativeDouble(attacker.properties().get("attack_range"))
            .orElse(Double.POSITIVE_INFINITY);
        return aabbDistance(attacker.boundingBox(), target) <= reach;
    }

    public static boolean isWithin(
        Vec3Snapshot eye,
        AabbSnapshot target,
        AttackProfile profile
    ) {
        double distance = Math.sqrt(distanceToSqr(eye, target));
        double min = profile.minRange() - profile.hitboxMargin() - ATTACK_PACKET_BUFFER;
        double max = profile.maxRange() + profile.hitboxMargin() + ATTACK_PACKET_BUFFER;
        return distance >= min && distance <= max;
    }

    public static Optional<AttackProfile> attackProfile(Map<String, String> properties) {
        Optional<Double> entityRange = nonNegativeDouble(properties.get("attack_range"));
        if (entityRange.isEmpty()) return Optional.empty();

        Optional<Double> min = nonNegativeDouble(properties.get("main_hand_attack_min_range"));
        Optional<Double> max = nonNegativeDouble(properties.get("main_hand_attack_max_range"));
        Optional<Double> margin = nonNegativeDouble(properties.get("main_hand_attack_hitbox_margin"));
        if (min.isEmpty() || max.isEmpty() || margin.isEmpty() || min.get() > max.get()) {
            return Optional.of(new AttackProfile(0d, entityRange.get(), 0d, "default_entity_range"));
        }
        return Optional.of(new AttackProfile(min.get(), max.get(), margin.get(), "current_main_hand"));
    }

    public static Optional<Vec3Snapshot> eyePosition(WorldSnapshot.EntitySnapshot attacker) {
        Optional<Double> x = finiteDouble(attacker.properties().get("eye_position_x"));
        Optional<Double> y = finiteDouble(attacker.properties().get("eye_position_y"));
        Optional<Double> z = finiteDouble(attacker.properties().get("eye_position_z"));
        if (x.isEmpty() || y.isEmpty() || z.isEmpty()) return Optional.empty();
        return Optional.of(new Vec3Snapshot(x.get(), y.get(), z.get()));
    }

    private static double distanceToSqr(Vec3Snapshot point, AabbSnapshot box) {
        double dx = axisDistance(point.x(), box.minX(), box.maxX());
        double dy = axisDistance(point.y(), box.minY(), box.maxY());
        double dz = axisDistance(point.z(), box.minZ(), box.maxZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0d;
    }

    private static double aabbDistance(AabbSnapshot a, AabbSnapshot b) {
        double dx = axisGap(a.minX(), a.maxX(), b.minX(), b.maxX());
        double dy = axisGap(a.minY(), a.maxY(), b.minY(), b.maxY());
        double dz = axisGap(a.minZ(), a.maxZ(), b.minZ(), b.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisGap(double minA, double maxA, double minB, double maxB) {
        if (maxA < minB) return minB - maxA;
        if (maxB < minA) return minA - maxB;
        return 0d;
    }

    private static Optional<Double> nonNegativeDouble(String value) {
        Optional<Double> parsed = finiteDouble(value);
        return parsed.isPresent() && parsed.get() >= 0d ? parsed : Optional.empty();
    }

    private static Optional<Double> finiteDouble(String value) {
        if (value == null) return Optional.empty();
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? Optional.of(parsed) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public record AttackProfile(double minRange, double maxRange, double hitboxMargin, String source) {
        public AttackProfile {
            if (!Double.isFinite(minRange) || minRange < 0d) throw new IllegalArgumentException("minRange");
            if (!Double.isFinite(maxRange) || maxRange < minRange) throw new IllegalArgumentException("maxRange");
            if (!Double.isFinite(hitboxMargin) || hitboxMargin < 0d) throw new IllegalArgumentException("hitboxMargin");
            if (source == null || source.isBlank()) throw new IllegalArgumentException("source");
        }
    }
}
