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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Predicts non-instant Poison/Wither delivered by splash potions that collide with nearby blocks. */
public final class SplashStatusProjectilePredictor implements ThreatPredictor {
    private static final String SPLASH_POTION_TYPE = "minecraft:splash_potion";
    private static final double EPSILON = 1.0E-7d;
    private static final int EFFECT_CUTOFF_TICKS = 20;
    private static final int POISON_BASE_INTERVAL_TICKS = 25;
    private static final int WITHER_BASE_INTERVAL_TICKS = 40;

    private final ProjectilePredictor projectilePredictor = new ProjectilePredictor();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");
        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!isSplashPotion(entity)) continue;
            addStatus(context, entity, "poison", POISON_BASE_INTERVAL_TICKS, "minecraft:magic", 1f, result);
            addStatus(context, entity, "wither", WITHER_BASE_INTERVAL_TICKS, "minecraft:wither", 0f, result);
        }
        return List.copyOf(result);
    }

    private void addStatus(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        String statusKey,
        int baseInterval,
        String sourceKey,
        float healthFloor,
        List<ThreatEvent> output
    ) {
        int sourceDuration = positiveInt(entity.properties().get("potion_" + statusKey + "_duration_ticks"), 0);
        if (sourceDuration <= 0) return;
        int amplifier = positiveInt(entity.properties().get("potion_" + statusKey + "_amplifier"), 0);
        float durationScale = finiteNonNegativeFloat(entity.properties().get("potion_duration_scale"), 1f);
        double scaledBase = sourceDuration * (double) durationScale;
        if (!Double.isFinite(scaledBase) || scaledBase <= 0d) return;

        Optional<BlockImpact> impact = blockImpact(context, entity, scaledBase);
        if (impact.isEmpty()) return;

        int interval = intervalTicks(baseInterval, amplifier);
        if (impact.get().exact()) {
            int duration = exactDuration(context.player(), entity, impact.get(), scaledBase);
            addExactEvents(context, entity, impact.get(), statusKey, interval, duration, sourceKey, healthFloor, output);
        } else {
            int maximumDuration = maximumPossibleDuration(context.player(), entity, impact.get(), scaledBase);
            addBoundedEvents(context, entity, impact.get(), statusKey, interval, maximumDuration, sourceKey, healthFloor, output);
        }
    }

    private Optional<BlockImpact> blockImpact(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        double scaledBaseDuration
    ) {
        Map<String, String> properties = new LinkedHashMap<>(entity.properties());
        properties.put("potion_instant_damage", Double.toString(Math.min(scaledBaseDuration, Float.MAX_VALUE)));
        properties.put("potion_source_key", "minecraft:indirect_magic");

        WorldSnapshot.EntitySnapshot markerEntity = new WorldSnapshot.EntitySnapshot(
            entity.id(), entity.typeKey(), entity.position(), entity.velocity(), entity.boundingBox(), properties
        );
        PredictionContext markerContext = new PredictionContext(
            context.player(),
            new WorldSnapshot(List.of(markerEntity), context.world().blocks()),
            context.timing(),
            context.limits()
        );

        return projectilePredictor.predict(markerContext).stream()
            .filter(event -> event.id().equals("projectile:" + entity.id() + ":splash_magic"))
            .filter(event -> event.impactPosition().isPresent())
            .map(event -> {
                AabbSnapshot block = fullBlockAt(context.world().blocks(), event.impactPosition().orElseThrow());
                if (block == null) return null;
                boolean uncertain = positiveInt(entity.properties().get("observation_age_ticks"), 0) > 0
                    || hasMotion(context.player().velocity());
                return new BlockImpact(event.impact(), event.impactPosition().orElseThrow(), block, !uncertain);
            })
            .filter(java.util.Objects::nonNull)
            .findFirst();
    }

    private static int exactDuration(
        PlayerSnapshot player,
        WorldSnapshot.EntitySnapshot entity,
        BlockImpact impact,
        double scaledBaseDuration
    ) {
        AabbSnapshot potion = moveBox(entity.boundingBox(), entity.position(), impact.position());
        double margin = projectileMarginAtImpact(entity, impact, false);
        AabbSnapshot target = inflate(player.boundingBox(), margin, margin, margin);
        return scaledDuration(scaledBaseDuration, distanceBetweenAabbs(potion, target), splashRadius(entity));
    }

    private static int maximumPossibleDuration(
        PlayerSnapshot player,
        WorldSnapshot.EntitySnapshot entity,
        BlockImpact impact,
        double scaledBaseDuration
    ) {
        double extentX = maxExtent(entity.boundingBox().minX() - entity.position().x(), entity.boundingBox().maxX() - entity.position().x());
        double extentY = maxExtent(entity.boundingBox().minY() - entity.position().y(), entity.boundingBox().maxY() - entity.position().y());
        double extentZ = maxExtent(entity.boundingBox().minZ() - entity.position().z(), entity.boundingBox().maxZ() - entity.position().z());
        AabbSnapshot possiblePotion = inflate(impact.blockBounds(), extentX, extentY, extentZ);
        AabbSnapshot possiblePlayer = sweptPlayerBox(player, impact.window().latest());
        double margin = projectileMarginAtImpact(entity, impact, true);
        possiblePlayer = inflate(possiblePlayer, margin, margin, margin);
        return scaledDuration(scaledBaseDuration, distanceBetweenAabbs(possiblePotion, possiblePlayer), splashRadius(entity));
    }

    private static double projectileMarginAtImpact(
        WorldSnapshot.EntitySnapshot entity,
        BlockImpact impact,
        boolean conservative
    ) {
        String ageProperty = entity.properties().get("projectile_age_ticks");
        if (ageProperty == null) {
            return finiteNonNegativeDouble(entity.properties().get("projectile_margin"), 0d);
        }

        int baseAge = positiveInt(ageProperty, 0);
        long futureAge = saturatingAdd(baseAge, impact.window().latest());
        if (conservative) {
            futureAge = saturatingAdd(
                futureAge,
                positiveInt(entity.properties().get("observation_age_ticks"), 0)
            );
        }
        double margin = (futureAge - 2d) / 20d;
        return Math.max(0d, Math.min(0.3d, margin));
    }

    private static void addExactEvents(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        BlockImpact impact,
        String statusKey,
        int interval,
        int duration,
        String sourceKey,
        float healthFloor,
        List<ThreatEvent> output
    ) {
        if (duration <= EFFECT_CUTOFF_TICKS) return;
        int firstOffset = duration % interval;
        long horizon = context.limits().maxProjectileHorizonTicks();
        int application = 0;
        for (int elapsed = firstOffset; elapsed < duration; elapsed += interval) {
            long earliest = saturatingAdd(impact.window().earliest(), elapsed);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(impact.window().latest(), elapsed));
            if (latest < earliest) break;
            output.add(event(
                entity, impact.position(), statusKey, application++, new TickWindow(earliest, latest),
                DamageRange.exact(1f), sourceKey, healthFloor,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED
            ));
        }
    }

    private static void addBoundedEvents(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        BlockImpact impact,
        String statusKey,
        int interval,
        int maximumDuration,
        String sourceKey,
        float healthFloor,
        List<ThreatEvent> output
    ) {
        if (maximumDuration <= Math.max(EFFECT_CUTOFF_TICKS, interval - 1)) return;
        int maximumApplications = maximumDuration / interval;
        long horizon = context.limits().maxProjectileHorizonTicks();
        for (int application = 0; application < maximumApplications; application++) {
            long earliest = saturatingAdd(impact.window().earliest(), (long) application * interval);
            if (earliest > horizon) break;
            long latest = saturatingAdd(
                impact.window().latest(),
                (long) application * interval + interval - 1L
            );
            latest = Math.min(horizon, latest);
            if (latest < earliest) break;
            output.add(event(
                entity, impact.position(), statusKey, application, new TickWindow(earliest, latest),
                new DamageRange(0f, 1f), sourceKey, healthFloor, Confidence.BOUNDED
            ));
        }
    }

    private static ThreatEvent event(
        WorldSnapshot.EntitySnapshot entity,
        Vec3Snapshot impactPosition,
        String statusKey,
        int application,
        TickWindow impact,
        DamageRange damage,
        String sourceKey,
        float healthFloor,
        Confidence confidence
    ) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            damage,
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.of(impactPosition),
            sourceKey,
            healthFloor
        );
        return new ThreatEvent(
            "projectile:" + entity.id() + ":splash_status:" + statusKey + ":" + application,
            ThreatKind.PROJECTILE,
            impact,
            source,
            confidence,
            Optional.of(entity.position()),
            Optional.of(impactPosition),
            true,
            false,
            true,
            false
        );
    }

    private static int scaledDuration(double scaledBaseDuration, double distance, double radius) {
        if (!(radius > 0d) || distance >= radius) return 0;
        double scale = Math.max(0d, 1d - distance / radius);
        double duration = scale * scaledBaseDuration + 0.5d;
        if (!Double.isFinite(duration)) return Integer.MAX_VALUE;
        return duration >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) duration);
    }

    private static AabbSnapshot fullBlockAt(List<WorldSnapshot.BlockSnapshot> blocks, Vec3Snapshot impact) {
        AabbSnapshot best = null;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (WorldSnapshot.BlockSnapshot block : blocks) {
            if (!block.collision()
                || !Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"))) {
                continue;
            }
            double x = Math.floor(block.position().x());
            double y = Math.floor(block.position().y());
            double z = Math.floor(block.position().z());
            AabbSnapshot cube = new AabbSnapshot(x, y, z, x + 1d, y + 1d, z + 1d);
            double distance = distanceToAabb(impact, cube);
            if (distance <= EPSILON && distance < bestDistance) {
                best = cube;
                bestDistance = distance;
            }
        }
        return best;
    }

    private static AabbSnapshot moveBox(AabbSnapshot box, Vec3Snapshot from, Vec3Snapshot to) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        return new AabbSnapshot(
            box.minX() + dx, box.minY() + dy, box.minZ() + dz,
            box.maxX() + dx, box.maxY() + dy, box.maxZ() + dz
        );
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

    private static AabbSnapshot inflate(AabbSnapshot box, double x, double y, double z) {
        return new AabbSnapshot(
            box.minX() - x, box.minY() - y, box.minZ() - z,
            box.maxX() + x, box.maxY() + y, box.maxZ() + z
        );
    }

    private static double maxExtent(double minOffset, double maxOffset) {
        return Math.max(Math.abs(minOffset), Math.abs(maxOffset));
    }

    private static double distanceBetweenAabbs(AabbSnapshot first, AabbSnapshot second) {
        double dx = axisGap(first.minX(), first.maxX(), second.minX(), second.maxX());
        double dy = axisGap(first.minY(), first.maxY(), second.minY(), second.maxY());
        double dz = axisGap(first.minZ(), first.maxZ(), second.minZ(), second.maxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double distanceToAabb(Vec3Snapshot point, AabbSnapshot box) {
        double dx = axisDistance(point.x(), box.minX(), box.maxX());
        double dy = axisDistance(point.y(), box.minY(), box.maxY());
        double dz = axisDistance(point.z(), box.minZ(), box.maxZ());
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

    private static double splashRadius(WorldSnapshot.EntitySnapshot entity) {
        return finiteNonNegativeDouble(entity.properties().get("potion_splash_radius"), 4d);
    }

    private static int intervalTicks(int baseInterval, int amplifier) {
        int shift = Math.min(30, Math.max(0, amplifier));
        return Math.max(1, baseInterval >> shift);
    }

    private static boolean isSplashPotion(WorldSnapshot.EntitySnapshot entity) {
        return SPLASH_POTION_TYPE.equals(entity.typeKey())
            || "minecraft:potion".equals(entity.typeKey()) && entity.properties().containsKey("potion_splash_radius");
    }

    private static boolean hasMotion(Vec3Snapshot velocity) {
        return Math.abs(velocity.x()) > EPSILON
            || Math.abs(velocity.y()) > EPSILON
            || Math.abs(velocity.z()) > EPSILON;
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

    private static float finiteNonNegativeFloat(String value, float fallback) {
        if (value == null) return fallback;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed >= 0f ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static double finiteNonNegativeDouble(String value, double fallback) {
        if (value == null) return fallback;
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed >= 0d ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }

    private record BlockImpact(TickWindow window, Vec3Snapshot position, AabbSnapshot blockBounds, boolean exact) {
    }
}
