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
    private static final String DRAGON_FIREBALL_TYPE = "minecraft:dragon_fireball";
    private static final String SPLASH_POTION_TYPE = "minecraft:splash_potion";
    private static final String LINGERING_POTION_TYPE = "minecraft:lingering_potion";
    private static final float DRAGON_BREATH_RAW_DAMAGE = 6f;
    private static final double DRAGON_BREATH_RADIUS = 3.05d;
    private static final double DRAGON_BREATH_HEIGHT = 0.5d;
    private static final int DRAGON_BREATH_REAPPLICATION_TICKS = 20;
    private static final float LINGERING_INSTANT_EFFECT_SCALE = 0.5f;
    private static final double LINGERING_CLOUD_RADIUS = 3d;
    private static final double LINGERING_CLOUD_HEIGHT = 0.5d;
    private static final int LINGERING_CLOUD_WAIT_TICKS = 10;
    private static final int SPLASH_EFFECT_CUTOFF_TICKS = 20;
    private static final int POISON_BASE_INTERVAL_TICKS = 25;
    private static final float POISON_RAW_DAMAGE = 1f;
    private static final float POISON_HEALTH_FLOOR = 1f;
    private static final int WITHER_BASE_INTERVAL_TICKS = 40;
    private static final float WITHER_RAW_DAMAGE = 1f;

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
                List<ThreatEvent> events = new ArrayList<>();
                splashPotionImpact(
                    context, entity, block.position(), block.blockBounds(), next.tick(), false
                ).ifPresent(events::add);
                events.addAll(poisonPotionImpacts(context, entity, block.position(), next.tick(), false));
                events.addAll(witherPotionImpacts(context, entity, block.position(), next.tick(), false));
                lingeringPotionImpact(context, entity, block.position(), next.tick()).ifPresent(events::add);
                collisionExplosion(context, entity, block.position(), next.tick()).ifPresent(events::add);
                events.addAll(collisionAreaHazards(context, entity, block.position(), next.tick()));
                return List.copyOf(events);
            }

            if (Double.isFinite(playerT)) {
                Vec3Snapshot impact = interpolate(current.position(), next.position(), playerT);
                List<ThreatEvent> events = new ArrayList<>();
                Vec3Snapshot impactVelocity = switch (family) {
                    case ARROW_LIKE, LLAMA_SPIT -> current.velocity();
                    default -> next.velocity();
                };
                directHit(entity, family, impactVelocity, impact, next.tick()).ifPresent(events::add);
                splashPotionImpact(context, entity, impact, null, next.tick(), true).ifPresent(events::add);
                events.addAll(poisonPotionImpacts(context, entity, impact, next.tick(), true));
                events.addAll(witherPotionImpacts(context, entity, impact, next.tick(), true));
                lingeringPotionImpact(context, entity, impact, next.tick()).ifPresent(events::add);
                collisionExplosion(context, entity, impact, next.tick()).ifPresent(events::add);
                Vec3Snapshot areaOrigin = isDragonFireball(entity)
                    ? playerPositionAt(context.player(), next.tick())
                    : impact;
                events.addAll(collisionAreaHazards(context, entity, areaOrigin, next.tick()));
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

    private Optional<ThreatEvent> splashPotionImpact(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        AabbSnapshot blockBounds,
        long tick,
        boolean directPlayerHit
    ) {
        if (!isSplashPotion(entity)) return Optional.empty();
        Float fullDamage = finiteFloat(entity.properties().get("potion_instant_damage"));
        if (fullDamage == null || fullDamage <= 0f) return Optional.empty();

        DamageRange damage;
        Confidence confidence;
        if (directPlayerHit) {
            damage = DamageRange.exact(fullDamage);
            confidence = Confidence.EXACT;
        } else {
            double radius = finiteNonNegative(entity.properties().get("potion_splash_radius"), 4d);
            if (radius <= 0d) return Optional.empty();

            boolean movingPlayer = hasMotion(context.player().velocity());
            int observationAge = positiveInt(entity.properties().get("observation_age_ticks"), 0);
            if (observationAge > 0 && blockBounds != null) {
                AabbSnapshot possiblePlayerBounds = movingPlayer
                    ? sweptPlayerBox(context.player(), tick)
                    : context.player().boundingBox();
                double minimumDistance = distanceBetweenAabbs(blockBounds, possiblePlayerBounds);
                float maximumRaw = splashRawDamage(fullDamage, radius, minimumDistance);
                if (maximumRaw <= 0f) return Optional.empty();
                damage = new DamageRange(0f, maximumRaw);
            } else if (movingPlayer) {
                double minimumDistance = distanceToAabb(impact, sweptPlayerBox(context.player(), tick));
                float maximumRaw = splashRawDamage(fullDamage, radius, minimumDistance);
                if (maximumRaw <= 0f) return Optional.empty();
                damage = new DamageRange(0f, maximumRaw);
            } else {
                float raw = splashRawDamage(fullDamage, radius, distanceToAabb(impact, context.player().boundingBox()));
                if (raw <= 0f) return Optional.empty();
                damage = DamageRange.exact(raw);
            }
            confidence = Confidence.BOUNDED;
        }

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damage,
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.of(impact),
            entity.properties().getOrDefault("potion_source_key", "minecraft:indirect_magic")
        );
        return Optional.of(new ThreatEvent(
            "projectile:" + entity.id() + ":splash_magic",
            ThreatKind.PROJECTILE,
            observedImpactWindow(entity, tick),
            source,
            confidence,
            Optional.of(entity.position()),
            Optional.of(impact),
            true,
            false,
            true,
            false
        ));
    }

    private List<ThreatEvent> poisonPotionImpacts(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        long tick,
        boolean directPlayerHit
    ) {
        if (!isSplashPotion(entity) || !directPlayerHit) return List.of();
        int sourceDuration = positiveInt(entity.properties().get("potion_poison_duration_ticks"), 0);
        int duration = directSplashStatusDuration(entity, sourceDuration);
        if (duration <= SPLASH_EFFECT_CUTOFF_TICKS) return List.of();
        int amplifier = positiveInt(entity.properties().get("potion_poison_amplifier"), 0);
        int interval = poisonIntervalTicks(amplifier);
        int firstOffset = duration % interval;

        TickWindow collision = observedImpactWindow(entity, tick);
        long horizon = context.limits().maxDecisionHistory();
        List<ThreatEvent> events = new ArrayList<>();
        int application = 0;
        for (int elapsed = firstOffset; elapsed < duration; elapsed += interval) {
            long earliest = saturatingAdd(collision.earliest(), elapsed);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(collision.latest(), elapsed));
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(POISON_RAW_DAMAGE),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(impact),
                "minecraft:magic",
                POISON_HEALTH_FLOOR
            );
            events.add(new ThreatEvent(
                "projectile:" + entity.id() + ":poison:" + application,
                ThreatKind.PROJECTILE,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.of(impact),
                true,
                false,
                true,
                false
            ));
            application++;
        }
        return List.copyOf(events);
    }

    private List<ThreatEvent> witherPotionImpacts(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        long tick,
        boolean directPlayerHit
    ) {
        if (!isSplashPotion(entity) || !directPlayerHit) return List.of();
        int sourceDuration = positiveInt(entity.properties().get("potion_wither_duration_ticks"), 0);
        int duration = directSplashStatusDuration(entity, sourceDuration);
        if (duration <= SPLASH_EFFECT_CUTOFF_TICKS) return List.of();
        int amplifier = positiveInt(entity.properties().get("potion_wither_amplifier"), 0);
        int interval = witherIntervalTicks(amplifier);
        int firstOffset = duration % interval;

        TickWindow collision = observedImpactWindow(entity, tick);
        long horizon = context.limits().maxDecisionHistory();
        List<ThreatEvent> events = new ArrayList<>();
        int application = 0;
        for (int elapsed = firstOffset; elapsed < duration; elapsed += interval) {
            long earliest = saturatingAdd(collision.earliest(), elapsed);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(collision.latest(), elapsed));
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(WITHER_RAW_DAMAGE),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(impact),
                "minecraft:wither",
                0f
            );
            events.add(new ThreatEvent(
                "projectile:" + entity.id() + ":wither:" + application,
                ThreatKind.PROJECTILE,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.of(impact),
                true,
                false,
                true,
                false
            ));
            application++;
        }
        return List.copyOf(events);
    }

    private static int directSplashStatusDuration(WorldSnapshot.EntitySnapshot entity, int sourceDuration) {
        if (sourceDuration <= 0) return 0;
        double durationScale = finiteNonNegative(entity.properties().get("potion_duration_scale"), 1d);
        return (int) (sourceDuration * durationScale + 0.5d);
    }

    private static int poisonIntervalTicks(int amplifier) {
        int shift = Math.min(30, Math.max(0, amplifier));
        return Math.max(1, POISON_BASE_INTERVAL_TICKS >> shift);
    }

    private static int witherIntervalTicks(int amplifier) {
        int shift = Math.min(30, Math.max(0, amplifier));
        return Math.max(1, WITHER_BASE_INTERVAL_TICKS >> shift);
    }

    private Optional<ThreatEvent> lingeringPotionImpact(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impact,
        long tick
    ) {
        if (!isLingeringPotion(entity)) return Optional.empty();
        Float fullDamage = finiteFloat(entity.properties().get("potion_instant_damage"));
        if (fullDamage == null || fullDamage <= 0f) return Optional.empty();

        float rawDamage = fullDamage * LINGERING_INSTANT_EFFECT_SCALE;
        if (!Float.isFinite(rawDamage) || rawDamage <= 0f) return Optional.empty();

        TickWindow collision = observedImpactWindow(entity, tick);
        long earliest = saturatingAdd(collision.earliest(), LINGERING_CLOUD_WAIT_TICKS);
        long latest = saturatingAdd(collision.latest(), LINGERING_CLOUD_WAIT_TICKS);
        long horizon = context.limits().maxDecisionHistory();
        if (earliest > horizon) return Optional.empty();
        latest = Math.min(latest, horizon);
        if (!couldOccupyLingeringCloud(context.player(), impact, latest)) return Optional.empty();

        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.of(impact),
            entity.properties().getOrDefault("potion_source_key", "minecraft:indirect_magic")
        );
        return Optional.of(new ThreatEvent(
            "projectile:" + entity.id() + ":lingering_cloud:0",
            ThreatKind.ENVIRONMENT,
            new TickWindow(earliest, latest),
            source,
            Confidence.BOUNDED,
            Optional.of(entity.position()),
            Optional.of(impact),
            true,
            false,
            true,
            false
        ));
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

    private List<ThreatEvent> collisionAreaHazards(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot areaOrigin,
        long modeledCollisionTick
    ) {
        if (!isDragonFireball(entity)) return List.of();

        TickWindow collisionWindow = observedImpactWindow(entity, modeledCollisionTick);
        long horizon = context.limits().maxDecisionHistory();
        long earliest = collisionWindow.earliest();
        long latest = Math.min(horizon, saturatingAdd(collisionWindow.latest(), DRAGON_BREATH_REAPPLICATION_TICKS));
        List<ThreatEvent> events = new ArrayList<>();
        int application = 0;

        while (earliest <= horizon && earliest <= latest) {
            if (couldOccupyDragonBreath(context.player(), areaOrigin, earliest, latest)) {
                DamageSourceSnapshot source = new DamageSourceSnapshot(
                    DamageRange.exact(DRAGON_BREATH_RAW_DAMAGE),
                    EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                    false,
                    1f,
                    false,
                    Optional.of(areaOrigin),
                    "minecraft:indirect_magic"
                );
                events.add(new ThreatEvent(
                    "projectile:" + entity.id() + ":dragon_breath:" + application,
                    ThreatKind.ENVIRONMENT,
                    new TickWindow(earliest, latest),
                    source,
                    Confidence.BOUNDED,
                    Optional.of(entity.position()),
                    Optional.of(areaOrigin),
                    true,
                    false,
                    true,
                    false
                ));
            }

            if (latest >= horizon) break;
            earliest = latest + 1L;
            latest = Math.min(horizon, saturatingAdd(latest, DRAGON_BREATH_REAPPLICATION_TICKS));
            application++;
        }
        return List.copyOf(events);
    }

    private static boolean couldOccupyDragonBreath(
        PlayerSnapshot player,
        Vec3Snapshot areaOrigin,
        long earliest,
        long latest
    ) {
        AabbSnapshot path = sweptPlayerBox(player, earliest, latest);
        double minX = areaOrigin.x() - DRAGON_BREATH_RADIUS;
        double maxX = areaOrigin.x() + DRAGON_BREATH_RADIUS;
        double minZ = areaOrigin.z() - DRAGON_BREATH_RADIUS;
        double maxZ = areaOrigin.z() + DRAGON_BREATH_RADIUS;
        double minY = areaOrigin.y();
        double maxY = areaOrigin.y() + DRAGON_BREATH_HEIGHT;
        return path.maxX() >= minX && path.minX() <= maxX
            && path.maxZ() >= minZ && path.minZ() <= maxZ
            && path.maxY() >= minY && path.minY() <= maxY;
    }

    private static boolean couldOccupyLingeringCloud(
        PlayerSnapshot player,
        Vec3Snapshot areaOrigin,
        long latest
    ) {
        AabbSnapshot path = sweptPlayerBox(player, 0L, latest);
        double minX = areaOrigin.x() - LINGERING_CLOUD_RADIUS;
        double maxX = areaOrigin.x() + LINGERING_CLOUD_RADIUS;
        double minZ = areaOrigin.z() - LINGERING_CLOUD_RADIUS;
        double maxZ = areaOrigin.z() + LINGERING_CLOUD_RADIUS;
        double minY = areaOrigin.y();
        double maxY = areaOrigin.y() + LINGERING_CLOUD_HEIGHT;
        return path.maxX() >= minX && path.minX() <= maxX
            && path.maxZ() >= minZ && path.minZ() <= maxZ
            && path.maxY() >= minY && path.minY() <= maxY;
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
        long earliest = Math.max(0L, modeledTick - observationAge);
        return new TickWindow(earliest, modeledTick);
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
            double bodyT = segmentAabbEntry(from, to, bounds.expand(cube));
            if (Double.isFinite(bodyT) && (best == null || bodyT < best.t())) {
                double centerT = segmentAabbEntry(from, to, cube);
                Vec3Snapshot impact;
                if (Double.isFinite(centerT)) {
                    impact = interpolate(from, to, centerT);
                } else {
                    Vec3Snapshot center = interpolate(from, to, bodyT);
                    impact = new Vec3Snapshot(
                        Math.max(cube.minX(), Math.min(center.x(), cube.maxX())),
                        Math.max(cube.minY(), Math.min(center.y(), cube.maxY())),
                        Math.max(cube.minZ(), Math.min(center.z(), cube.maxZ()))
                    );
                }
                best = new Collision(bodyT, impact, cube);
            }
        }
        return best;
    }

    private static AabbSnapshot sweptPlayerBox(PlayerSnapshot player, long tick) {
        return sweptPlayerBox(player, 0L, tick);
    }

    private static AabbSnapshot sweptPlayerBox(PlayerSnapshot player, long startTick, long endTick) {
        AabbSnapshot box = player.boundingBox();
        Vec3Snapshot velocity = player.velocity();
        double startX = velocity.x() * startTick;
        double startY = velocity.y() * startTick;
        double startZ = velocity.z() * startTick;
        double endX = velocity.x() * endTick;
        double endY = velocity.y() * endTick;
        double endZ = velocity.z() * endTick;
        return new AabbSnapshot(
            Math.min(box.minX() + startX, box.minX() + endX),
            Math.min(box.minY() + startY, box.minY() + endY),
            Math.min(box.minZ() + startZ, box.minZ() + endZ),
            Math.max(box.maxX() + startX, box.maxX() + endX),
            Math.max(box.maxY() + startY, box.maxY() + endY),
            Math.max(box.maxZ() + startZ, box.maxZ() + endZ)
        );
    }

    private static Vec3Snapshot playerPositionAt(PlayerSnapshot player, long tick) {
        Vec3Snapshot position = player.position();
        Vec3Snapshot velocity = player.velocity();
        return new Vec3Snapshot(
            position.x() + velocity.x() * tick,
            position.y() + velocity.y() * tick,
            position.z() + velocity.z() * tick
        );
    }

    private static float splashRawDamage(float fullDamage, double radius, double distance) {
        if (distance >= radius) return 0f;
        double intensity = Math.max(0d, 1d - distance / radius);
        double scaled = fullDamage * intensity;
        return scaled >= Float.MAX_VALUE
            ? Float.MAX_VALUE
            : (float) Math.floor(scaled + 0.5d);
    }

    private static boolean hasMotion(Vec3Snapshot velocity) {
        return Math.abs(velocity.x()) > EPSILON
            || Math.abs(velocity.y()) > EPSILON
            || Math.abs(velocity.z()) > EPSILON;
    }

    private static double distanceToAabb(Vec3Snapshot point, AabbSnapshot box) {
        double dx = axisDistance(point.x(), box.minX(), box.maxX());
        double dy = axisDistance(point.y(), box.minY(), box.maxY());
        double dz = axisDistance(point.z(), box.minZ(), box.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distanceBetweenAabbs(AabbSnapshot first, AabbSnapshot second) {
        double dx = axisGap(first.minX(), first.maxX(), second.minX(), second.maxX());
        double dy = axisGap(first.minY(), first.maxY(), second.minY(), second.maxY());
        double dz = axisGap(first.minZ(), first.maxZ(), second.minZ(), second.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double axisGap(double firstMin, double firstMax, double secondMin, double secondMax) {
        if (firstMax < secondMin) return secondMin - firstMax;
        if (secondMax < firstMin) return firstMin - secondMax;
        return 0d;
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) return min - value;
        if (value > max) return value - max;
        return 0d;
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

    private static boolean isDragonFireball(WorldSnapshot.EntitySnapshot entity) {
        return DRAGON_FIREBALL_TYPE.equals(entity.typeKey());
    }

    private static boolean isSplashPotion(WorldSnapshot.EntitySnapshot entity) {
        return SPLASH_POTION_TYPE.equals(entity.typeKey())
            || "minecraft:potion".equals(entity.typeKey()) && entity.properties().containsKey("potion_splash_radius");
    }

    private static boolean isLingeringPotion(WorldSnapshot.EntitySnapshot entity) {
        return LINGERING_POTION_TYPE.equals(entity.typeKey())
            || Boolean.parseBoolean(entity.properties().getOrDefault("potion_lingering", "false"));
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0 && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
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

    private record Collision(double t, Vec3Snapshot position, AabbSnapshot blockBounds) {
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
