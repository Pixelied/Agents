package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Adds Poison/Wither ticks omitted by the legacy single-strongest-effect forecasts.
 * This includes vanilla hidden-effect tails and custom POTION_DURATION_SCALE phase changes for
 * direct splash and lingering-cloud delivery while reusing the production projectile collision model.
 */
public final class StackedPotionStatusPredictor implements ThreatPredictor {
    private static final String SPLASH_POTION_TYPE = "minecraft:splash_potion";
    private static final String LINGERING_POTION_TYPE = "minecraft:lingering_potion";
    private static final float DEFAULT_LINGERING_DURATION_SCALE = 0.25f;
    private static final int EFFECT_CUTOFF_TICKS = 20;
    private static final int POISON_BASE_INTERVAL_TICKS = 25;
    private static final int WITHER_BASE_INTERVAL_TICKS = 40;
    private final ProjectilePredictor projectilePredictor = new ProjectilePredictor();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        if (context == null) throw new NullPointerException("context");

        List<ThreatEvent> result = new ArrayList<>();
        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            DeliveryKind deliveryKind = deliveryKind(entity);
            if (deliveryKind == null) continue;
            List<StatusSpec> ordered = statusSpecs(entity);
            if (ordered.isEmpty()) continue;

            Optional<ThreatEvent> application = application(context, entity, deliveryKind);
            if (application.isEmpty()) continue;
            ThreatEvent impact = application.get();

            addKind(context, entity, impact, deliveryKind, ordered, "poison", POISON_BASE_INTERVAL_TICKS,
                "minecraft:magic", 1f, result);
            addKind(context, entity, impact, deliveryKind, ordered, "wither", WITHER_BASE_INTERVAL_TICKS,
                "minecraft:wither", 0f, result);
        }
        return List.copyOf(result);
    }

    private Optional<ThreatEvent> application(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        DeliveryKind deliveryKind
    ) {
        Map<String, String> markerProperties = new LinkedHashMap<>(entity.properties());
        markerProperties.put("potion_instant_damage", "1.0");
        markerProperties.put("potion_source_key", "minecraft:indirect_magic");
        if (deliveryKind == DeliveryKind.LINGERING) markerProperties.put("potion_lingering", "true");

        WorldSnapshot.EntitySnapshot marker = new WorldSnapshot.EntitySnapshot(
            entity.id(), entity.typeKey(), entity.position(), entity.velocity(), entity.boundingBox(), markerProperties
        );
        PredictionContext markerContext = new PredictionContext(
            context.player(),
            new WorldSnapshot(List.of(marker), context.world().blocks()),
            context.timing(),
            context.limits()
        );
        String eventId = deliveryKind == DeliveryKind.LINGERING
            ? "projectile:" + entity.id() + ":lingering_cloud:0"
            : "projectile:" + entity.id() + ":splash_magic";
        return projectilePredictor.predict(markerContext).stream()
            .filter(event -> event.id().equals(eventId))
            .filter(event -> event.impactPosition().isPresent())
            .findFirst();
    }

    private static void addKind(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        ThreatEvent application,
        DeliveryKind deliveryKind,
        List<StatusSpec> ordered,
        String kind,
        int baseInterval,
        String sourceKey,
        float healthFloor,
        List<ThreatEvent> output
    ) {
        float durationScale = finiteNonNegativeFloat(
            entity.properties().get("potion_duration_scale"),
            deliveryKind == DeliveryKind.LINGERING ? DEFAULT_LINGERING_DURATION_SCALE : 1f
        );
        List<StatusSpec> delivered = new ArrayList<>();
        for (StatusSpec spec : ordered) {
            if (!kind.equals(spec.kind())) continue;
            int duration = deliveryKind == DeliveryKind.LINGERING
                ? scaledLingeringDuration(spec.duration(), durationScale)
                : scaledSplashDuration(spec.duration(), durationScale);
            if (duration == Integer.MIN_VALUE) continue;
            if (duration != -1 && deliveryKind == DeliveryKind.SPLASH && duration <= EFFECT_CUTOFF_TICKS) continue;
            if (duration == 0) continue;
            delivered.add(new StatusSpec(kind, duration, spec.amplifier()));
        }
        if (delivered.isEmpty()) return;

        if (deliveryKind == DeliveryKind.SPLASH && application.confidence() != Confidence.EXACT) {
            addBoundedSplashTail(
                context,
                entity,
                application,
                ordered,
                kind,
                baseInterval,
                sourceKey,
                healthFloor,
                durationScale,
                output
            );
            return;
        }

        long horizon = context.limits().maxDecisionHistory();
        List<Integer> actualOffsets = simulateDamageOffsets(delivered, baseInterval, horizon);
        Set<Integer> legacyOffsets = legacyOffsets(
            entity,
            deliveryKind,
            kind,
            baseInterval,
            durationScale,
            horizon
        );
        int eventIndex = 0;
        for (int elapsed : actualOffsets) {
            if (legacyOffsets.contains(elapsed)) continue;
            long earliest = saturatingAdd(application.impact().earliest(), elapsed);
            if (earliest > horizon) break;
            long latest = Math.min(horizon, saturatingAdd(application.impact().latest(), elapsed));
            if (latest < earliest) continue;
            DamageSourceSnapshot source = new DamageSourceSnapshot(
                DamageRange.exact(1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                application.impactPosition(),
                sourceKey,
                healthFloor
            );
            String marker = deliveryKind == DeliveryKind.LINGERING
                ? "lingering_stacked_status"
                : "stacked_status";
            output.add(new ThreatEvent(
                "projectile:" + entity.id() + ":" + marker + ":" + kind + ":" + eventIndex++,
                deliveryKind == DeliveryKind.LINGERING ? ThreatKind.ENVIRONMENT : ThreatKind.PROJECTILE,
                new TickWindow(earliest, latest),
                source,
                earliest == latest ? Confidence.EXACT : Confidence.BOUNDED,
                Optional.of(entity.position()),
                application.impactPosition(),
                true,
                false,
                true,
                false
            ));
        }
    }

    private static void addBoundedSplashTail(
        PredictionContext context,
        WorldSnapshot.EntitySnapshot entity,
        ThreatEvent application,
        List<StatusSpec> ordered,
        String kind,
        int baseInterval,
        String sourceKey,
        float healthFloor,
        float durationScale,
        List<ThreatEvent> output
    ) {
        int legacySourceDuration = nonNegativeInt(
            entity.properties().get("potion_" + kind + "_duration_ticks"),
            0
        );
        int legacyMaximumDuration = scaledSplashDuration(legacySourceDuration, durationScale);
        if (legacyMaximumDuration < 0) return;

        int maximumStackDuration = 0;
        int fastestTailInterval = Integer.MAX_VALUE;
        for (StatusSpec spec : ordered) {
            if (!kind.equals(spec.kind())) continue;
            int duration = scaledSplashDuration(spec.duration(), durationScale);
            if (duration == -1) return;
            if (duration <= EFFECT_CUTOFF_TICKS) continue;
            maximumStackDuration = Math.max(maximumStackDuration, duration);
            if (duration > legacyMaximumDuration) {
                fastestTailInterval = Math.min(fastestTailInterval, intervalTicks(baseInterval, spec.amplifier()));
            }
        }
        if (maximumStackDuration <= legacyMaximumDuration || fastestTailInterval == Integer.MAX_VALUE) return;

        long tailStart = legacyMaximumDuration > EFFECT_CUTOFF_TICKS ? legacyMaximumDuration : 0L;
        long horizon = context.limits().maxDecisionHistory();
        int eventIndex = 0;
        for (long bucketStart = tailStart; bucketStart < maximumStackDuration; bucketStart = saturatingAdd(bucketStart, fastestTailInterval)) {
            long earliest = saturatingAdd(application.impact().earliest(), bucketStart);
            if (earliest > horizon) break;
            long latestOffset = saturatingAdd(bucketStart, fastestTailInterval - 1L);
            long latest = Math.min(horizon, saturatingAdd(application.impact().latest(), latestOffset));
            if (latest < earliest) continue;

            DamageSourceSnapshot source = new DamageSourceSnapshot(
                new DamageRange(0f, 1f),
                EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
                false,
                1f,
                false,
                application.impactPosition(),
                sourceKey,
                healthFloor
            );
            output.add(new ThreatEvent(
                "projectile:" + entity.id() + ":stacked_status:" + kind + ":" + eventIndex++,
                ThreatKind.PROJECTILE,
                new TickWindow(earliest, latest),
                source,
                Confidence.BOUNDED,
                Optional.of(entity.position()),
                application.impactPosition(),
                true,
                false,
                true,
                false
            ));
        }
    }

    private static List<Integer> simulateDamageOffsets(List<StatusSpec> effects, int baseInterval, long horizon) {
        EffectState active = null;
        for (StatusSpec effect : effects) active = apply(active, effect.duration(), effect.amplifier());
        if (active == null) return List.of();

        int maxTicks = horizon >= Integer.MAX_VALUE ? Integer.MAX_VALUE : Math.max(0, (int) horizon);
        List<Integer> offsets = new ArrayList<>();
        for (int elapsed = 0; elapsed <= maxTicks && active != null; elapsed++) {
            if (active.duration == -1) {
                // Infinite effects use target.tickCount for phase, which is not synchronized as an authoritative
                // server phase. Infinite pre-application payloads are handled by a separate conservative path.
                break;
            }
            if (active.duration <= 0) break;
            int interval = intervalTicks(baseInterval, active.amplifier);
            if (active.duration % interval == 0) offsets.add(elapsed);
            tickDown(active);
            if (active.duration == 0) active = active.hidden;
        }
        return List.copyOf(offsets);
    }

    private static EffectState apply(EffectState active, int duration, int amplifier) {
        EffectState takeOver = new EffectState(duration, amplifier, null);
        if (active == null) return takeOver;
        update(active, takeOver);
        return active;
    }

    private static void update(EffectState active, EffectState takeOver) {
        if (takeOver.amplifier > active.amplifier) {
            if (isShorterDuration(takeOver.duration, active.duration)) {
                active.hidden = new EffectState(active.duration, active.amplifier, active.hidden);
            }
            active.amplifier = takeOver.amplifier;
            active.duration = takeOver.duration;
        } else if (isShorterDuration(active.duration, takeOver.duration)) {
            if (takeOver.amplifier == active.amplifier) {
                active.duration = takeOver.duration;
            } else if (active.hidden == null) {
                active.hidden = takeOver;
            } else {
                update(active.hidden, takeOver);
            }
        }
    }

    private static boolean isShorterDuration(int first, int second) {
        if (first == -1) return false;
        return second == -1 || first < second;
    }

    private static void tickDown(EffectState state) {
        if (state.hidden != null) tickDown(state.hidden);
        if (state.duration > 0) state.duration--;
    }

    private static Set<Integer> legacyOffsets(
        WorldSnapshot.EntitySnapshot entity,
        DeliveryKind deliveryKind,
        String kind,
        int baseInterval,
        float durationScale,
        long horizon
    ) {
        int sourceDuration = nonNegativeInt(entity.properties().get("potion_" + kind + "_duration_ticks"), 0);
        if (sourceDuration <= 0) return Set.of();
        int duration = deliveryKind == DeliveryKind.LINGERING
            ? scaledLingeringDuration(sourceDuration, durationScale)
            : sourceDuration;
        if (duration <= 0) return Set.of();
        int amplifier = nonNegativeInt(entity.properties().get("potion_" + kind + "_amplifier"), 0);
        int interval = intervalTicks(baseInterval, amplifier);
        int first = duration % interval;
        Set<Integer> offsets = new HashSet<>();
        for (int elapsed = first; elapsed < duration && elapsed <= horizon; elapsed += interval) offsets.add(elapsed);
        return Set.copyOf(offsets);
    }

    private static List<StatusSpec> statusSpecs(WorldSnapshot.EntitySnapshot entity) {
        int count = nonNegativeInt(entity.properties().get("potion_status_count"), 0);
        if (count <= 0) return List.of();
        List<StatusSpec> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String prefix = "potion_status_" + index + "_";
            String kind = entity.properties().get(prefix + "kind");
            if (!"poison".equals(kind) && !"wither".equals(kind)) continue;
            int duration = signedDuration(entity.properties().get(prefix + "duration_ticks"));
            if (duration == Integer.MIN_VALUE || duration == 0) continue;
            int amplifier = nonNegativeInt(entity.properties().get(prefix + "amplifier"), 0);
            result.add(new StatusSpec(kind, duration, amplifier));
        }
        return List.copyOf(result);
    }

    private static int scaledSplashDuration(int duration, float scale) {
        if (duration == -1) return -1;
        double scaled = duration * (double) scale + 0.5d;
        if (!Double.isFinite(scaled)) return Integer.MAX_VALUE;
        if (scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(0, (int) scaled);
    }

    private static int scaledLingeringDuration(int duration, float scale) {
        if (duration == -1) return -1;
        if (!Float.isFinite(scale) || scale <= 0f || duration <= 0) return 0;
        double scaled = Math.floor(duration * (double) scale);
        if (!Double.isFinite(scaled) || scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) scaled);
    }

    private static int intervalTicks(int baseInterval, int amplifier) {
        int shift = Math.min(30, Math.max(0, amplifier));
        return Math.max(1, baseInterval >> shift);
    }

    private static DeliveryKind deliveryKind(WorldSnapshot.EntitySnapshot entity) {
        if (LINGERING_POTION_TYPE.equals(entity.typeKey())
            || Boolean.parseBoolean(entity.properties().getOrDefault("potion_lingering", "false"))) {
            return DeliveryKind.LINGERING;
        }
        if (SPLASH_POTION_TYPE.equals(entity.typeKey())) return DeliveryKind.SPLASH;
        return null;
    }

    private static int signedDuration(String value) {
        if (value == null) return Integer.MIN_VALUE;
        try {
            int parsed = Integer.parseInt(value);
            return parsed == -1 || parsed > 0 ? parsed : Integer.MIN_VALUE;
        } catch (NumberFormatException ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static int nonNegativeInt(String value, int fallback) {
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

    private enum DeliveryKind {
        SPLASH,
        LINGERING
    }

    private record StatusSpec(String kind, int duration, int amplifier) {
    }

    private static final class EffectState {
        private int duration;
        private int amplifier;
        private EffectState hidden;

        private EffectState(int duration, int amplifier, EffectState hidden) {
            this.duration = duration;
            this.amplifier = amplifier;
            this.hidden = hidden;
        }
    }
}