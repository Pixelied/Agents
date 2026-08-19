package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class FallPredictor implements ThreatPredictor {
    private final FallLandingSolver landingSolver = new FallLandingSolver();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> events = new ArrayList<>();

        Optional<LandingPrediction> landing = landingSolver.solve(context);
        List<ThreatEvent> voidThreats = predictVoidTicks(context, landing.map(LandingPrediction::tick));
        if (!voidThreats.isEmpty()) {
            events.addAll(voidThreats);
        } else {
            landing.flatMap(this::landingThreat).ifPresent(events::add);
        }

        predictElytraWall(context).ifPresent(events::add);
        events.addAll(predictFallingObjects(context));
        return List.copyOf(events);
    }

    private Optional<ThreatEvent> landingThreat(LandingPrediction landing) {
        if (landing.rawFallDamage().max() <= 0f) return Optional.empty();
        boolean stalagmite = "minecraft:pointed_dripstone".equals(landing.surfaceBlockId());
        EnumSet<DamageFlag> flags = EnumSet.of(
            DamageFlag.BYPASSES_ARMOR,
            DamageFlag.BYPASSES_SHIELD,
            DamageFlag.IS_FALL
        );
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            landing.rawFallDamage(),
            flags,
            false,
            1f,
            false,
            Optional.of(landing.position()),
            stalagmite ? "minecraft:stalagmite" : "minecraft:fall"
        );
        return Optional.of(new ThreatEvent(
            "fall:landing",
            ThreatKind.FALL,
            new TickWindow(landing.tick(), landing.tick()),
            source,
            Confidence.BOUNDED,
            Optional.empty(),
            Optional.of(landing.position()),
            true,
            false,
            true,
            false
        ));
    }

    private List<ThreatEvent> predictVoidTicks(PredictionContext context, Optional<Long> landingTick) {
        PlayerSnapshot player = context.player();
        Double worldMinY = finiteDouble(player.state("world_min_y"));
        if (worldMinY == null) return List.of();
        double threshold = worldMinY - 64d;
        long firstHitTick = firstBelowThreshold(context, threshold, landingTick.orElse(Long.MAX_VALUE));
        if (firstHitTick < 0) return List.of();

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(4f),
            EnumSet.of(
                DamageFlag.BYPASSES_ARMOR,
                DamageFlag.BYPASSES_SHIELD,
                DamageFlag.BYPASSES_INVULNERABILITY,
                DamageFlag.BYPASSES_RESISTANCE
            ),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:out_of_world"
        );

        long horizon = context.limits().maxProjectileHorizonTicks();
        long lastHitTick = horizon;
        if (landingTick.isPresent()) {
            long landingAt = landingTick.get();
            if (landingAt <= firstHitTick) return List.of();
            lastHitTick = Math.min(lastHitTick, landingAt - 1L);
        }

        List<ThreatEvent> events = new ArrayList<>();
        for (long tick = firstHitTick; tick <= lastHitTick; tick++) {
            events.add(new ThreatEvent(
                "fall:void:" + tick,
                ThreatKind.FALL,
                new TickWindow(tick, tick),
                source,
                Confidence.BOUNDED,
                Optional.empty(),
                Optional.empty(),
                true,
                false,
                true,
                false
            ));
        }
        return List.copyOf(events);
    }

    private static long firstBelowThreshold(PredictionContext context, double threshold, long stopBeforeOrAt) {
        PlayerSnapshot player = context.player();
        Vec3Snapshot position = player.position();
        Vec3Snapshot velocity = player.velocity();
        double gravity = finiteNonNegative(player.state("effective_gravity"), 0.08d);
        double verticalFriction = finitePositive(player.state("vertical_friction"), 0.98d);
        double horizontalFriction = finitePositive(player.state("horizontal_friction"), 0.91d);
        if (position.y() < threshold) return 0L;

        for (long tick = 1; tick <= context.limits().maxProjectileHorizonTicks() && tick < stopBeforeOrAt; tick++) {
            position = add(position, velocity);
            if (position.y() < threshold) return tick;
            velocity = new Vec3Snapshot(
                velocity.x() * horizontalFriction,
                (velocity.y() - gravity) * verticalFriction,
                velocity.z() * horizontalFriction
            );
        }
        return -1L;
    }

    private Optional<ThreatEvent> predictElytraWall(PredictionContext context) {
        PlayerSnapshot player = context.player();
        if (!Boolean.parseBoolean(value(player.state("fall_flying"), "false"))) return Optional.empty();
        double speed = horizontalLength(player.velocity());
        float raw = (float) Math.max(0d, speed * 10d - 3d);
        if (raw <= 0f) return Optional.empty();

        AabbSnapshot box = player.boundingBox();
        Vec3Snapshot velocity = player.velocity();
        for (long tick = 1; tick <= context.limits().maxProjectileHorizonTicks(); tick++) {
            AabbSnapshot next = move(box, velocity.x(), velocity.y(), velocity.z());
            if (intersectsConfirmedBlock(next, context.world().blocks())) {
                DamageSourceSnapshot source = new DamageSourceSnapshot(
                    DamageRange.exact(raw),
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    false,
                    1f,
                    false,
                    Optional.empty(),
                    "minecraft:fly_into_wall"
                );
                return Optional.of(new ThreatEvent(
                    "fall:elytra_wall",
                    ThreatKind.FALL,
                    new TickWindow(tick, tick),
                    source,
                    Confidence.POTENTIAL,
                    Optional.empty(),
                    Optional.empty(),
                    true,
                    false,
                    true,
                    false
                ));
            }
            box = next;
        }
        return Optional.empty();
    }

    private List<ThreatEvent> predictFallingObjects(PredictionContext context) {
        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!"minecraft:falling_block".equals(entity.typeKey())) continue;
            if (!Boolean.parseBoolean(entity.properties().getOrDefault("hurt_entities", "false"))) continue;
            predictFallingObject(context, entity).ifPresent(result::add);
        }
        return result;
    }

    private Optional<ThreatEvent> predictFallingObject(PredictionContext context, WorldSnapshot.EntitySnapshot entity) {
        Vec3Snapshot position = entity.position();
        Vec3Snapshot velocity = entity.velocity();
        AabbSnapshot box = entity.boundingBox();
        double accumulatedFall = finiteNonNegative(entity.properties().get("fall_distance"), 0d);

        for (long tick = 1; tick <= context.limits().maxProjectileHorizonTicks(); tick++) {
            velocity = new Vec3Snapshot(velocity.x(), velocity.y() - 0.04d, velocity.z());
            AabbSnapshot nextBox = move(box, velocity.x(), velocity.y(), velocity.z());
            accumulatedFall += Math.max(0d, box.minY() - nextBox.minY());
            AabbSnapshot playerBox = move(
                context.player().boundingBox(),
                context.player().velocity().x() * tick,
                context.player().velocity().y() * tick,
                context.player().velocity().z() * tick
            );
            if (intersects(nextBox, playerBox)) {
                DamageRange damage = fallingObjectDamage(entity.properties(), accumulatedFall);
                if (damage.max() <= 0f) return Optional.empty();
                String sourceKey = entity.properties().getOrDefault("damage_source", "minecraft:falling_block");
                EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.DAMAGES_HELMET);
                if ("minecraft:falling_anvil".equals(sourceKey) || "minecraft:falling_stalactite".equals(sourceKey)) {
                    flags.add(DamageFlag.BYPASSES_SHIELD);
                }
                DamageSourceSnapshot source = new DamageSourceSnapshot(
                    damage, flags, false, 1f, false, Optional.of(position), sourceKey
                );
                return Optional.of(new ThreatEvent(
                    "falling_object:" + entity.id(),
                    ThreatKind.FALL,
                    new TickWindow(tick, tick),
                    source,
                    Confidence.BOUNDED,
                    Optional.of(position),
                    Optional.of(context.player().position()),
                    true,
                    !flags.contains(DamageFlag.BYPASSES_SHIELD),
                    true,
                    false
                ));
            }
            position = add(position, velocity);
            box = nextBox;
            velocity = new Vec3Snapshot(velocity.x() * 0.98d, velocity.y() * 0.98d, velocity.z() * 0.98d);
        }
        return Optional.empty();
    }

    private static DamageRange fallingObjectDamage(Map<String, String> properties, double fallDistance) {
        Float perDistance = finiteFloat(properties.get("fall_damage_per_distance"));
        Float maxDamage = finiteFloat(properties.get("fall_damage_max"));
        if (perDistance == null || maxDamage == null || perDistance < 0f || maxDamage < 0f) {
            return new DamageRange(0f, Float.MAX_VALUE);
        }
        int distance = (int) Math.ceil(fallDistance - 1d);
        if (distance < 0) return DamageRange.exact(0f);
        double raw = Math.min(Math.floor(distance * perDistance), maxDamage);
        if (!Double.isFinite(raw) || raw >= Float.MAX_VALUE) return new DamageRange(0f, Float.MAX_VALUE);
        return DamageRange.exact((float) Math.max(0d, raw));
    }

    private static boolean intersectsConfirmedBlock(AabbSnapshot box, List<WorldSnapshot.BlockSnapshot> blocks) {
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!FallLandingSolver.confirmedFullCollisionCube(block)) continue;
            double x = Math.floor(block.position().x());
            double y = Math.floor(block.position().y());
            double z = Math.floor(block.position().z());
            if (intersects(box, new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d))) return true;
        }
        return false;
    }

    private static boolean intersects(AabbSnapshot a, AabbSnapshot b) {
        return a.maxX() > b.minX() && a.minX() < b.maxX()
            && a.maxY() > b.minY() && a.minY() < b.maxY()
            && a.maxZ() > b.minZ() && a.minZ() < b.maxZ();
    }

    private static AabbSnapshot move(AabbSnapshot box, double dx, double dy, double dz) {
        return new AabbSnapshot(
            box.minX() + dx, box.minY() + dy, box.minZ() + dz,
            box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz
        );
    }

    private static Vec3Snapshot add(Vec3Snapshot a, Vec3Snapshot b) {
        return new Vec3Snapshot(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
    }

    private static double horizontalLength(Vec3Snapshot value) {
        return Math.sqrt(value.x() * value.x() + value.z() * value.z());
    }

    private static String value(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static Double finiteDouble(String value) {
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double finiteNonNegative(String value, double fallback) {
        Double parsed = finiteDouble(value);
        return parsed != null && parsed >= 0d ? parsed : fallback;
    }

    private static double finitePositive(String value, double fallback) {
        Double parsed = finiteDouble(value);
        return parsed != null && parsed > 0d ? parsed : fallback;
    }

    private static Float finiteFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
