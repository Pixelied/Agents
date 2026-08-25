package dev.pixelied.survival.threat.opportunity;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.VanillaDamageOracle;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.threat.ExplosionSpec;
import dev.pixelied.survival.threat.ExplosionThreatFactory;
import dev.pixelied.survival.threat.SnapshotOcclusionView;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatTimeline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Predicts source-confirmed TNT-minecart detonation paths before the minecart is primed or explodes. */
public final class TntMinecartOpportunityPredictor implements LethalOpportunityPredictor {
    private static final String TNT_MINECART = "minecraft:tnt_minecart";
    private static final double HORIZONTAL_COLLISION_SPEED_SQR = 0.01d;
    private static final double MAX_EXPLOSION_SPEED = 5.0d;
    private static final double VANILLA_BASE_POWER = 4.0d;
    private static final double VANILLA_SPEED_FACTOR = 1.0d;
    private static final double HIDDEN_POWER_COMPONENT_MAX = 128.0d;
    private static final int RANDOM_SHORT_FUSE_MIN = 0;
    private static final int RANDOM_SHORT_FUSE_MAX = 38;
    private static final int RANDOM_SHORT_FUSE_SCHEDULING_MAX = 39;

    private final ExplosionThreatFactory explosionFactory = new ExplosionThreatFactory();
    private final VanillaDamageOracle damageOracle = new VanillaDamageOracle();

    @Override
    public List<LethalOpportunity> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<LethalOpportunity> result = new ArrayList<>();
        SnapshotOcclusionView world = new SnapshotOcclusionView(context.world().blocks());

        for (WorldSnapshot.EntitySnapshot cart : context.world().entities()) {
            if (!TNT_MINECART.equals(cart.typeKey())) continue;
            Map<String, String> properties = cart.properties();
            if (!Boolean.parseBoolean(properties.getOrDefault("tnt_minecart", "false"))) continue;
            if (Boolean.parseBoolean(properties.getOrDefault("tnt_minecart_primed", "false"))) continue;
            String tntExplodes = properties.getOrDefault("tnt_explodes", "unknown");
            if ("false".equals(tntExplodes)) continue;

            Trigger trigger = earliestTrigger(cart, context.world());
            if (trigger == null) continue;

            RadiusRange radius = radiusFor(trigger.speedSqr(), context.safetyMode(), properties);
            if (radius.max() <= 0f) continue;
            Vec3Snapshot center = add(cart.position(), scale(cart.velocity(), trigger.impact().earliest()));
            TickWindow impact = trigger.impact();
            String id = "opportunity:tnt_minecart:" + cart.id() + ":" + trigger.name();
            ExplosionSpec spec = new ExplosionSpec(
                center,
                radius.min(),
                radius.max(),
                properties.getOrDefault("source_key", "minecraft:explosion"),
                Boolean.parseBoolean(properties.getOrDefault("scales_with_difficulty", "true")),
                true
            );
            ThreatEvent projected = explosionFactory.createProjected(
                id,
                impact,
                Confidence.POTENTIAL,
                spec,
                cart.velocity(),
                context,
                world
            ).orElse(null);
            if (projected == null) continue;
            if (!damageOracle.lethalWithoutDeathProtection(
                context.player(), new ThreatTimeline(List.of(projected)))) continue;

            Map<String, String> evidence = new LinkedHashMap<>();
            evidence.put("cart_id", cart.id());
            evidence.put("trigger", trigger.name());
            evidence.put("trigger_tick", Long.toString(trigger.impact().earliest()));
            evidence.put("trigger_tick_latest", Long.toString(trigger.impact().latest()));
            evidence.put("trigger_speed_sqr", Double.toString(trigger.speedSqr()));
            evidence.put("explosion_radius_min", Float.toString(radius.min()));
            evidence.put("explosion_radius_max", Float.toString(radius.max()));
            evidence.put("tnt_explodes", tntExplodes);
            if (trigger.projectileSpeedSqr() != null) {
                evidence.put("projectile_speed_sqr", Double.toString(trigger.projectileSpeedSqr()));
            }
            if (trigger.randomFuseMin() != null) {
                evidence.put("random_fuse_min", Integer.toString(trigger.randomFuseMin()));
                evidence.put("random_fuse_max", Integer.toString(trigger.randomFuseMax()));
            }
            Double fallDistance = finiteDouble(properties.get("fall_distance"));
            if (fallDistance != null) evidence.put("fall_distance", Double.toString(Math.max(0d, fallDistance)));

            result.add(new LethalOpportunity(
                id,
                OpportunityFamily.TNT_MINECART,
                projected,
                Confidence.POTENTIAL,
                0,
                evidence
            ));
        }
        return List.copyOf(result);
    }

    private static Trigger earliestTrigger(WorldSnapshot.EntitySnapshot cart, WorldSnapshot world) {
        double horizontalSpeedSqr = horizontalSpeedSqr(cart.velocity());
        if (Boolean.parseBoolean(cart.properties().getOrDefault("horizontal_collision", "false"))
            && horizontalSpeedSqr >= HORIZONTAL_COLLISION_SPEED_SQR) {
            return Trigger.exact("horizontal_collision", 0L, horizontalSpeedSqr, null);
        }

        Trigger arrow = burningArrowTrigger(cart, world.entities());
        if (arrow != null) return arrow;

        Trigger destructiveProjectile = destructiveBurningProjectileTrigger(cart, world.entities());
        if (destructiveProjectile != null) return destructiveProjectile;

        Double fallDistance = finiteDouble(cart.properties().get("fall_distance"));
        if (fallDistance != null && fallDistance >= 3d && cart.velocity().y() < 0d
            && collidesWithWorld(cart.boundingBox(), cart.velocity(), world.blocks())) {
            double fallPower = fallDistance / 10d;
            return Trigger.exact("fall_impact", 1L, fallPower * fallPower, null);
        }

        Vec3Snapshot horizontalVelocity = new Vec3Snapshot(cart.velocity().x(), 0d, cart.velocity().z());
        if (horizontalSpeedSqr >= HORIZONTAL_COLLISION_SPEED_SQR
            && collidesWithWorld(cart.boundingBox(), horizontalVelocity, world.blocks())) {
            return Trigger.exact("forecast_horizontal_collision", 1L, horizontalSpeedSqr, null);
        }
        return null;
    }

    private static Trigger burningArrowTrigger(
        WorldSnapshot.EntitySnapshot cart,
        List<WorldSnapshot.EntitySnapshot> entities
    ) {
        for (WorldSnapshot.EntitySnapshot projectile : entities) {
            if (projectile == cart || !isAbstractArrow(projectile)) continue;
            if (!Boolean.parseBoolean(projectile.properties().getOrDefault("on_fire", "false"))) continue;
            if (!relativeProjectileSweepHits(projectile, cart)) continue;
            double speedSqr = lengthSqr(projectile.velocity());
            return Trigger.exact("burning_arrow", 0L, speedSqr, speedSqr);
        }
        return null;
    }

    private static Trigger destructiveBurningProjectileTrigger(
        WorldSnapshot.EntitySnapshot cart,
        List<WorldSnapshot.EntitySnapshot> entities
    ) {
        for (WorldSnapshot.EntitySnapshot projectile : entities) {
            if (projectile == cart || isAbstractArrow(projectile)) continue;
            if (!Boolean.parseBoolean(projectile.properties().getOrDefault("projectile", "false"))) continue;
            if (!Boolean.parseBoolean(projectile.properties().getOrDefault("on_fire", "false"))) continue;
            if (!relativeProjectileSweepHits(projectile, cart)) continue;
            double projectileSpeedSqr = lengthSqr(projectile.velocity());
            return new Trigger(
                "destructive_burning_projectile",
                new TickWindow(0L, RANDOM_SHORT_FUSE_SCHEDULING_MAX),
                MAX_EXPLOSION_SPEED * MAX_EXPLOSION_SPEED,
                projectileSpeedSqr,
                RANDOM_SHORT_FUSE_MIN,
                RANDOM_SHORT_FUSE_MAX
            );
        }
        return null;
    }

    private static boolean isAbstractArrow(WorldSnapshot.EntitySnapshot entity) {
        if (Boolean.parseBoolean(entity.properties().getOrDefault("abstract_arrow", "false"))) return true;
        return switch (entity.typeKey()) {
            case "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident" -> true;
            default -> false;
        };
    }

    private static boolean relativeProjectileSweepHits(
        WorldSnapshot.EntitySnapshot projectile,
        WorldSnapshot.EntitySnapshot target
    ) {
        AabbSnapshot projectileBox = projectile.boundingBox();
        double halfX = Math.max(0d, (projectileBox.maxX() - projectileBox.minX()) * 0.5d);
        double halfY = Math.max(0d, (projectileBox.maxY() - projectileBox.minY()) * 0.5d);
        double halfZ = Math.max(0d, (projectileBox.maxZ() - projectileBox.minZ()) * 0.5d);
        AabbSnapshot expandedTarget = expand(target.boundingBox(), halfX, halfY, halfZ);
        Vec3Snapshot relativeVelocity = new Vec3Snapshot(
            projectile.velocity().x() - target.velocity().x(),
            projectile.velocity().y() - target.velocity().y(),
            projectile.velocity().z() - target.velocity().z()
        );
        return segmentIntersects(projectile.position(), add(projectile.position(), relativeVelocity), expandedTarget);
    }

    private static boolean collidesWithWorld(
        AabbSnapshot box,
        Vec3Snapshot delta,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        AabbSnapshot swept = swept(box, delta);
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!block.collision()) continue;
            if (!block.collisionBoxes().isEmpty()) {
                for (AabbSnapshot component : block.collisionBoxes()) {
                    if (intersects(swept, component)) return true;
                }
                continue;
            }
            if (intersects(swept, unitCube(block.position()))) return true;
        }
        return false;
    }

    private static RadiusRange radiusFor(
        double speedSqr,
        SafetyMode mode,
        Map<String, String> properties
    ) {
        double speed = Math.min(Math.sqrt(Math.max(0d, speedSqr)), MAX_EXPLOSION_SPEED);
        if (mode == SafetyMode.SAFE) {
            double hiddenMax = HIDDEN_POWER_COMPONENT_MAX
                + HIDDEN_POWER_COMPONENT_MAX * 1.5d * speed;
            Double capturedHiddenMax = finiteDouble(properties.get("explosion_radius_hidden_max"));
            if (capturedHiddenMax != null) hiddenMax = Math.min(hiddenMax, Math.max(0d, capturedHiddenMax));
            return new RadiusRange(0f, finiteFloat(hiddenMax));
        }
        double max = VANILLA_BASE_POWER + VANILLA_SPEED_FACTOR * 1.5d * speed;
        return new RadiusRange((float)VANILLA_BASE_POWER, finiteFloat(max));
    }

    private static float finiteFloat(double value) {
        if (!Double.isFinite(value) || value <= 0d) return 0f;
        return value >= Float.MAX_VALUE ? Float.MAX_VALUE : (float)value;
    }

    private static double horizontalSpeedSqr(Vec3Snapshot velocity) {
        return velocity.x() * velocity.x() + velocity.z() * velocity.z();
    }

    private static double lengthSqr(Vec3Snapshot velocity) {
        return velocity.x() * velocity.x()
            + velocity.y() * velocity.y()
            + velocity.z() * velocity.z();
    }

    private static AabbSnapshot swept(AabbSnapshot box, Vec3Snapshot delta) {
        AabbSnapshot moved = translate(box, delta);
        return new AabbSnapshot(
            Math.min(box.minX(), moved.minX()),
            Math.min(box.minY(), moved.minY()),
            Math.min(box.minZ(), moved.minZ()),
            Math.max(box.maxX(), moved.maxX()),
            Math.max(box.maxY(), moved.maxY()),
            Math.max(box.maxZ(), moved.maxZ())
        );
    }

    private static AabbSnapshot translate(AabbSnapshot box, Vec3Snapshot delta) {
        return new AabbSnapshot(
            box.minX() + delta.x(), box.minY() + delta.y(), box.minZ() + delta.z(),
            box.maxX() + delta.x(), box.maxY() + delta.y(), box.maxZ() + delta.z()
        );
    }

    private static AabbSnapshot expand(AabbSnapshot box, double x, double y, double z) {
        return new AabbSnapshot(
            box.minX() - x, box.minY() - y, box.minZ() - z,
            box.maxX() + x, box.maxY() + y, box.maxZ() + z
        );
    }

    private static AabbSnapshot unitCube(Vec3Snapshot position) {
        double x = Math.floor(position.x());
        double y = Math.floor(position.y());
        double z = Math.floor(position.z());
        return new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
    }

    private static boolean intersects(AabbSnapshot first, AabbSnapshot second) {
        return first.minX() < second.maxX() && first.maxX() > second.minX()
            && first.minY() < second.maxY() && first.maxY() > second.minY()
            && first.minZ() < second.maxZ() && first.maxZ() > second.minZ();
    }

    private static boolean segmentIntersects(Vec3Snapshot from, Vec3Snapshot to, AabbSnapshot box) {
        double[] range = {0d, 1d};
        return slab(from.x(), to.x() - from.x(), box.minX(), box.maxX(), range)
            && slab(from.y(), to.y() - from.y(), box.minY(), box.maxY(), range)
            && slab(from.z(), to.z() - from.z(), box.minZ(), box.maxZ(), range)
            && range[0] <= range[1];
    }

    private static boolean slab(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) < 1.0E-12d) return origin >= min && origin <= max;
        double first = (min - origin) / direction;
        double second = (max - origin) / direction;
        if (first > second) {
            double swap = first;
            first = second;
            second = swap;
        }
        range[0] = Math.max(range[0], first);
        range[1] = Math.min(range[1], second);
        return range[0] <= range[1];
    }

    private static Vec3Snapshot scale(Vec3Snapshot vector, long ticks) {
        return new Vec3Snapshot(vector.x() * ticks, vector.y() * ticks, vector.z() * ticks);
    }

    private static Vec3Snapshot add(Vec3Snapshot first, Vec3Snapshot second) {
        return new Vec3Snapshot(first.x() + second.x(), first.y() + second.y(), first.z() + second.z());
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

    private record Trigger(
        String name,
        TickWindow impact,
        double speedSqr,
        Double projectileSpeedSqr,
        Integer randomFuseMin,
        Integer randomFuseMax
    ) {
        private static Trigger exact(String name, long tick, double speedSqr, Double projectileSpeedSqr) {
            return new Trigger(name, new TickWindow(tick, tick), speedSqr, projectileSpeedSqr, null, null);
        }
    }

    private record RadiusRange(float min, float max) {
    }
}
