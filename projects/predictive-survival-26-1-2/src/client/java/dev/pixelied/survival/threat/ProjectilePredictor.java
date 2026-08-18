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

public final class ProjectilePredictor implements ThreatPredictor {
    private static final double EPSILON = 1.0E-9d;
    private final ExplosionExposure explosionExposure = new ExplosionExposure();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            ProjectileFamily.from(entity).ifPresent(family -> result.addAll(predictEntity(context, entity, family)));
        }
        return List.copyOf(result);
    }

    private List<ThreatEvent> predictEntity(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        ProjectileFamily family
    ) {
        if (Boolean.parseBoolean(entity.properties().getOrDefault("in_ground", "false"))) return List.of();

        ProjectileMotionModel motion = motionModel(entity, family);
        ProjectileBounds bounds = ProjectileBounds.from(entity);
        ProjectileStep current = new ProjectileStep(entity.position(), entity.velocity(), 0L);
        int horizon = context.limits().maxProjectileHorizonTicks();

        for (int i = 0; i < horizon; i++) {
            ProjectileStep next = motion.step(current);
            double playerT = segmentAabbEntry(
                current.position(),
                next.position(),
                bounds.expand(sweptPlayerBox(context.player(), next.tick()))
            );
            Collision block = firstBlockCollision(context.world().blocks(), bounds, current.position(), next.position());

            if (block != null && block.t() <= playerT + EPSILON) {
                Optional<ThreatEvent> explosion = collisionExplosion(context, entity, block.position(), next.tick());
                return explosion.map(List::of).orElseGet(List::of);
            }

            if (Double.isFinite(playerT)) {
                Vec3Snapshot impact = interpolate(current.position(), next.position(), playerT);
                List<ThreatEvent> events = new ArrayList<>();
                Vec3Snapshot impactVelocity = switch (family) {
                    case ARROW_LIKE, LLAMA_SPIT -> current.velocity();
                    default -> next.velocity();
                };
                directHit(entity, family, impactVelocity, impact, next.tick()).ifPresent(events::add);
                collisionExplosion(context, entity, impact, next.tick()).ifPresent(events::add);
                return List.copyOf(events);
            }

            Optional<ThreatEvent> timedExplosion = timedExplosion(context, entity, family, next);
            if (timedExplosion.isPresent()) return List.of(timedExplosion.get());
            current = next;
        }
        return List.of();
    }

    private static ProjectileMotionModel motionModel(WorldSnapshot.EntitySnapshot entity, ProjectileFamily family) {
        Map<String, String> properties = entity.properties();
        boolean noGravity = Boolean.parseBoolean(properties.getOrDefault("no_gravity", "false"));
        boolean inWater = Boolean.parseBoolean(properties.getOrDefault("in_water", "false"));
        boolean inLiquid = Boolean.parseBoolean(properties.getOrDefault("in_liquid", Boolean.toString(inWater)));

        return switch (family) {
            case ARROW_LIKE -> VanillaProjectileMotionModels.arrowLike(
                inWater ? 0.6d : 0.99d,
                noGravity ? 0d : 0.05d
            );
            case THROWABLE -> VanillaProjectileMotionModels.throwable(
                inWater ? 0.8d : 0.99d,
                noGravity ? 0d : 0.03d
            );
            case LLAMA_SPIT -> VanillaProjectileMotionModels.llamaSpit(0.99d, noGravity ? 0d : 0.06d);
            case HURTING_PROJECTILE -> VanillaProjectileMotionModels.hurtingProjectile(
                inLiquid ? 0.8d : 0.95d,
                finiteNonNegative(properties.get("acceleration_power"), 0.1d)
            );
            case WIND_CHARGE -> VanillaProjectileMotionModels.constantVelocity();
            case FIREWORK -> VanillaProjectileMotionModels.firework(
                Boolean.parseBoolean(properties.getOrDefault("horizontal_collision", "false"))
            );
        };
    }

    private Optional<ThreatEvent> directHit(
        WorldSnapshot.EntitySnapshot entity,
        ProjectileFamily family,
        Vec3Snapshot impactVelocity,
        Vec3Snapshot impact,
        long tick
    ) {
        Optional<DamageRange> damage = directDamage(entity, family, impactVelocity);
        if (damage.isEmpty() || damage.get().max() <= 0f) return Optional.empty();

        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.IS_PROJECTILE);
        if (Boolean.parseBoolean(entity.properties().getOrDefault("bypasses_shield", "false"))) {
            flags.add(DamageFlag.BYPASSES_SHIELD);
        }
        boolean piercing = positiveInt(entity.properties().get("pierce_level"), 0) > 0;
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damage.get(),
            flags,
            Boolean.parseBoolean(entity.properties().getOrDefault("scales_with_difficulty", "false")),
            1f,
            piercing,
            Optional.of(entity.position()),
            entity.properties().getOrDefault("source_key", defaultSourceKey(entity, family))
        );

        Confidence confidence = damage.get().min() == damage.get().max() ? Confidence.EXACT : Confidence.BOUNDED;
        return Optional.of(new ThreatEvent(
            "projectile:" + entity.id() + ":direct",
            ThreatKind.PROJECTILE,
            observedImpactWindow(entity, tick),
            source,
            confidence,
            Optional.of(entity.position()),
            Optional.of(impact),
            true,
            !source.has(DamageFlag.BYPASSES_SHIELD) && !piercing,
            true,
            Boolean.parseBoolean(entity.properties().getOrDefault("can_disable_blocking", "false"))
        ));
    }

    private Optional<DamageRange> directDamage(
        WorldSnapshot.EntitySnapshot entity,
        ProjectileFamily family,
        Vec3Snapshot impactVelocity
    ) {
        Map<String, String> properties = entity.properties();
        Float exact = finiteFloat(properties.get("raw_damage"));
        if (exact != null && exact >= 0f) return Optional.of(DamageRange.exact(exact));

        if (family == ProjectileFamily.ARROW_LIKE) {
            Float baseDamage = finiteFloat(properties.get("base_damage"));
            if (baseDamage == null || baseDamage < 0f) {
                return Optional.of(new DamageRange(0f, Float.MAX_VALUE));
            }

            double scaledDamage = VanillaProjectileMotionModels.length(impactVelocity) * baseDamage;
            if (!Double.isFinite(scaledDamage) || scaledDamage > Integer.MAX_VALUE) {
                return Optional.of(new DamageRange(0f, Float.MAX_VALUE));
            }
            int normal = Math.max(0, (int) Math.ceil(scaledDamage));
            String critical = properties.getOrDefault("critical", "unknown");
            if ("true".equalsIgnoreCase(critical) || "unknown".equalsIgnoreCase(critical)) {
                long maximum = (long) normal + normal / 2L + 1L;
                return Optional.of(new DamageRange(normal, Math.min((float) maximum, Float.MAX_VALUE)));
            }
            return Optional.of(DamageRange.exact(normal));
        }
        if (family == ProjectileFamily.LLAMA_SPIT) return Optional.of(DamageRange.exact(1f));
        if (family == ProjectileFamily.WIND_CHARGE) return Optional.of(DamageRange.exact(1f));

        Float min = finiteFloat(properties.get("raw_damage_min"));
        Float max = finiteFloat(properties.get("raw_damage_max"));
        if (min != null && max != null && min >= 0f && max >= min) return Optional.of(new DamageRange(min, max));
        return Optional.empty();
    }

    private Optional<ThreatEvent> collisionExplosion(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        long tick
    ) {
        Float radius = finiteFloat(entity.properties().get("explosion_radius"));
        if (radius == null || radius <= 0f) return Optional.empty();
        double distance = distance(context.player().position(), impact);
        float raw = explosionExposure.rawEntityDamage(radius, distance, 1f);
        if (raw <= 0f) return Optional.empty();

        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.IS_EXPLOSION);
        if (Boolean.parseBoolean(entity.properties().getOrDefault("bypasses_shield", "false"))) {
            flags.add(DamageFlag.BYPASSES_SHIELD);
        }
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(raw),
            flags,
            Boolean.parseBoolean(entity.properties().getOrDefault("scales_with_difficulty", "false")),
            1f,
            false,
            Optional.of(impact),
            entity.properties().getOrDefault("explosion_source_key", "minecraft:explosion")
        );
        return Optional.of(new ThreatEvent(
            "projectile:" + entity.id() + ":explosion",
            ThreatKind.PROJECTILE,
            observedImpactWindow(entity, tick),
            source,
            Confidence.BOUNDED,
            Optional.of(entity.position()),
            Optional.of(impact),
            true,
            !source.has(DamageFlag.BYPASSES_SHIELD),
            true,
            false
        ));
    }

    private Optional<ThreatEvent> timedExplosion(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        ProjectileFamily family,
        ProjectileStep step
    ) {
        if (family != ProjectileFamily.FIREWORK) return Optional.empty();
        int life = positiveInt(entity.properties().get("life_ticks"), 0);
        int lifetime = positiveInt(entity.properties().get("lifetime_ticks"), Integer.MAX_VALUE);
        if ((long) life + step.tick() <= lifetime) return Optional.empty();

        int explosions = positiveInt(entity.properties().get("firework_explosions"), 0);
        if (explosions <= 0) return Optional.empty();
        float base = 5f + 2f * explosions;
        double distance = distance(context.player().position(), step.position());
        if (distance > 5d) return Optional.empty();
        float scaled = (float) (base * Math.sqrt((5d - distance) / 5d));
        if (scaled <= 0f) return Optional.empty();

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(scaled),
            EnumSet.of(DamageFlag.IS_EXPLOSION),
            false,
            1f,
            false,
            Optional.of(step.position()),
            entity.properties().getOrDefault("source_key", "minecraft:fireworks")
        );
        return Optional.of(new ThreatEvent(
            "projectile:" + entity.id() + ":firework",
            ThreatKind.PROJECTILE,
            observedImpactWindow(entity, step.tick()),
            source,
            Confidence.BOUNDED,
            Optional.of(entity.position()),
            Optional.of(step.position()),
            true,
            true,
            true,
            false
        ));
    }

    private static TickWindow observedImpactWindow(WorldSnapshot.EntitySnapshot entity, long modeledTick) {
        int observationAge = positiveInt(entity.properties().get("observation_age_ticks"), 0);
        long observedTick = Math.max(0L, modeledTick - observationAge);
        return new TickWindow(observedTick, observedTick);
    }

    private static Collision firstBlockCollision(
        List<WorldSnapshot.BlockSnapshot> blocks,
        ProjectileBounds bounds,
        Vec3Snapshot from,
        Vec3Snapshot to
    ) {
        Collision best = null;
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!block.collision()
                || !Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"))) {
                continue;
            }
            double x = Math.floor(block.position().x());
            double y = Math.floor(block.position().y());
            double z = Math.floor(block.position().z());
            AabbSnapshot cube = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
            double t = segmentAabbEntry(from, to, bounds.expand(cube));
            if (Double.isFinite(t) && (best == null || t < best.t())) {
                best = new Collision(t, interpolate(from, to, t));
            }
        }
        return best;
    }

    private static AabbSnapshot sweptPlayerBox(PlayerSnapshot player, long tick) {
        AabbSnapshot box = player.boundingBox();
        Vec3Snapshot velocity = player.velocity();
        double dx = velocity.x() * tick;
        double dy = velocity.y() * tick;
        double dz = velocity.z() * tick;
        return new AabbSnapshot(
            Math.min(box.minX(), box.minX() + dx),
            Math.min(box.minY(), box.minY() + dy),
            Math.min(box.minZ(), box.minZ() + dz),
            Math.max(box.maxX(), box.maxX() + dx),
            Math.max(box.maxY(), box.maxY() + dy),
            Math.max(box.maxZ(), box.maxZ() + dz)
        );
    }

    private static double segmentAabbEntry(Vec3Snapshot from, Vec3Snapshot to, AabbSnapshot box) {
        double[] range = {0d, 1d};
        if (!slab(from.x(), to.x() - from.x(), box.minX(), box.maxX(), range)) return Double.POSITIVE_INFINITY;
        if (!slab(from.y(), to.y() - from.y(), box.minY(), box.maxY(), range)) return Double.POSITIVE_INFINITY;
        if (!slab(from.z(), to.z() - from.z(), box.minZ(), box.maxZ(), range)) return Double.POSITIVE_INFINITY;
        return range[0];
    }

    private static boolean slab(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) < 1.0E-12d) return origin >= min && origin <= max;
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        if (t1 > t2) {
            double swap = t1;
            t1 = t2;
            t2 = swap;
        }
        range[0] = Math.max(range[0], t1);
        range[1] = Math.min(range[1], t2);
        return range[0] <= range[1];
    }

    private static Vec3Snapshot interpolate(Vec3Snapshot from, Vec3Snapshot to, double t) {
        return new Vec3Snapshot(
            from.x() + (to.x() - from.x()) * t,
            from.y() + (to.y() - from.y()) * t,
            from.z() + (to.z() - from.z()) * t
        );
    }

    private static double distance(Vec3Snapshot a, Vec3Snapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String defaultSourceKey(WorldSnapshot.EntitySnapshot entity, ProjectileFamily family) {
        return switch (family) {
            case ARROW_LIKE -> entity.typeKey().contains("trident") ? "minecraft:trident" : "minecraft:arrow";
            case LLAMA_SPIT -> "minecraft:spit";
            case WIND_CHARGE -> "minecraft:wind_charge";
            default -> "minecraft:mob_projectile";
        };
    }

    private static double finiteNonNegative(String value, double fallback) {
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
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

    private static int positiveInt(String value, int fallback) {
        if (value == null) return fallback;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private record Collision(double t, Vec3Snapshot position) {
    }

    private record ProjectileBounds(
        double minOffsetX,
        double minOffsetY,
        double minOffsetZ,
        double maxOffsetX,
        double maxOffsetY,
        double maxOffsetZ
    ) {
        static ProjectileBounds from(WorldSnapshot.EntitySnapshot entity) {
            AabbSnapshot box = entity.boundingBox();
            Vec3Snapshot position = entity.position();
            return new ProjectileBounds(
                box.minX() - position.x(),
                box.minY() - position.y(),
                box.minZ() - position.z(),
                box.maxX() - position.x(),
                box.maxY() - position.y(),
                box.maxZ() - position.z()
            );
        }

        AabbSnapshot expand(AabbSnapshot target) {
            return new AabbSnapshot(
                target.minX() - maxOffsetX,
                target.minY() - maxOffsetY,
                target.minZ() - maxOffsetZ,
                target.maxX() - minOffsetX,
                target.maxY() - minOffsetY,
                target.maxZ() - minOffsetZ
            );
        }
    }
}
