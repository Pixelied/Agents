package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
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

/**
 * Predicts delayed Poison/Wither damage caused by lingering-potion clouds.
 *
 * <p>The throwable trajectory and collision point deliberately come from the production
 * {@link ProjectilePredictor}. A synthetic one-damage lingering payload is used only to ask that
 * predictor for the cloud-application window; the synthetic damage event itself is never emitted.
 * This keeps one authoritative projectile-motion/collision implementation.</p>
 */
public final class LingeringStatusProjectilePredictor implements ThreatPredictor {
    private static final String LINGERING_POTION_TYPE = "minecraft:lingering_potion";
    private static final float DEFAULT_LINGERING_DURATION_SCALE = 0.25f;
    private static final int POISON_BASE_INTERVAL_TICKS = 25;
    private static final int WITHER_BASE_INTERVAL_TICKS = 40;
    private static final float STATUS_RAW_DAMAGE = 1f;
    private static final float POISON_HEALTH_FLOOR = 1f;
    private static final float WITHER_HEALTH_FLOOR = 0f;

    private final ProjectilePredictor projectilePredictor = new ProjectilePredictor();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");

        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            if (!isLingeringPotion(entity)) continue;
            Optional<CloudApplication> application = cloudApplication(context, entity);
            if (application.isEmpty()) continue;

            int poisonDuration = positiveInt(entity.properties().get("potion_poison_duration_ticks"), 0);
            if (poisonDuration > 0) {
                int amplifier = positiveInt(entity.properties().get("potion_poison_amplifier"), 0);
                addStatusEvents(
                    context,
                    entity,
                    application.get(),
                    "poison",
                    poisonDuration,
                    amplifier,
                    POISON_BASE_INTERVAL_TICKS,
                    "minecraft:magic",
                    POISON_HEALTH_FLOOR,
                    result
                );
            }

            int witherDuration = positiveInt(entity.properties().get("potion_wither_duration_ticks"), 0);
            if (witherDuration > 0) {
                int amplifier = positiveInt(entity.properties().get("potion_wither_amplifier"), 0);
                addStatusEvents(
                    context,
                    entity,
                    application.get(),
                    "wither",
                    witherDuration,
                    amplifier,
                    WITHER_BASE_INTERVAL_TICKS,
                    "minecraft:wither",
                    WITHER_HEALTH_FLOOR,
                    result
                );
            }
        }
        return List.copyOf(result);
    }

    private Optional<CloudApplication> cloudApplication(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity
    ) {
        Map<String, String> properties = new LinkedHashMap<>(entity.properties());
        properties.put("potion_lingering", "true");
        properties.put("potion_instant_damage", "1");
        properties.put("potion_source_key", "minecraft:indirect_magic");

        WorldSnapshot.EntitySnapshot markerEntity = new WorldSnapshot.EntitySnapshot(
            entity.id(),
            entity.typeKey(),
            entity.position(),
            entity.velocity(),
            entity.boundingBox(),
            properties
        );
        PredictionContext markerContext = new PredictionContext(
            context.player(),
            new WorldSnapshot(List.of(markerEntity), context.world().blocks()),
            context.timing(),
            context.limits()
        );

        return projectilePredictor.predict(markerContext).stream()
            .filter(event -> event.id().equals("projectile:" + entity.id() + ":lingering_cloud:0"))
            .filter(event -> event.impactPosition().isPresent())
            .findFirst()
            .map(event -> new CloudApplication(event.impact(), event.impactPosition().orElseThrow()));
    }

    private static void addStatusEvents(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        CloudApplication cloud,
        String statusKey,
        int sourceDuration,
        int amplifier,
        int baseInterval,
        String sourceKey,
        float healthFloor,
        List<ThreatEvent> output
    ) {
        float durationScale = finiteNonNegativeFloat(
            entity.properties().get("potion_duration_scale"),
            DEFAULT_LINGERING_DURATION_SCALE
        );
        int cloudDuration = scaledDuration(sourceDuration, durationScale);
        if (cloudDuration <= 0) return;

        int interval = intervalTicks(baseInterval, amplifier);
        int firstOffset = cloudDuration % interval;
        long horizon = context.limits().maxProjectileHorizonTicks();
        int application = 0;

        for (int elapsed = firstOffset; elapsed < cloudDuration; elapsed += interval) {
            long earliest = saturatingAdd(cloud.window().earliest(), elapsed);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(cloud.window().latest(), elapsed));
            if (latest < earliest) break;

            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(STATUS_RAW_DAMAGE),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                Optional.of(cloud.origin()),
                sourceKey,
                healthFloor
            );
            output.add(new ThreatEvent(
                "projectile:" + entity.id() + ":lingering_status:" + statusKey + ":" + application,
                ThreatKind.ENVIRONMENT,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                Optional.of(cloud.origin()),
                true,
                false,
                true,
                false
            ));
            application++;
        }
    }

    private static int scaledDuration(int sourceDuration, float scale) {
        if (sourceDuration <= 0 || !Float.isFinite(scale) || scale <= 0f) return 0;
        double scaled = Math.floor(sourceDuration * (double) scale);
        if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) scaled);
    }

    private static int intervalTicks(int baseInterval, int amplifier) {
        int shift = Math.min(30, Math.max(0, amplifier));
        return Math.max(1, baseInterval >> shift);
    }

    private static boolean isLingeringPotion(WorldSnapshot.EntitySnapshot entity) {
        return LINGERING_POTION_TYPE.equals(entity.typeKey())
            || Boolean.parseBoolean(entity.properties().getOrDefault("potion_lingering", "false"));
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

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        return value + increment;
    }

    private record CloudApplication(TickWindow window, Vec3Snapshot origin) {
    }
}
